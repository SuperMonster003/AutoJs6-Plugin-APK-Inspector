package io.github.supermonster003.autojs6.plugin.apkinspector

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class ContainerMetadataSummary(
    val sourceEntry: String? = null,
    val packager: ContainerMetadataPackager? = null,
    val packagerVersion: String? = null,
    val packageName: String? = null,
    val declaredVersionName: String? = null,
    val declaredVersionCode: String? = null,
    val iconEntry: String? = null,
    val issue: ContainerMetadataIssue? = null,
)

internal enum class ContainerMetadataPackager(
    val displayName: String,
) {
    SAI("SAI"),
    XAPK("XAPK"),
    APK_MIRROR("APKMirror"),
    APKS("APKS"),
    APKZ("APKZ"),
}

internal enum class ContainerMetadataIssue {
    METADATA_LIMIT,
    METADATA_INVALID,
}

/**
 * Reads optional package-container metadata as an untrusted hint. APK manifests remain authoritative.
 */
internal object ContainerMetadataInspector {

    const val MAX_METADATA_BYTES = PackageInspectionLimits.METADATA_BYTES

    private const val MAX_PACKAGE_NAME_CHARS = 255
    private const val MAX_VERSION_CHARS = 256
    private const val MAX_PACKAGER_VERSION_CHARS = 64
    private const val MAX_ICON_PATH_CHARS = PackageInspectionLimits.ENTRY_NAME_CHARS
    private const val MAX_JSON_SCALAR_CHARS = MAX_ICON_PATH_CHARS

    fun inspect(
        zip: ZipFile,
        entries: List<ZipEntry>,
        subtype: AndroidPackageSubtype,
    ): ContainerMetadataSummary {
        val (candidate, entry) = candidatesFor(subtype).firstNotNullOfOrNull { candidate ->
            findEntry(entries, candidate.entryName)?.let { entry -> candidate to entry }
        } ?: return ContainerMetadataSummary()

        val initial = ContainerMetadataSummary(
            sourceEntry = entry.name,
            packager = candidate.fallbackPackager,
            packagerVersion = candidate.fallbackVersion,
        )
        if (entry.size > MAX_METADATA_BYTES) {
            return initial.copy(issue = ContainerMetadataIssue.METADATA_LIMIT)
        }

        val bytes = try {
            zip.getInputStream(entry).use { input -> input.readBounded(MAX_METADATA_BYTES) }
        } catch (_: MetadataLimitException) {
            return initial.copy(issue = ContainerMetadataIssue.METADATA_LIMIT)
        } catch (_: Exception) {
            return initial.copy(issue = ContainerMetadataIssue.METADATA_INVALID)
        }
        val fields = try {
            parseRootFields(bytes)
        } catch (_: Exception) {
            return initial.copy(issue = ContainerMetadataIssue.METADATA_INVALID)
        }

        val packager = if (candidate.fallbackPackager == ContainerMetadataPackager.APKS) {
            when {
                fields.value("apkm_version") != null -> ContainerMetadataPackager.APK_MIRROR
                fields.value("xapk_version") != null -> ContainerMetadataPackager.XAPK
                fields.value("meta_version") != null -> ContainerMetadataPackager.SAI
                fields.value("apkz_version") != null -> ContainerMetadataPackager.APKZ
                else -> ContainerMetadataPackager.APKS
            }
        } else {
            candidate.fallbackPackager
        }
        val packagerVersion = when (packager) {
            ContainerMetadataPackager.SAI -> fields.value("meta_version")
            ContainerMetadataPackager.XAPK -> fields.value("xapk_version")
            ContainerMetadataPackager.APK_MIRROR -> fields.value("apkm_version")
            ContainerMetadataPackager.APKZ -> fields.value("apkz_version")
            ContainerMetadataPackager.APKS -> null
        }.bounded(MAX_PACKAGER_VERSION_CHARS) ?: candidate.fallbackVersion
        val declaredIcon = fields.value("icon", "icon_path", "icon_file")
            .bounded(MAX_ICON_PATH_CHARS)
        val iconEntry = declaredIcon
            ?.let { path -> findEntry(entries, path) }
            ?: findEntry(entries, CONVENTIONAL_ICON_ENTRY)

        return initial.copy(
            packager = packager,
            packagerVersion = packagerVersion,
            packageName = fields.value(
                "package_name",
                "packageName",
                "package",
                "pname",
            ).bounded(MAX_PACKAGE_NAME_CHARS),
            declaredVersionName = fields.value(
                "version_name",
                "versionName",
                "release_version",
                "version",
            ).bounded(MAX_VERSION_CHARS),
            declaredVersionCode = fields.value(
                "version_code",
                "versionCode",
                "versioncode",
            ).bounded(MAX_VERSION_CHARS),
            iconEntry = iconEntry?.name,
        )
    }

