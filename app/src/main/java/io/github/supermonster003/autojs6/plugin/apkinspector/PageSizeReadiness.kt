package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Overall answer to "is this package ready for 16 KB page-size devices?".
 *
 * Android 15+ devices with 16 KB memory pages only run 64-bit code, so the check covers the
 * `arm64-v8a`, `x86_64` and `riscv64` directories: every `PT_LOAD` segment of every shared library
 * must be aligned to at least 16 KB and, when the manifest keeps libraries inside the APK
 * (`extractNativeLibs="false"`), each stored library must also start at a 16 KB-aligned ZIP data
 * offset. 32-bit directories are reported by the plain native-library section and ignored here.
 */
internal enum class PageSizeReadinessState {
    /** Every evaluated 64-bit library is aligned; stored entries sit on aligned ZIP offsets. */
    READY,

    /** At least one 64-bit library or stored ZIP offset is below the 16 KB alignment. */
    NOT_READY,

    /** Nothing failed, but some libraries could not be read or were omitted by a limit. */
    UNVERIFIED,

    /** The completed scan found no 64-bit native libraries, so page size cannot matter. */
    NO_64BIT_LIBRARIES,

    /** Nested container APKs are only indexed through their central directories. */
    NOT_EVALUATED,
}

internal data class PageSizeAbiReadiness(
    val abi: String,
    val libraryCount: Int,
    val alignedCount: Int,
    val unalignedCount: Int,
    val unreadableCount: Int,
    val zipMisalignedCount: Int,
    val examples: List<String>,
)

internal data class PageSizeReadinessSummary(
    val state: PageSizeReadinessState,
    val extractNativeLibs: Boolean? = null,
    val abis: List<PageSizeAbiReadiness> = emptyList(),
    val omittedLibraryCount: Int = 0,
    val zipOffsetsChecked: Boolean = false,
) {
    val libraryCount: Int get() = abis.sumOf(PageSizeAbiReadiness::libraryCount)
    val unalignedCount: Int get() = abis.sumOf(PageSizeAbiReadiness::unalignedCount)
    val unreadableCount: Int get() = abis.sumOf(PageSizeAbiReadiness::unreadableCount)
    val zipMisalignedCount: Int get() = abis.sumOf(PageSizeAbiReadiness::zipMisalignedCount)

    companion object {
        val NOT_EVALUATED = PageSizeReadinessSummary(PageSizeReadinessState.NOT_EVALUATED)
    }
}

/**
 * Reads only the ELF header and program-header table of each 64-bit library (at most
 * [MAX_ELF_HEADER_BYTES] per entry, streamed through the ZIP inflater) plus, when needed, the ZIP
 * central directory of the package itself. Library bodies are never inflated and nothing is written.
 */
internal object PageSizeReadinessInspector {

    const val PAGE_SIZE = 16_384L
    const val MAX_ELF_HEADER_BYTES = 64 * 1024
    const val MAX_EXAMPLES = 3
    const val MAX_LIBRARIES = NativeLibraryInspector.MAX_NATIVE_LIBRARY_ENTRIES

