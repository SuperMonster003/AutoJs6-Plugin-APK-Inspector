package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.AndroidPackageFormat

import java.io.IOException

internal enum class AndroidPackageArchiveRejection(
    val message: String,
) {
    PACKAGE_FILE_MISSING("Package file does not exist"),
    ARCHIVE_ENTRY_COUNT("Archive entry count exceeds the inspection limit"),
    ARCHIVE_ENTRY_NAME("Archive contains an unsafe entry name"),
    ARCHIVE_DUPLICATE_ENTRY("Archive contains duplicate entry names"),
    ARCHIVE_ENTRY_SIZE("Archive entry exceeds the inspection size limit"),
    ARCHIVE_TOTAL_SIZE("Archive contents exceed the inspection size limit"),
    AAB_MANIFEST_MISSING("AAB contains no module AndroidManifest.xml"),
    AAB_DISPLAY_MANIFEST_SIZE("AAB display manifest exceeds the inspection limit"),
    GENERIC_APK_ENTRY_COUNT("APK entry count exceeds the inspection limit"),
    UNSUPPORTED_CONTAINER("Unsupported Android package container"),
    DISPLAY_BASE_APK_MISSING("A base APK is unavailable"),
    DISPLAY_AAB_UNAVAILABLE("AAB files do not contain a directly inspectable base APK"),
    DISPLAY_APK_SIZE("Base APK exceeds the display extraction limit"),
    DISPLAY_CACHE_SPACE("Insufficient cache space for the display APK"),
    DISPLAY_DIRECTORY("Unable to create the package information directory"),
    DISPLAY_BASE_ENTRY_MISSING("Base APK entry is missing"),
    MANIFEST_ROOT_MISSING("Android manifest root element is missing"),
}

internal class AndroidPackageArchiveException(
    val rejection: AndroidPackageArchiveRejection,
) : IOException(rejection.message)

internal data class ArchiveEntryValidationLimits(
    val maxEntries: Int,
    val maxNameCharacters: Int,
    val maxEntryBytes: Long,
    val maxTotalBytes: Long,
)

internal class ArchiveEntryValidationState(
    private val limits: ArchiveEntryValidationLimits,
) {

    private val entryNames = HashSet<String>()
    private var entryCount = 0
    private var declaredTotal = 0L

    fun accept(name: String, declaredSize: Long) {
        if (entryCount >= limits.maxEntries) {
            AndroidPackageArchiveValidator.reject(AndroidPackageArchiveRejection.ARCHIVE_ENTRY_COUNT)
        }
        if (!isSafeEntryName(name, limits.maxNameCharacters)) {
            AndroidPackageArchiveValidator.reject(AndroidPackageArchiveRejection.ARCHIVE_ENTRY_NAME)
        }
        if (!entryNames.add(name)) {
            AndroidPackageArchiveValidator.reject(AndroidPackageArchiveRejection.ARCHIVE_DUPLICATE_ENTRY)
        }
        if (declaredSize > limits.maxEntryBytes) {
            AndroidPackageArchiveValidator.reject(AndroidPackageArchiveRejection.ARCHIVE_ENTRY_SIZE)
        }
        if (declaredSize > 0L) {
            val nextTotal = try {
                Math.addExact(declaredTotal, declaredSize)
            } catch (_: ArithmeticException) {
                AndroidPackageArchiveValidator.reject(AndroidPackageArchiveRejection.ARCHIVE_TOTAL_SIZE)
            }
            if (nextTotal > limits.maxTotalBytes) {
                AndroidPackageArchiveValidator.reject(AndroidPackageArchiveRejection.ARCHIVE_TOTAL_SIZE)
            }
            declaredTotal = nextTotal
        }
        entryCount += 1
    }

    companion object {
        internal fun isSafeEntryName(name: String, maxCharacters: Int): Boolean =
            name.isNotBlank() &&
                name.length <= maxCharacters &&
                '\u0000' !in name &&
                '\\' !in name &&
                !name.startsWith('/') &&
                !Regex("^[A-Za-z]:").containsMatchIn(name) &&
                name.split('/').none { segment -> segment == ".." }
    }
}

internal data class DisplayApkFacts(
    val format: AndroidPackageFormat,
    val baseApkSize: Long?,
    val availableCacheBytes: Long,
    val maxDisplayApkBytes: Long,
)

internal object AndroidPackageArchiveValidator {

    fun rejectDisplayApk(facts: DisplayApkFacts): AndroidPackageArchiveRejection? {
        val baseSize = facts.baseApkSize
            ?: return AndroidPackageArchiveRejection.DISPLAY_BASE_APK_MISSING
        if (facts.format == AndroidPackageFormat.AAB) {
            return AndroidPackageArchiveRejection.DISPLAY_AAB_UNAVAILABLE
        }
        if (facts.format == AndroidPackageFormat.APK) return null
        if (baseSize > facts.maxDisplayApkBytes) {
            return AndroidPackageArchiveRejection.DISPLAY_APK_SIZE
        }
        if (baseSize > facts.availableCacheBytes.coerceAtLeast(0L)) {
            return AndroidPackageArchiveRejection.DISPLAY_CACHE_SPACE
        }
        return null
    }

    fun <T> requireAabDisplayManifest(
        candidate: T?,
        declaredSize: (T) -> Long,
    ): T {
        val entry = candidate ?: reject(AndroidPackageArchiveRejection.AAB_MANIFEST_MISSING)
        if (declaredSize(entry) > AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFEST_BYTES) {
            reject(AndroidPackageArchiveRejection.AAB_DISPLAY_MANIFEST_SIZE)
        }
        return entry
    }

    fun requireGenericApkEntryCount(isBundletool: Boolean, count: Int) {
        if (!isBundletool && count > AndroidPackageArchiveInspector.MAX_GENERIC_APK_ENTRIES) {
            reject(AndroidPackageArchiveRejection.GENERIC_APK_ENTRY_COUNT)
        }
    }

    fun reject(reason: AndroidPackageArchiveRejection): Nothing =
        throw AndroidPackageArchiveException(reason)
}
