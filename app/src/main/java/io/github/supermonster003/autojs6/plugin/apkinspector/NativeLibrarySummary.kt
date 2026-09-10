package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal enum class NativeAbiCompatibility {
    PREFERRED,
    COMPATIBLE,
    UNSUPPORTED,
}

internal data class NativeLibraryAbiSummary(
    val abi: String,
    val libraryCount: Int,
    val uncompressedBytes: Long,
    val compatibility: NativeAbiCompatibility,
)

internal data class NativeLibrarySummary(
    val abiGroups: List<NativeLibraryAbiSummary> = emptyList(),
    val totalLibraryCount: Int = 0,
    val totalUncompressedBytes: Long = 0L,
    val omittedLibraryCount: Int = 0,
    val omittedAbiCount: Int = 0,
    val invalidEntryCount: Int = 0,
    val failedApkCount: Int = 0,
    val omittedApkCount: Int = 0,
    val nestedScanLimitReached: Boolean = false,
) {
    val hasPartialResults: Boolean
        get() = omittedLibraryCount > 0 ||
            omittedAbiCount > 0 ||
            invalidEntryCount > 0 ||
            failedApkCount > 0 ||
            omittedApkCount > 0 ||
            nestedScanLimitReached
}

/**
 * Collects native-library and DEX metadata without inflating their contents.
 *
 * Direct APK and AAB inputs use their already-validated ZIP directory entries. APKs nested in a
 * package container are streamed once and retain only a bounded tail containing the ZIP central
 * directory. The two report sections therefore share one pass and can degrade without invalidating
 * the manifest, signature, or compatibility results.
 */
internal object NativeLibraryInspector {

    const val MAX_NATIVE_LIBRARY_ENTRIES = 32_768
    const val MAX_DISPLAYED_NATIVE_ABIS = 64
    const val MAX_SELECTED_APK_SCANS = PackageInspectionLimits.GENERIC_APKS
    const val MAX_NESTED_APK_SCAN_BYTES = PackageInspectionLimits.TOTAL_SCAN_BYTES
    const val MAX_NESTED_APK_CENTRAL_DIRECTORY_BYTES = 32 * 1024 * 1024

    internal data class Limits(
        val maxNativeLibraryEntries: Int = MAX_NATIVE_LIBRARY_ENTRIES,
        val maxDisplayedAbis: Int = MAX_DISPLAYED_NATIVE_ABIS,
        val maxDisplayedDexFiles: Int = DexFileSummary.MAX_DISPLAYED_FILES,
        val maxSelectedApkScans: Int = MAX_SELECTED_APK_SCANS,
        val maxNestedApkScanBytes: Long = MAX_NESTED_APK_SCAN_BYTES,
        val maxNestedApkCentralDirectoryBytes: Int =
            MAX_NESTED_APK_CENTRAL_DIRECTORY_BYTES,
        val maxNestedApkEntries: Int = MAX_NESTED_APK_ENTRIES,
        val maxEntryNameBytes: Int = MAX_ENTRY_NAME_BYTES,
    ) {
        init {
            require(maxNativeLibraryEntries > 0)
            require(maxDisplayedAbis > 0)
            require(maxDisplayedDexFiles > 0)
            require(maxSelectedApkScans > 0)
            require(maxNestedApkScanBytes > 0L)
            require(maxNestedApkCentralDirectoryBytes > 0)
            require(
                maxNestedApkCentralDirectoryBytes <=
                    Int.MAX_VALUE - ZIP_EOCD_MAX_BYTES - ZIP64_TRAILER_BYTES,
            )
            require(maxNestedApkEntries > 0)
            require(maxEntryNameBytes > 0)
        }
    }

    fun inspectApkEntries(
        entries: Iterable<ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): NativeLibrarySummary = inspectApkCodeEntries(entries, deviceAbis, limits).nativeLibraries

    fun inspectApkCodeEntries(
        entries: Iterable<ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): PackageCodeSummary = inspectCodeEntries(
        entries = entries,
        deviceAbis = deviceAbis,
        nativeLayout = NativeLibraryLayout.APK,
        dexLayout = DexFileLayout.APK,
        limits = limits,
    )

    fun inspectAabEntries(
        entries: Iterable<ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): NativeLibrarySummary = inspectAabCodeEntries(entries, deviceAbis, limits).nativeLibraries