    private fun candidatesFor(subtype: AndroidPackageSubtype): List<MetadataCandidate> = when (subtype) {
        AndroidPackageSubtype.SINGLE_APK,
        AndroidPackageSubtype.ANDROID_APP_BUNDLE,
        -> emptyList()

        AndroidPackageSubtype.SAI_APKS -> listOf(
            SAI_V2_CANDIDATE,
            SAI_V1_CANDIDATE,
            MetadataCandidate("info.json", ContainerMetadataPackager.APKS),
            XAPK_CANDIDATE,
        )

        AndroidPackageSubtype.XAPK -> listOf(
            XAPK_CANDIDATE,
            MetadataCandidate("info.json", ContainerMetadataPackager.XAPK),
            SAI_V2_CANDIDATE,
            SAI_V1_CANDIDATE,
        )

        AndroidPackageSubtype.APKM -> listOf(
            APK_MIRROR_CANDIDATE,
            XAPK_CANDIDATE,
            SAI_V2_CANDIDATE,
            SAI_V1_CANDIDATE,
        )

        AndroidPackageSubtype.APKZ -> listOf(
            APKZ_CANDIDATE,
            APK_MIRROR_CANDIDATE,
        )

        AndroidPackageSubtype.BUNDLETOOL_APKS,
        AndroidPackageSubtype.GENERIC_APKS,
        -> listOf(
            SAI_V2_CANDIDATE,
            SAI_V1_CANDIDATE,
            MetadataCandidate("info.json", ContainerMetadataPackager.APKS),
            XAPK_CANDIDATE,
        )
    }

    private fun parseRootFields(bytes: ByteArray): Map<String, String> = buildMap {
        JsonReader(bytes.inputStream().reader(Charsets.UTF_8)).use { reader ->
            reader.strictness = Strictness.STRICT
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw IOException("Container metadata root is not a JSON object")
            }
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name in SUPPORTED_FIELDS && name !in this) {
                    reader.readBoundedScalar()?.let { value -> put(name, value) }
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw IOException("Container metadata has trailing content")
            }
        }
    }

    private fun JsonReader.readBoundedScalar(): String? {
        val value = when (peek()) {
            JsonToken.STRING,
            JsonToken.NUMBER,
            -> nextString()

            JsonToken.NULL -> {
                nextNull()
                return null
            }

            else -> {
                skipValue()
                return null
            }
        }
        return value.trim().takeIf { candidate ->
            candidate.isNotEmpty() &&
                candidate.length <= MAX_JSON_SCALAR_CHARS &&
                candidate.none(Character::isISOControl)
        }
    }

    private fun Map<String, String>.value(vararg keys: String): String? =
        keys.firstNotNullOfOrNull(::get)

    private fun String?.bounded(maxChars: Int): String? =
        this?.takeIf { value -> value.length <= maxChars }

    private fun findEntry(entries: List<ZipEntry>, requestedName: String): ZipEntry? {
        entries.firstOrNull { entry -> !entry.isDirectory && entry.name == requestedName }?.let { return it }
        return entries.asSequence()
            .filter { entry -> !entry.isDirectory && entry.name.equals(requestedName, ignoreCase = true) }
            .take(2)
            .toList()
            .singleOrNull()
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total = Math.addExact(total, read)
            if (total > maxBytes) throw MetadataLimitException()
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private data class MetadataCandidate(
        val entryName: String,
        val fallbackPackager: ContainerMetadataPackager,
        val fallbackVersion: String? = null,
    )

    private class MetadataLimitException : IOException()

    private val SAI_V2_CANDIDATE = MetadataCandidate(
        "meta.sai_v2.json",
        ContainerMetadataPackager.SAI,
        "2",
    )
    private val SAI_V1_CANDIDATE = MetadataCandidate(
        "meta.sai_v1.json",
        ContainerMetadataPackager.SAI,
        "1",
    )
    private val XAPK_CANDIDATE = MetadataCandidate(
        "manifest.json",
        ContainerMetadataPackager.XAPK,
    )
    private val APK_MIRROR_CANDIDATE = MetadataCandidate(
        "info.json",
        ContainerMetadataPackager.APK_MIRROR,
    )
    private val APKZ_CANDIDATE = MetadataCandidate(
        "apkz.json",
        ContainerMetadataPackager.APKZ,
    )
    private const val CONVENTIONAL_ICON_ENTRY = "icon.png"

    private val SUPPORTED_FIELDS = setOf(
        "package_name",
        "packageName",
        "package",
        "pname",
        "version_name",
        "versionName",
        "release_version",
        "version",
        "version_code",
        "versionCode",
        "versioncode",
        "meta_version",
        "xapk_version",
        "apkm_version",
        "apkz_version",
        "icon",
        "icon_path",
        "icon_file",
    )
}
