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
 * Collects native-library metadata without inflating library contents.
 *
 * Direct APK and AAB inputs use their already-validated ZIP directory entries. APKs nested in a
 * package container are streamed once and retain only a bounded tail containing the ZIP central
 * directory. This keeps native-library inspection independent from manifest parsing and lets this
 * section degrade without invalidating the rest of the report.
 */
internal object NativeLibraryInspector {

    const val MAX_NATIVE_LIBRARY_ENTRIES = 4_096
    const val MAX_DISPLAYED_NATIVE_ABIS = 64
    const val MAX_SELECTED_APK_SCANS = 512
    const val MAX_NESTED_APK_SCAN_BYTES = 256L * 1024L * 1024L
    const val MAX_NESTED_APK_CENTRAL_DIRECTORY_BYTES = 8 * 1024 * 1024

    internal data class Limits(
        val maxNativeLibraryEntries: Int = MAX_NATIVE_LIBRARY_ENTRIES,
        val maxDisplayedAbis: Int = MAX_DISPLAYED_NATIVE_ABIS,
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
            require(maxSelectedApkScans > 0)
            require(maxNestedApkScanBytes > 0L)
            require(maxNestedApkCentralDirectoryBytes > 0)
            require(
                maxNestedApkCentralDirectoryBytes <=
                    Int.MAX_VALUE - ZIP_EOCD_MAX_BYTES,
            )
            require(maxNestedApkEntries > 0)
            require(maxEntryNameBytes > 0)
        }
    }

    fun inspectApkEntries(
        entries: Iterable<ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): NativeLibrarySummary {
        val accumulator = Accumulator(deviceAbis, limits)
        entries.forEach { entry ->
            accumulator.inspectEntry(
                path = entry.name,
                size = entry.size,
                isDirectory = entry.isDirectory,
                layout = NativeLibraryLayout.APK,
            )
        }
        return accumulator.build()
    }

    fun inspectAabEntries(
        entries: Iterable<ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): NativeLibrarySummary {
        val accumulator = Accumulator(deviceAbis, limits)
        entries.forEach { entry ->
            accumulator.inspectEntry(
                path = entry.name,
                size = entry.size,
                isDirectory = entry.isDirectory,
                layout = NativeLibraryLayout.AAB,
            )
        }
        return accumulator.build()
    }

    fun inspectNestedApks(
        zip: ZipFile,
        selectedApks: List<ArchiveApkEntry>,
        entriesByPath: Map<String, ZipEntry>,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): NativeLibrarySummary {
        val accumulator = Accumulator(deviceAbis, limits)
        val selectedPaths = selectedApks.asSequence()
            .map(ArchiveApkEntry::archivePath)
            .distinct()
            .toList()
        val pathsToScan = selectedPaths.take(limits.maxSelectedApkScans)
        accumulator.recordOmittedApks(selectedPaths.size - pathsToScan.size)
        val budget = ScanBudget(limits.maxNestedApkScanBytes)
        val centralDirectoryTail = createCentralDirectoryTail(limits)

        pathsToScan.forEach { path ->
            val entry = entriesByPath[path]
            if (entry == null || entry.isDirectory) {
                accumulator.recordFailedApk()
                return@forEach
            }
            if (entry.size > budget.remaining) {
                accumulator.recordOmittedApks(1)
                accumulator.markNestedScanLimit()
                return@forEach
            }
            try {
                val outcome = zip.getInputStream(entry).use { input ->
                    scanNestedApkCentralDirectory(
                        input = input,
                        budget = budget,
                        accumulator = accumulator,
                        limits = limits,
                        tail = centralDirectoryTail,
                    )
                }
                if (outcome.entryLimitReached) {
                    accumulator.markNestedScanLimit()
                }
            } catch (_: NativeLibraryScanLimitException) {
                accumulator.recordOmittedApks(1)
                accumulator.markNestedScanLimit()
            } catch (_: IOException) {
                accumulator.recordFailedApk()
            }
        }
        return accumulator.build()
    }

    internal fun inspectNestedApk(
        input: InputStream,
        deviceAbis: List<String>,
        limits: Limits = Limits(),
    ): NativeLibrarySummary {
        val accumulator = Accumulator(deviceAbis, limits)
        val budget = ScanBudget(limits.maxNestedApkScanBytes)
        val centralDirectoryTail = createCentralDirectoryTail(limits)
        try {
            val outcome = scanNestedApkCentralDirectory(
                input = input,
                budget = budget,
                accumulator = accumulator,
                limits = limits,
                tail = centralDirectoryTail,
            )
            if (outcome.entryLimitReached) {
                accumulator.markNestedScanLimit()
            }
        } catch (_: NativeLibraryScanLimitException) {
            accumulator.recordOmittedApks(1)
            accumulator.markNestedScanLimit()
        } catch (_: IOException) {
            accumulator.recordFailedApk()
        }
        return accumulator.build()
    }

    private fun scanNestedApkCentralDirectory(
        input: InputStream,
        budget: ScanBudget,
        accumulator: Accumulator,
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
        val totalEntries =
            readUInt16(bytes, eocdOffset + ZIP_EOCD_TOTAL_ENTRIES_OFFSET)
        if (diskNumber != 0 || centralDirectoryDisk != 0 || entriesOnDisk != totalEntries) {
            throw IOException("Split ZIP archives are not supported for nested APK inspection")
        }
        val centralDirectorySize =
            readUInt32(bytes, eocdOffset + ZIP_EOCD_DIRECTORY_SIZE_OFFSET)
        val centralDirectoryOffset =
            readUInt32(bytes, eocdOffset + ZIP_EOCD_DIRECTORY_OFFSET_OFFSET)
        if (
            totalEntries == ZIP_UINT16_MAX ||
            centralDirectorySize == ZIP_UINT32_MAX ||
            centralDirectoryOffset == ZIP_UINT32_MAX
        ) {
            throw IOException("ZIP64 nested APK inspection is not supported")
        }
        if (centralDirectorySize > limits.maxNestedApkCentralDirectoryBytes) {
            throw NativeLibraryScanLimitException()
        }

        val eocdAbsoluteOffset = Math.addExact(tailStart, eocdOffset.toLong())
        val centralDirectoryEnd = Math.addExact(
            centralDirectoryOffset,
            centralDirectorySize,
        )
        if (centralDirectoryEnd > eocdAbsoluteOffset) {
            throw IOException("Nested APK central directory exceeds the EOCD boundary")
        }
        if (centralDirectoryOffset < tailStart) {
            throw NativeLibraryScanLimitException()
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

        val entriesToRead = minOf(totalEntries, limits.maxNestedApkEntries)
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
                accumulator.inspectEntry(
                    path = path,
                    size = uncompressedSize.takeUnless { it == ZIP_UINT32_MAX } ?: -1L,
                    isDirectory = path.endsWith('/'),
                    layout = NativeLibraryLayout.APK,
                )
            } else if (
                looksLikeOversizedApkNativeLibrary(
                    bytes = bytes,
                    offset = nameOffset,
                    length = nameLength,
                )
            ) {
                accumulator.recordInvalidEntry()
            }
            offset = nextOffset
        }

        return NestedApkScanOutcome(
            entryLimitReached = totalEntries > entriesToRead,
        )
    }

    private fun createCentralDirectoryTail(limits: Limits): TailBuffer =
        TailBuffer(
            Math.addExact(
                limits.maxNestedApkCentralDirectoryBytes,
                ZIP_EOCD_MAX_BYTES,
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
                throw NativeLibraryScanLimitException()
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

    private class NativeLibraryScanLimitException : IOException()

    private val ABI_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._+-]{0,63}""")
    private val APK_NATIVE_PREFIX = "lib/".toByteArray(StandardCharsets.US_ASCII)
    private val SHARED_OBJECT_SUFFIX = ".so".toByteArray(StandardCharsets.US_ASCII)

    private const val MAX_NESTED_APK_ENTRIES = 16_384
    private const val MAX_ENTRY_NAME_BYTES = 1_024

    private const val ZIP_CENTRAL_HEADER_SIGNATURE = 0x02014B50L
    private const val ZIP_CENTRAL_HEADER_BYTES = 46
    private const val ZIP_CENTRAL_UNCOMPRESSED_SIZE_OFFSET = 24
    private const val ZIP_CENTRAL_NAME_LENGTH_OFFSET = 28
    private const val ZIP_CENTRAL_EXTRA_LENGTH_OFFSET = 30
    private const val ZIP_CENTRAL_COMMENT_LENGTH_OFFSET = 32

    private const val ZIP_EOCD_SIGNATURE = 0x06054B50L
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

private fun saturatingAddInt(left: Int, right: Int): Int =
    (left.toLong() + right.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private fun saturatingAddLong(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