    fun inspectAabCodeEntries(
        entries: Iterable<ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): PackageCodeSummary = inspectCodeEntries(
        entries = entries,
        deviceAbis = deviceAbis,
        nativeLayout = NativeLibraryLayout.AAB,
        dexLayout = DexFileLayout.AAB,
        limits = limits,
    )

    private fun inspectCodeEntries(
        entries: Iterable<ZipEntry>,
        deviceAbis: List<String>,
        nativeLayout: NativeLibraryLayout,
        dexLayout: DexFileLayout,
        limits: Limits,
    ): PackageCodeSummary {
        val nativeAccumulator = Accumulator(deviceAbis, limits)
        val dexAccumulator = DexFileAccumulator(limits.maxDisplayedDexFiles)
        entries.forEach { entry ->
            nativeAccumulator.inspectEntry(
                path = entry.name,
                size = entry.size,
                isDirectory = entry.isDirectory,
                layout = nativeLayout,
            )
            dexAccumulator.inspectEntry(
                path = entry.name,
                size = entry.size,
                isDirectory = entry.isDirectory,
                layout = dexLayout,
            )
        }
        return PackageCodeSummary(
            nativeLibraries = nativeAccumulator.build(),
            dexFiles = dexAccumulator.build(),
        )
    }

    fun inspectNestedApks(
        zip: ZipFile,
        selectedApks: List<ArchiveApkEntry>,
        entriesByPath: Map<String, ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): NativeLibrarySummary = inspectNestedPackageCode(
        zip = zip,
        selectedApks = selectedApks,
        entriesByPath = entriesByPath,
        deviceAbis = deviceAbis,
        limits = limits,
    ).nativeLibraries

    fun inspectNestedPackageCode(
        zip: ZipFile,
        selectedApks: List<ArchiveApkEntry>,
        entriesByPath: Map<String, ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): PackageCodeSummary {
        val nativeAccumulator = Accumulator(deviceAbis, limits)
        val dexAccumulator = DexFileAccumulator(limits.maxDisplayedDexFiles)
        val selectedPaths = selectedApks.asSequence()
            .map(ArchiveApkEntry::archivePath)
            .distinct()
            .toList()
        val pathsToScan = selectedPaths.take(limits.maxSelectedApkScans)
        val initiallyOmitted = selectedPaths.size - pathsToScan.size
        nativeAccumulator.recordOmittedApks(initiallyOmitted)
        dexAccumulator.recordOmittedApks(initiallyOmitted)
        val budget = ScanBudget(limits.maxNestedApkScanBytes)
        val centralDirectoryTail = createCentralDirectoryTail(limits)

        pathsToScan.forEach { path ->
            val entry = entriesByPath[path]
            if (entry == null || entry.isDirectory) {
                nativeAccumulator.recordFailedApk()
                dexAccumulator.recordFailedApk()
                return@forEach
            }
            if (entry.size > budget.remaining) {
                nativeAccumulator.recordOmittedApks(1)
                nativeAccumulator.markNestedScanLimit()
                dexAccumulator.recordOmittedApks(1)
                dexAccumulator.markNestedScanLimit()
                return@forEach
            }
            try {
                val outcome = zip.getInputStream(entry).use { input ->
                    scanNestedApkCentralDirectory(
                        input = input,
                        budget = budget,
                        nativeAccumulator = nativeAccumulator,
                        dexAccumulator = dexAccumulator,
                        sourcePath = path,
                        limits = limits,
                        tail = centralDirectoryTail,
                    )
                }
                if (outcome.entryLimitReached) {
                    nativeAccumulator.markNestedScanLimit()
                    dexAccumulator.markNestedScanLimit()
                }
            } catch (_: PackageCodeScanLimitException) {
                nativeAccumulator.recordOmittedApks(1)
                nativeAccumulator.markNestedScanLimit()
                dexAccumulator.recordOmittedApks(1)
                dexAccumulator.markNestedScanLimit()
            } catch (_: IOException) {
                nativeAccumulator.recordFailedApk()
                dexAccumulator.recordFailedApk()
            }
        }
        return PackageCodeSummary(
            nativeLibraries = nativeAccumulator.build(),
            dexFiles = dexAccumulator.build(),
        )
    }

    internal fun inspectNestedApk(
        input: InputStream,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): NativeLibrarySummary = inspectNestedPackageCode(input, deviceAbis, limits).nativeLibraries