    private val SIXTY_FOUR_BIT_ABIS = setOf("arm64-v8a", "x86_64", "riscv64")
    private val ABI_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._+-]{0,63}""")

    fun inspectApk(
        file: File,
        zip: ZipFile,
        entries: Iterable<ZipEntry>,
        extractNativeLibs: Boolean?,
        maxLibraries: Int = MAX_LIBRARIES,
    ): PageSizeReadinessSummary {
        val libraries = entries.mapNotNull { entry -> apkLibrary(entry) }
        // Libraries stay inside the APK only when the manifest says so explicitly.
        val checkOffsets = extractNativeLibs == false
        val offsets = if (checkOffsets && libraries.any { (entry, _) -> entry.method == ZipEntry.STORED }) {
            readLocalHeaderOffsets(file, libraries.map { (entry, _) -> entry.name }.toSet())
        } else {
            null
        }
        return evaluate(zip, libraries, extractNativeLibs, offsets, checkOffsets, file, maxLibraries)
    }

    fun inspectAab(
        zip: ZipFile,
        entries: Iterable<ZipEntry>,
        maxLibraries: Int = MAX_LIBRARIES,
    ): PageSizeReadinessSummary {
        val libraries = entries.mapNotNull { entry -> aabLibrary(entry) }
        // bundletool repackages the libraries, so the bundle's own ZIP layout is not checked.
        return evaluate(zip, libraries, null, null, false, null, maxLibraries)
    }

    private fun evaluate(
        zip: ZipFile,
        libraries: List<Pair<ZipEntry, String>>,
        extractNativeLibs: Boolean?,
        offsets: Map<String, Long>?,
        checkOffsets: Boolean,
        file: File?,
        maxLibraries: Int,
    ): PageSizeReadinessSummary {
        require(maxLibraries > 0)
        if (libraries.isEmpty()) {
            return PageSizeReadinessSummary(
                state = PageSizeReadinessState.NO_64BIT_LIBRARIES,
                extractNativeLibs = extractNativeLibs,
                zipOffsetsChecked = checkOffsets,
            )
        }
        val groups = LinkedHashMap<String, MutableAbiReadiness>()
        var evaluated = 0
        var omitted = 0
        for ((entry, abi) in libraries) {
            if (evaluated >= maxLibraries) {
                omitted++
                continue
            }
            evaluated++
            val group = groups.getOrPut(abi) { MutableAbiReadiness() }
            group.libraryCount++
            val name = entry.name.substringAfterLast('/')
            when (readMinimumLoadAlignment(zip, entry)) {
                null -> {
                    group.unreadableCount++
                    group.addExample(name)
                }
                in PAGE_SIZE..Long.MAX_VALUE -> group.alignedCount++
                else -> {
                    group.unalignedCount++
                    group.addExample(name)
                }
            }
            if (checkOffsets && entry.method == ZipEntry.STORED && file != null) {
                val dataOffset = offsets?.get(entry.name)?.let { local -> readDataOffset(file, local) }
                if (dataOffset == null) {
                    group.unreadableCount++
                    group.addExample(name)
                } else if (dataOffset % PAGE_SIZE != 0L) {
                    group.zipMisalignedCount++
                    group.addExample(name)
                }
            }
        }
        val abis = groups.map { (abi, values) ->
            PageSizeAbiReadiness(
                abi = abi,
                libraryCount = values.libraryCount,
                alignedCount = values.alignedCount,
                unalignedCount = values.unalignedCount,
                unreadableCount = values.unreadableCount,
                zipMisalignedCount = values.zipMisalignedCount,
                examples = values.examples.toList(),
            )
        }.sortedBy(PageSizeAbiReadiness::abi)
        val state = when {
            abis.any { it.unalignedCount > 0 || it.zipMisalignedCount > 0 } -> PageSizeReadinessState.NOT_READY
            omitted > 0 || abis.any { it.unreadableCount > 0 } -> PageSizeReadinessState.UNVERIFIED
            else -> PageSizeReadinessState.READY
        }
        return PageSizeReadinessSummary(
            state = state,
            extractNativeLibs = extractNativeLibs,
            abis = abis,
            omittedLibraryCount = omitted,
            zipOffsetsChecked = checkOffsets,
        )
    }

    private fun apkLibrary(entry: ZipEntry): Pair<ZipEntry, String>? {
        if (entry.isDirectory || !entry.name.endsWith(".so")) return null
        val segments = entry.name.split('/')
        if (segments.size != 3 || segments[0] != "lib") return null
        return library(entry, segments[1])
    }

    private fun aabLibrary(entry: ZipEntry): Pair<ZipEntry, String>? {
        if (entry.isDirectory || !entry.name.endsWith(".so")) return null
        val segments = entry.name.split('/')
        if (segments.size != 4 || segments[0].isEmpty() || segments[1] != "lib") return null
        return library(entry, segments[2])
    }

    private fun library(entry: ZipEntry, abi: String): Pair<ZipEntry, String>? {
        if (abi !in SIXTY_FOUR_BIT_ABIS || !ABI_NAME.matches(abi)) return null
        if (entry.name.substringAfterLast('/').isEmpty()) return null
        return entry to abi
    }

    /**
     * Returns the smallest `p_align` among the `PT_LOAD` program headers, or null when the entry is
     * not a readable ELF file (bad magic, truncated table, header table beyond the read budget, or
     * no `PT_LOAD` segment at all).
     */
    internal fun readMinimumLoadAlignment(zip: ZipFile, entry: ZipEntry): Long? = try {
        zip.getInputStream(entry).use { input -> readMinimumLoadAlignment(input) }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    internal fun readMinimumLoadAlignment(input: InputStream): Long? {
        val header = ByteArray(ELF_HEADER_BYTES_64)
        val headerRead = readFully(input, header, header.size)
        if (headerRead < ELF_HEADER_BYTES_32) return null
        if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
        ) {
            return null
        }
        val is64 = when (header[4].toInt()) {
            1 -> false
            2 -> true
            else -> return null
        }
        val littleEndian = when (header[5].toInt()) {
            1 -> true
            2 -> false
            else -> return null
        }
        if (is64 && headerRead < ELF_HEADER_BYTES_64) return null
        val phoff: Long
        val phentsize: Int
        val phnum: Int
        if (is64) {
            phoff = readLong(header, 0x20, littleEndian)
            phentsize = readShort(header, 0x36, littleEndian)
            phnum = readShort(header, 0x38, littleEndian)
        } else {
            phoff = readInt(header, 0x1C, littleEndian)
            phentsize = readShort(header, 0x2A, littleEndian)
            phnum = readShort(header, 0x2C, littleEndian)
        }
        val minEntry = if (is64) PHDR_BYTES_64 else PHDR_BYTES_32
        val headerBytes = if (is64) ELF_HEADER_BYTES_64 else ELF_HEADER_BYTES_32
        if (phnum == 0 || phentsize < minEntry || phoff < headerBytes) return null
        val tableEnd = phoff + phentsize.toLong() * phnum
        if (tableEnd > MAX_ELF_HEADER_BYTES) return null
        val table = ByteArray(phentsize * phnum)
        var filled = 0
        if (phoff < headerRead) {
            // A 32-bit table can start inside the 64 bytes that were already consumed.
            filled = minOf((headerRead - phoff).toInt(), table.size)
            System.arraycopy(header, phoff.toInt(), table, 0, filled)
        } else {
            var toSkip = phoff - headerRead
            val scratch = ByteArray(4096)
            while (toSkip > 0) {
                val read = input.read(scratch, 0, minOf(scratch.size.toLong(), toSkip).toInt())
                if (read < 0) return null
                toSkip -= read
            }
        }
        while (filled < table.size) {
            val read = input.read(table, filled, table.size - filled)
            if (read < 0) return null
            filled += read
        }
        var minimum: Long? = null
        for (index in 0 until phnum) {
            val base = index * phentsize
            val type = readInt(table, base, littleEndian)
            if (type != PT_LOAD) continue
            val align = if (is64) readLong(table, base + 48, littleEndian) else readInt(table, base + 28, littleEndian)
            if (align < 0) return null
            minimum = if (minimum == null) align else minOf(minimum, align)
        }
        return minimum
    }

    /**
     * Maps the given entry names to their local-header offsets by walking the central directory of
     * [file]. ZIP64 offsets and archives with a ZIP64 directory are reported as absent so that the
     * caller marks those entries unverified instead of guessing.
     */
    internal fun readLocalHeaderOffsets(file: File, names: Set<String>): Map<String, Long> {
        if (names.isEmpty()) return emptyMap()
        val result = HashMap<String, Long>()
        try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                val tailSize = minOf(length, (EOCD_MIN_BYTES + EOCD_MAX_COMMENT_BYTES).toLong()).toInt()
                if (tailSize < EOCD_MIN_BYTES) return emptyMap()
                val tail = ByteArray(tailSize)
                raf.seek(length - tailSize)
                raf.readFully(tail)
                var eocd = -1
                for (offset in tail.size - EOCD_MIN_BYTES downTo 0) {
                    if (readInt(tail, offset, true) == EOCD_SIGNATURE) {
                        val comment = readShort(tail, offset + 20, true)
                        if (offset + EOCD_MIN_BYTES + comment == tail.size) {
                            eocd = offset
                            break
                        }
                    }
                }
                if (eocd < 0) return emptyMap()
                val totalEntries = readShort(tail, eocd + 10, true)
                val directorySize = readInt(tail, eocd + 12, true)
                val directoryOffset = readInt(tail, eocd + 16, true)
                if (totalEntries == 0xFFFF || directorySize == 0xFFFF_FFFFL || directoryOffset == 0xFFFF_FFFFL) {
                    return emptyMap()
                }
                if (directorySize > MAX_CENTRAL_DIRECTORY_BYTES || directoryOffset + directorySize > length) {
                    return emptyMap()
                }
                val directory = ByteArray(directorySize.toInt())
                raf.seek(directoryOffset)
                raf.readFully(directory)
                var cursor = 0
                var seen = 0
                while (cursor + CENTRAL_HEADER_BYTES <= directory.size && seen < totalEntries) {
                    if (readInt(directory, cursor, true) != CENTRAL_HEADER_SIGNATURE) break
                    val nameLength = readShort(directory, cursor + 28, true)
                    val extraLength = readShort(directory, cursor + 30, true)
                    val commentLength = readShort(directory, cursor + 32, true)
                    val localOffset = readInt(directory, cursor + 42, true)
                    val nameStart = cursor + CENTRAL_HEADER_BYTES
                    val recordEnd = nameStart + nameLength + extraLength + commentLength
                    if (recordEnd > directory.size) break
                    val name = String(directory, nameStart, nameLength, StandardCharsets.UTF_8)
                    if (name in names && localOffset != 0xFFFF_FFFFL) {
                        result[name] = localOffset
                    }
                    cursor = recordEnd
                    seen++
                }
            }
        } catch (_: IOException) {
            return emptyMap()
        }
        return result
    }

    /** Resolves the first data byte of a local entry: header, then name and extra field. */
    internal fun readDataOffset(file: File, localHeaderOffset: Long): Long? = try {
        RandomAccessFile(file, "r").use { raf ->
            if (localHeaderOffset < 0 || localHeaderOffset + LOCAL_HEADER_BYTES > raf.length()) return null
            val header = ByteArray(LOCAL_HEADER_BYTES)
            raf.seek(localHeaderOffset)
            raf.readFully(header)
            if (readInt(header, 0, true) != LOCAL_HEADER_SIGNATURE) return null
            val nameLength = readShort(header, 26, true)
            val extraLength = readShort(header, 28, true)
            localHeaderOffset + LOCAL_HEADER_BYTES + nameLength + extraLength
        }
    } catch (_: IOException) {
        null
    } catch (_: EOFException) {
        null
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, total, length - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun readShort(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        return if (littleEndian) b0 or (b1 shl 8) else (b0 shl 8) or b1
    }

    private fun readInt(bytes: ByteArray, offset: Int, littleEndian: Boolean): Long {
        var value = 0L
        for (index in 0 until 4) {
            val shift = if (littleEndian) index * 8 else (3 - index) * 8
            value = value or ((bytes[offset + index].toLong() and 0xFFL) shl shift)
        }
        return value
    }

    private fun readLong(bytes: ByteArray, offset: Int, littleEndian: Boolean): Long {
        var value = 0L
        for (index in 0 until 8) {
            val shift = if (littleEndian) index * 8 else (7 - index) * 8
            value = value or ((bytes[offset + index].toLong() and 0xFFL) shl shift)
        }
        return value
    }

    private class MutableAbiReadiness {
        var libraryCount = 0
        var alignedCount = 0
        var unalignedCount = 0
        var unreadableCount = 0
        var zipMisalignedCount = 0
        val examples = LinkedHashSet<String>()

        fun addExample(name: String) {
            if (examples.size < MAX_EXAMPLES) examples.add(name)
        }
    }

    private const val PT_LOAD = 1L
    private const val ELF_HEADER_BYTES_32 = 52
    private const val ELF_HEADER_BYTES_64 = 64
    private const val PHDR_BYTES_32 = 32
    private const val PHDR_BYTES_64 = 56
    private const val EOCD_SIGNATURE = 0x06054B50L
    private const val EOCD_MIN_BYTES = 22
    private const val EOCD_MAX_COMMENT_BYTES = 65_535
    private const val CENTRAL_HEADER_SIGNATURE = 0x02014B50L
    private const val CENTRAL_HEADER_BYTES = 46
    private const val LOCAL_HEADER_SIGNATURE = 0x04034B50L
    private const val LOCAL_HEADER_BYTES = 30
    private const val MAX_CENTRAL_DIRECTORY_BYTES = 32L * 1024 * 1024
}
