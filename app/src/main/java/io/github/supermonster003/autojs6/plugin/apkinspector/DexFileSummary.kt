package io.github.supermonster003.autojs6.plugin.apkinspector

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.PriorityQueue

internal data class DexFileEntrySummary(
    val path: String,
    val uncompressedBytes: Long,
)

internal data class DexFileSummary(
    val files: List<DexFileEntrySummary> = emptyList(),
    val totalFileCount: Int = 0,
    val totalUncompressedBytes: Long = 0L,
    val omittedFileCount: Int = 0,
    val invalidEntryCount: Int = 0,
    val failedApkCount: Int = 0,
    val omittedApkCount: Int = 0,
    val nestedScanLimitReached: Boolean = false,
) {
    val hasPartialResults: Boolean
        get() = omittedFileCount > 0 ||
            invalidEntryCount > 0 ||
            failedApkCount > 0 ||
            omittedApkCount > 0 ||
            nestedScanLimitReached

    companion object {
        const val MAX_DISPLAYED_FILES = 128
    }
}

internal data class PackageCodeSummary(
    val nativeLibraries: NativeLibrarySummary,
    val dexFiles: DexFileSummary,
)

internal enum class DexFileLayout {
    APK,
    AAB,
}

/** Retains only the naturally first DEX paths while counting every bounded valid entry. */
internal class DexFileAccumulator(
    private val maxDisplayedFiles: Int,
) {
    private val retainedFiles = PriorityQueue(
        maxDisplayedFiles,
        DEX_FILE_COMPARATOR.reversed(),
    )
    private var totalFileCount = 0
    private var totalUncompressedBytes = 0L
    private var invalidEntryCount = 0
    private var failedApkCount = 0
    private var omittedApkCount = 0
    private var nestedScanLimitReached = false

    fun inspectEntry(
        path: String,
        size: Long,
        isDirectory: Boolean,
        layout: DexFileLayout,
        sourcePath: String? = null,
    ) {
        if (isDirectory) return
        when (val parsed = parseDexFilePath(path, layout, sourcePath)) {
            DexFilePath.NotDex -> Unit
            DexFilePath.Invalid -> recordInvalidEntry()
            is DexFilePath.File -> {
                if (size < 0L) {
                    recordInvalidEntry()
                } else {
                    addFile(parsed, size)
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

    fun build(): DexFileSummary {
        val displayedFiles = retainedFiles.toList()
            .sortedWith(DEX_FILE_COMPARATOR)
            .map { file ->
                DexFileEntrySummary(
                    path = file.displayPath,
                    uncompressedBytes = file.uncompressedBytes,
                )
            }
        return DexFileSummary(
            files = displayedFiles,
            totalFileCount = totalFileCount,
            totalUncompressedBytes = totalUncompressedBytes,
            omittedFileCount = (totalFileCount - displayedFiles.size).coerceAtLeast(0),
            invalidEntryCount = invalidEntryCount,
            failedApkCount = failedApkCount,
            omittedApkCount = omittedApkCount,
            nestedScanLimitReached = nestedScanLimitReached,
        )
    }

    private fun addFile(path: DexFilePath.File, size: Long) {
        totalFileCount = saturatingAddInt(totalFileCount, 1)
        totalUncompressedBytes = saturatingAddLong(totalUncompressedBytes, size)
        val candidate = RetainedDexFile(
            displayPath = path.displayPath,
            sourceSortKey = path.sourceSortKey,
            ordinalDigits = path.ordinalDigits,
            uncompressedBytes = size,
        )
        if (retainedFiles.size < maxDisplayedFiles) {
            retainedFiles += candidate
        } else if (DEX_FILE_COMPARATOR.compare(candidate, retainedFiles.peek()) < 0) {
            retainedFiles.poll()
            retainedFiles += candidate
        }
    }
}

private sealed interface DexFilePath {
    data class File(
        val displayPath: String,
        val sourceSortKey: String,
        val ordinalDigits: String,
    ) : DexFilePath

    data object NotDex : DexFilePath
    data object Invalid : DexFilePath
}

private data class RetainedDexFile(
    val displayPath: String,
    val sourceSortKey: String,
    val ordinalDigits: String,
    val uncompressedBytes: Long,
)

private fun parseDexFilePath(
    path: String,
    layout: DexFileLayout,
    sourcePath: String?,
): DexFilePath {
    val segments = path.split('/')
    val fileName = segments.lastOrNull() ?: return DexFilePath.NotDex
    val ordinalDigits = parseDexOrdinal(fileName) ?: return if (
        fileName.startsWith(DEX_FILE_PREFIX) && fileName.endsWith(DEX_FILE_SUFFIX)
    ) {
        DexFilePath.Invalid
    } else {
        DexFilePath.NotDex
    }
    if (
        path.any { character -> character == '\u0000' || character == '\\' } ||
        segments.any { segment -> segment.isEmpty() || segment == ".." }
    ) {
        return DexFilePath.Invalid
    }

    val sourceSortKey = when (layout) {
        DexFileLayout.APK -> {
            if (segments.size != 1) return DexFilePath.Invalid
            sourcePath.orEmpty()
        }
        DexFileLayout.AAB -> {
            if (segments.size != 3 || segments[1] != "dex") return DexFilePath.Invalid
            segments[0]
        }
    }
    val displayPath = sourcePath?.let { source -> "$source!/$path" } ?: path
    return DexFilePath.File(
        displayPath = displayPath,
        sourceSortKey = sourceSortKey,
        ordinalDigits = ordinalDigits,
    )
}

private fun parseDexOrdinal(fileName: String): String? {
    if (fileName == PRIMARY_DEX_FILE) return "1"
    if (!fileName.startsWith(DEX_FILE_PREFIX) || !fileName.endsWith(DEX_FILE_SUFFIX)) {
        return null
    }
    val digits = fileName.substring(
        DEX_FILE_PREFIX.length,
        fileName.length - DEX_FILE_SUFFIX.length,
    )
    if (
        digits.isEmpty() ||
        digits.first() == '0' ||
        digits.any { digit -> digit !in '0'..'9' } ||
        digits == "1"
    ) {
        return null
    }
    return digits
}

internal fun looksLikeOversizedApkDexFile(
    bytes: ByteArray,
    offset: Int,
    length: Int,
): Boolean {
    if (length < DEX_PREFIX_BYTES.size + DEX_SUFFIX_BYTES.size) return false
    return DEX_PREFIX_BYTES.indices.all { index ->
        bytes[offset + index] == DEX_PREFIX_BYTES[index]
    } && DEX_SUFFIX_BYTES.indices.all { index ->
        bytes[offset + length - DEX_SUFFIX_BYTES.size + index] == DEX_SUFFIX_BYTES[index]
    }
}

private val DEX_FILE_COMPARATOR = Comparator<RetainedDexFile> { left, right ->
    compareValues(
        left.sourceSortKey.lowercase(Locale.ROOT),
        right.sourceSortKey.lowercase(Locale.ROOT),
    ).takeIf { it != 0 }
        ?: compareValues(left.sourceSortKey, right.sourceSortKey).takeIf { it != 0 }
        ?: compareDexOrdinals(left.ordinalDigits, right.ordinalDigits).takeIf { it != 0 }
        ?: compareValues(left.displayPath, right.displayPath)
}

private fun compareDexOrdinals(left: String, right: String): Int =
    compareValues(left.length, right.length).takeIf { it != 0 }
        ?: compareValues(left, right)

private const val DEX_FILE_PREFIX = "classes"
private const val DEX_FILE_SUFFIX = ".dex"
private const val PRIMARY_DEX_FILE = "$DEX_FILE_PREFIX$DEX_FILE_SUFFIX"
private val DEX_PREFIX_BYTES = DEX_FILE_PREFIX.toByteArray(StandardCharsets.US_ASCII)
private val DEX_SUFFIX_BYTES = DEX_FILE_SUFFIX.toByteArray(StandardCharsets.US_ASCII)