    internal fun inspectNestedPackageCode(
        input: InputStream,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): PackageCodeSummary {
        val nativeAccumulator = Accumulator(deviceAbis, limits)
        val dexAccumulator = DexFileAccumulator(limits.maxDisplayedDexFiles)
        val budget = ScanBudget(limits.maxNestedApkScanBytes)
        val centralDirectoryTail = createCentralDirectoryTail(limits)
        try {
            val outcome = scanNestedApkCentralDirectory(
                input = input,
                budget = budget,
                nativeAccumulator = nativeAccumulator,
                dexAccumulator = dexAccumulator,
                sourcePath = null,
                limits = limits,
                tail = centralDirectoryTail,
            )
            if (outcome.entryLimitReached) {
                nativeAccumulator.markNestedScanLimit()
                dexAccumulator.markNestedScanLimit()
            }
        } catch (_: PackageCodeScanLimitException) {
            nativeAccumulator.recordOmittedApks(1)
            nativeAccumulator.markNestedScanLimit()
            dexAccumulator.recordOmittedApks(1)
            dexAccumulator.markNestedScanLimit()
        } catch (_: IOException) {
            nativeAccumulator.recordFailedApk()
            dexAccumulator.recordFailedApk()
        }
        return PackageCodeSummary(
            nativeLibraries = nativeAccumulator.build(),
            dexFiles = dexAccumulator.build(),
        )
    }

    private fun scanNestedApkCentralDirectory(
        input: InputStream,
        budget: ScanBudget,
        nativeAccumulator: Accumulator,
        dexAccumulator: DexFileAccumulator,
        sourcePath: String?,
        limits: Limits,
        tail: TailBuffer,
    ): NestedApkScanOutcome {
        tail.reset()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = budget.read(input, buffer)
            if (read < 0) break
            if (read == 0) continue
            tail.append(buffer, 0, read)
            totalBytes = Math.addExact(totalBytes, read.toLong())
        }

        val bytes = tail.toByteArray()
        val tailStart = totalBytes - bytes.size
        val eocdOffset = findEocd(bytes)
        val diskNumber = readUInt16(bytes, eocdOffset + ZIP_EOCD_DISK_NUMBER_OFFSET)
        val centralDirectoryDisk =
            readUInt16(bytes, eocdOffset + ZIP_EOCD_DIRECTORY_DISK_OFFSET)
        val entriesOnDisk =
            readUInt16(bytes, eocdOffset + ZIP_EOCD_ENTRIES_ON_DISK_OFFSET)
        var totalEntries =
            readUInt16(bytes, eocdOffset + ZIP_EOCD_TOTAL_ENTRIES_OFFSET).toLong()
        if (diskNumber != 0 || centralDirectoryDisk != 0 || entriesOnDisk.toLong() != totalEntries) {
            throw IOException("Split ZIP archives are not supported for nested APK inspection")
        }
        var centralDirectorySize =
            readUInt32(bytes, eocdOffset + ZIP_EOCD_DIRECTORY_SIZE_OFFSET)
        var centralDirectoryOffset =
            readUInt32(bytes, eocdOffset + ZIP_EOCD_DIRECTORY_OFFSET_OFFSET)
        var directoryBoundary = Math.addExact(tailStart, eocdOffset.toLong())
        if (
            totalEntries == ZIP_UINT16_MAX.toLong() ||
            centralDirectorySize == ZIP_UINT32_MAX ||
            centralDirectoryOffset == ZIP_UINT32_MAX
        ) {
            val locator = eocdOffset - 20
            if (readUInt32(bytes, locator) != 0x07064B50L ||
                readUInt32(bytes, locator + 4) != 0L || readUInt32(bytes, locator + 16) != 1L
            ) {
                throw IOException("Nested APK ZIP64 locator is malformed")
            }
            val recordOffset = readUInt64(bytes, locator + 8)
            if (recordOffset < tailStart) throw PackageCodeScanLimitException()
            if (recordOffset > tailStart + locator - 56L) {
                throw IOException("Nested APK ZIP64 record is outside the retained input")
            }
            val record = (recordOffset - tailStart).toInt()
            val recordSize = readUInt64(bytes, record + 4)
            if (readUInt32(bytes, record) != 0x06064B50L || recordSize < 44L ||
                recordSize != (locator - record - 12).toLong() ||
                readUInt32(bytes, record + 16) != 0L || readUInt32(bytes, record + 20) != 0L
            ) {
                throw IOException("Nested APK ZIP64 record is malformed")
            }
            totalEntries = readUInt64(bytes, record + 32)
            if (readUInt64(bytes, record + 24) != totalEntries) {
                throw IOException("Split ZIP64 archives are not supported")
            }
            centralDirectorySize = readUInt64(bytes, record + 40)
            centralDirectoryOffset = readUInt64(bytes, record + 48)
            directoryBoundary = recordOffset
        }
        if (centralDirectorySize > limits.maxNestedApkCentralDirectoryBytes) {
            throw PackageCodeScanLimitException()
        }

        if (centralDirectoryOffset > directoryBoundary - centralDirectorySize) {
            throw IOException("Nested APK central directory exceeds the EOCD boundary")
        }
        if (centralDirectoryOffset < tailStart) {
            throw PackageCodeScanLimitException()
        }
        val centralOffsetInTail = (centralDirectoryOffset - tailStart).toInt()
        val centralEndInTail = Math.addExact(
            centralOffsetInTail,
            centralDirectorySize.toInt(),
        )
        if (
            centralOffsetInTail < 0 ||
            centralEndInTail > eocdOffset ||
            centralEndInTail > bytes.size
        ) {
            throw IOException("Nested APK central directory is outside the retained input")
        }

        val entriesToRead = minOf(totalEntries, limits.maxNestedApkEntries.toLong()).toInt()
        var offset = centralOffsetInTail
        repeat(entriesToRead) {
            ensureRange(bytes, offset, ZIP_CENTRAL_HEADER_BYTES)
            if (readUInt32(bytes, offset) != ZIP_CENTRAL_HEADER_SIGNATURE) {
                throw IOException("Nested APK central directory entry is malformed")
            }
            val uncompressedSize =
                readUInt32(bytes, offset + ZIP_CENTRAL_UNCOMPRESSED_SIZE_OFFSET)
            val nameLength =
                readUInt16(bytes, offset + ZIP_CENTRAL_NAME_LENGTH_OFFSET)
            val extraLength =
                readUInt16(bytes, offset + ZIP_CENTRAL_EXTRA_LENGTH_OFFSET)
            val commentLength =
                readUInt16(bytes, offset + ZIP_CENTRAL_COMMENT_LENGTH_OFFSET)
            val variableLength = Math.addExact(
                Math.addExact(nameLength, extraLength),
                commentLength,
            )
            val nextOffset = Math.addExact(
                offset,
                Math.addExact(ZIP_CENTRAL_HEADER_BYTES, variableLength),
            )
            if (nextOffset > centralEndInTail) {
                throw IOException("Nested APK central directory entry exceeds its boundary")
            }

            val nameOffset = offset + ZIP_CENTRAL_HEADER_BYTES
            if (nameLength <= limits.maxEntryNameBytes) {
                val path = String(
                    bytes,
                    nameOffset,
                    nameLength,
                    StandardCharsets.ISO_8859_1,
                )
                val declaredSize = if (uncompressedSize == ZIP_UINT32_MAX) {
                    readZip64EntrySize(bytes, nameOffset + nameLength, extraLength)
                } else uncompressedSize
                val isDirectory = path.endsWith('/')
                nativeAccumulator.inspectEntry(
                    path = path,
                    size = declaredSize,
                    isDirectory = isDirectory,
                    layout = NativeLibraryLayout.APK,
                )
                dexAccumulator.inspectEntry(
                    path = path,
                    size = declaredSize,
                    isDirectory = isDirectory,
                    layout = DexFileLayout.APK,
                    sourcePath = sourcePath,
                )
            } else {
                if (
                    looksLikeOversizedApkNativeLibrary(
                        bytes = bytes,
                        offset = nameOffset,
                        length = nameLength,
                    )
                ) {
                    nativeAccumulator.recordInvalidEntry()
                }
                if (
                    looksLikeOversizedApkDexFile(
                        bytes = bytes,
                        offset = nameOffset,
                        length = nameLength,
                    )
                ) {
                    dexAccumulator.recordInvalidEntry()
                }
            }
            offset = nextOffset
        }
        if (totalEntries == entriesToRead.toLong() && offset != centralEndInTail) {
            throw IOException("Nested APK central directory entry count does not match its size")
        }

        return NestedApkScanOutcome(
            entryLimitReached = totalEntries > entriesToRead,
        )
    }

    private fun createCentralDirectoryTail(limits: Limits): TailBuffer =
        TailBuffer(
            Math.addExact(
                limits.maxNestedApkCentralDirectoryBytes,
                ZIP_EOCD_MAX_BYTES + ZIP64_TRAILER_BYTES,
            ),
        )

    private fun findEocd(bytes: ByteArray): Int {
        if (bytes.size < ZIP_EOCD_MIN_BYTES) {
            throw IOException("Nested APK is too small to contain a ZIP EOCD")
        }
        val firstCandidate = maxOf(0, bytes.size - ZIP_EOCD_MAX_BYTES)
        for (offset in bytes.size - ZIP_EOCD_MIN_BYTES downTo firstCandidate) {
            if (readUInt32Unchecked(bytes, offset) != ZIP_EOCD_SIGNATURE) continue
            val commentLength = readUInt16(bytes, offset + ZIP_EOCD_COMMENT_LENGTH_OFFSET)
            if (offset + ZIP_EOCD_MIN_BYTES + commentLength == bytes.size) {
                return offset
            }
        }
        throw IOException("Nested APK ZIP EOCD was not found")
    }

    private fun looksLikeOversizedApkNativeLibrary(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Boolean {
        if (length < APK_NATIVE_PREFIX.size + SHARED_OBJECT_SUFFIX.size) return false
        return APK_NATIVE_PREFIX.indices.all { index ->
            bytes[offset + index] == APK_NATIVE_PREFIX[index]
        } && SHARED_OBJECT_SUFFIX.indices.all { index ->
            bytes[offset + length - SHARED_OBJECT_SUFFIX.size + index] ==
                SHARED_OBJECT_SUFFIX[index]
        }
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int {
        ensureRange(bytes, offset, 2)
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long {
        ensureRange(bytes, offset, 4)
        return readUInt32Unchecked(bytes, offset)
    }

    private fun readUInt64(bytes: ByteArray, offset: Int): Long {
        val low = readUInt32(bytes, offset)
        val high = readUInt32(bytes, offset + 4)
        if (high > Int.MAX_VALUE) throw IOException("Nested APK ZIP64 value exceeds the supported range")
        return low or (high shl 32)
    }

    private fun readZip64EntrySize(bytes: ByteArray, extraOffset: Int, extraLength: Int): Long {
        val end = extraOffset + extraLength
        var offset = extraOffset
        while (offset < end) {
            if (end - offset < 4) throw IOException("Nested APK ZIP extra field is truncated")
            val id = readUInt16(bytes, offset)
            val length = readUInt16(bytes, offset + 2)
            offset += 4
            if (length > end - offset) throw IOException("Nested APK ZIP extra field exceeds its boundary")
            if (id == 1) {
                if (length < 8) throw IOException("Nested APK ZIP64 entry size is missing")
                return readUInt64(bytes, offset)
            }
            offset += length
        }
        throw IOException("Nested APK ZIP64 entry size is missing")
    }

    private fun readUInt32Unchecked(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)

    private fun ensureRange(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) {
            throw IOException("Nested APK ZIP structure is truncated")
        }
    }

    private class Accumulator(
        deviceAbis: List<String>,
        private val limits: Limits,
    ) {
        private val deviceAbiOrder = deviceAbis.asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        private val groups = HashMap<String, MutableNativeLibraryAbiSummary>()
        private var totalLibraryCount = 0
        private var totalUncompressedBytes = 0L
        private var omittedLibraryCount = 0
        private var invalidEntryCount = 0
        private var failedApkCount = 0
        private var omittedApkCount = 0
        private var nestedScanLimitReached = false

        fun inspectEntry(
            path: String,
            size: Long,
            isDirectory: Boolean,
            layout: NativeLibraryLayout,
        ) {
            if (isDirectory) return
            when (val parsed = parseNativeLibraryPath(path, layout)) {
                NativeLibraryPath.NotLibrary -> Unit
                NativeLibraryPath.Invalid -> recordInvalidEntry()
                is NativeLibraryPath.Library -> {
                    if (size < 0L) {
                        recordInvalidEntry()
                    } else {
                        addLibrary(parsed.abi, size)
                    }
                }
            }
        }

        fun recordInvalidEntry() {
            invalidEntryCount = saturatingAddInt(invalidEntryCount, 1)
        }

        fun recordFailedApk() {
            failedApkCount = saturatingAddInt(failedApkCount, 1)
        }

        fun recordOmittedApks(count: Int) {
            omittedApkCount = saturatingAddInt(omittedApkCount, count.coerceAtLeast(0))
        }

        fun markNestedScanLimit() {
            nestedScanLimitReached = true
        }

        private fun addLibrary(abi: String, size: Long) {
            if (totalLibraryCount >= limits.maxNativeLibraryEntries) {
                omittedLibraryCount = saturatingAddInt(omittedLibraryCount, 1)
                return
            }
            totalLibraryCount = saturatingAddInt(totalLibraryCount, 1)
            totalUncompressedBytes = saturatingAddLong(totalUncompressedBytes, size)
            val group = groups.getOrPut(abi, ::MutableNativeLibraryAbiSummary)
            group.libraryCount = saturatingAddInt(group.libraryCount, 1)
            group.uncompressedBytes = saturatingAddLong(group.uncompressedBytes, size)
        }

        fun build(): NativeLibrarySummary {
            val preferredAbi = deviceAbiOrder.firstOrNull(groups::containsKey)
            val allGroups = groups.map { (abi, values) ->
                NativeLibraryAbiSummary(
                    abi = abi,
                    libraryCount = values.libraryCount,
                    uncompressedBytes = values.uncompressedBytes,
                    compatibility = when {
                        abi == preferredAbi -> NativeAbiCompatibility.PREFERRED
                        abi in deviceAbiOrder -> NativeAbiCompatibility.COMPATIBLE
                        else -> NativeAbiCompatibility.UNSUPPORTED
                    },
                )
            }.sortedWith(
                compareBy<NativeLibraryAbiSummary>(
                    NativeLibraryAbiSummary::compatibility,
                    { group -> group.abi.lowercase(Locale.ROOT) },
                    NativeLibraryAbiSummary::abi,
                ),
            )
            val displayedGroups = allGroups.take(limits.maxDisplayedAbis)
            return NativeLibrarySummary(
                abiGroups = displayedGroups,
                totalLibraryCount = totalLibraryCount,
                totalUncompressedBytes = totalUncompressedBytes,
                omittedLibraryCount = omittedLibraryCount,
                omittedAbiCount = allGroups.size - displayedGroups.size,
                invalidEntryCount = invalidEntryCount,
                failedApkCount = failedApkCount,
                omittedApkCount = omittedApkCount,
                nestedScanLimitReached = nestedScanLimitReached,
            )
        }
    }

    private class MutableNativeLibraryAbiSummary(
        var libraryCount: Int = 0,
        var uncompressedBytes: Long = 0L,
    )

    private class ScanBudget(remaining: Long) {
        var remaining = remaining
            private set

        fun read(input: InputStream, buffer: ByteArray): Int {
            if (remaining == 0L) {
                if (input.read() < 0) return -1
                throw PackageCodeScanLimitException()
            }
            val maximum = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, maximum)
            if (read > 0) remaining -= read.toLong()
            return read
        }
    }

    private class TailBuffer(capacity: Int) {
        private val bytes = ByteArray(capacity)
        private var size = 0
        private var writeOffset = 0

        fun reset() {
            size = 0
            writeOffset = 0
        }

        fun append(source: ByteArray, offset: Int, length: Int) {
            if (length >= bytes.size) {
                source.copyInto(
                    destination = bytes,
                    destinationOffset = 0,
                    startIndex = offset + length - bytes.size,
                    endIndex = offset + length,
                )
                size = bytes.size
                writeOffset = 0
                return
            }
            val firstLength = minOf(length, bytes.size - writeOffset)
            source.copyInto(
                destination = bytes,
                destinationOffset = writeOffset,
                startIndex = offset,
                endIndex = offset + firstLength,
            )
            val remainingLength = length - firstLength
            if (remainingLength > 0) {
                source.copyInto(
                    destination = bytes,
                    destinationOffset = 0,
                    startIndex = offset + firstLength,
                    endIndex = offset + length,
                )
            }
            writeOffset = (writeOffset + length) % bytes.size
            size = minOf(bytes.size, size + length)
        }

        fun toByteArray(): ByteArray {
            if (size < bytes.size) return bytes.copyOf(size)
            return ByteArray(size).also { result ->
                val firstLength = bytes.size - writeOffset
                bytes.copyInto(
                    destination = result,
                    destinationOffset = 0,
                    startIndex = writeOffset,
                    endIndex = bytes.size,
                )
                if (writeOffset > 0) {
                    bytes.copyInto(
                        destination = result,
                        destinationOffset = firstLength,
                        startIndex = 0,
                        endIndex = writeOffset,
                    )
                }
            }
        }
    }

    private sealed interface NativeLibraryPath {
        data class Library(val abi: String) : NativeLibraryPath
        data object NotLibrary : NativeLibraryPath
        data object Invalid : NativeLibraryPath
    }

    private fun parseNativeLibraryPath(
        path: String,
        layout: NativeLibraryLayout,
    ): NativeLibraryPath {
        if (!path.endsWith(".so")) return NativeLibraryPath.NotLibrary
        val segments = path.split('/')
        val abi = when (layout) {
            NativeLibraryLayout.APK -> {
                if (segments.firstOrNull() != "lib") return NativeLibraryPath.NotLibrary
                if (segments.size != 3) return NativeLibraryPath.Invalid
                segments[1]
            }
            NativeLibraryLayout.AAB -> {
                if (segments.getOrNull(1) != "lib") return NativeLibraryPath.NotLibrary
                if (segments.size != 4 || segments[0].isEmpty()) {
                    return NativeLibraryPath.Invalid
                }
                segments[2]
            }
        }
        val fileName = segments.last()
        if (
            !ABI_NAME.matches(abi) ||
            fileName.isEmpty() ||
            path.any { character -> character == '\u0000' || character == '\\' } ||
            segments.any { segment -> segment.isEmpty() || segment == ".." }
        ) {
            return NativeLibraryPath.Invalid
        }
        return NativeLibraryPath.Library(abi)
    }

    private enum class NativeLibraryLayout {
        APK,
        AAB,
    }

    private data class NestedApkScanOutcome(
        val entryLimitReached: Boolean,
    )

    private class PackageCodeScanLimitException : IOException()

    private val ABI_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._+-]{0,63}""")
    private val APK_NATIVE_PREFIX = "lib/".toByteArray(StandardCharsets.US_ASCII)
    private val SHARED_OBJECT_SUFFIX = ".so".toByteArray(StandardCharsets.US_ASCII)

    private const val MAX_NESTED_APK_ENTRIES = PackageInspectionLimits.ARCHIVE_ENTRIES
    private const val MAX_ENTRY_NAME_BYTES = PackageInspectionLimits.ENTRY_NAME_CHARS

    private const val ZIP_CENTRAL_HEADER_SIGNATURE = 0x02014B50L
    private const val ZIP_CENTRAL_HEADER_BYTES = 46
    private const val ZIP_CENTRAL_UNCOMPRESSED_SIZE_OFFSET = 24
    private const val ZIP_CENTRAL_NAME_LENGTH_OFFSET = 28
    private const val ZIP_CENTRAL_EXTRA_LENGTH_OFFSET = 30
    private const val ZIP_CENTRAL_COMMENT_LENGTH_OFFSET = 32

    private const val ZIP_EOCD_SIGNATURE = 0x06054B50L
    private const val ZIP64_TRAILER_BYTES = 76
    private const val ZIP_EOCD_MIN_BYTES = 22
    private const val ZIP_EOCD_MAX_COMMENT_BYTES = 65_535
    private const val ZIP_EOCD_MAX_BYTES =
        ZIP_EOCD_MIN_BYTES + ZIP_EOCD_MAX_COMMENT_BYTES
    private const val ZIP_EOCD_DISK_NUMBER_OFFSET = 4
    private const val ZIP_EOCD_DIRECTORY_DISK_OFFSET = 6
    private const val ZIP_EOCD_ENTRIES_ON_DISK_OFFSET = 8
    private const val ZIP_EOCD_TOTAL_ENTRIES_OFFSET = 10
    private const val ZIP_EOCD_DIRECTORY_SIZE_OFFSET = 12
    private const val ZIP_EOCD_DIRECTORY_OFFSET_OFFSET = 16
    private const val ZIP_EOCD_COMMENT_LENGTH_OFFSET = 20
    private const val ZIP_UINT16_MAX = 0xFFFF
    private const val ZIP_UINT32_MAX = 0xFFFF_FFFFL
}

internal fun saturatingAddInt(left: Int, right: Int): Int =
    (left.toLong() + right.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun saturatingAddLong(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
