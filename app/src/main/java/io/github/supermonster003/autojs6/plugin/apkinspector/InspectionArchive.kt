package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.AabManifestDisplayDecoder
import org.autojs.plugin.packagearchive.ApkManifestDisplayDecoder
import org.autojs.plugin.packagearchive.PackageInspectionLimits
import org.autojs.plugin.packagearchive.PackageDeviceSpec
import org.autojs.plugin.packagearchive.AndroidPackageFormat
import org.autojs.plugin.packagearchive.AndroidPackageSubtype
import org.autojs.plugin.packagearchive.ArchiveProblem
import org.autojs.plugin.packagearchive.ArchiveProblemCode
import org.autojs.plugin.packagearchive.ArchiveAssetEntry

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * A bounded, read-only description of an Android package file or package container.
 *
 * Container metadata is treated as a hint. Compatibility findings are based on the manifests of
 * the APK entries themselves.
 */
internal data class AndroidPackageArchive(
    val sourceFile: File,
    val format: AndroidPackageFormat,
    val subtype: AndroidPackageSubtype,
    val apkEntries: List<ArchiveApkEntry>,
    val apkEntryCount: Int = apkEntries.size,
    val selectedApks: List<ArchiveApkEntry>,
    val obbEntries: List<ArchiveAssetEntry>,
    val aabModules: List<String>,
    val baseManifest: ManifestSummary?,
    val manifestComponents: ManifestComponentSummary,
    val nativeLibraries: NativeLibrarySummary,
    val dexFiles: DexFileSummary,
    val problems: List<ArchiveProblem>,
    val containerMetadata: ContainerMetadataSummary = ContainerMetadataSummary(),
    val aabBundleConfig: AabBundleConfigSummary? = null,
    val aabModuleMetadata: List<AabModuleMetadata> = emptyList(),
    val aabModuleMetadataOmittedCount: Int = 0,
    val pageSizeReadiness: PageSizeReadinessSummary = PageSizeReadinessSummary.NOT_EVALUATED,
) {

    val baseApk: ArchiveApkEntry?
        get() = selectedApks.firstOrNull { it.manifest.splitName.isNullOrBlank() }

    val inspectionState: ArchiveInspectionState
        get() = when {
            format == AndroidPackageFormat.AAB -> ArchiveInspectionState.AAB_SOURCE
            selectedApks.isNotEmpty() && problems.none { it.blocking } && obbEntries.isNotEmpty() ->
                ArchiveInspectionState.COMPATIBLE_WITH_OBB
            selectedApks.isNotEmpty() && problems.none { it.blocking } -> ArchiveInspectionState.COMPATIBLE
            problems.any { it.code == ArchiveProblemCode.INCOMPATIBLE_DEVICE } -> ArchiveInspectionState.INCOMPATIBLE
            else -> ArchiveInspectionState.INVALID
        }

    fun decodeDisplayManifest(): String = when (format) {
        AndroidPackageFormat.AAB -> AabManifestDisplayDecoder.decode(sourceFile)
        AndroidPackageFormat.APK -> ApkManifestDisplayDecoder.decode(sourceFile)
        else -> {
            val base = baseApk ?: AndroidPackageArchiveValidator.reject(
                AndroidPackageArchiveRejection.DISPLAY_BASE_APK_MISSING,
            )
            ZipFile(sourceFile).use { zip ->
                val entry = requireDisplayBaseEntry(zip, base.archivePath)
                zip.getInputStream(entry).use(ApkManifestDisplayDecoder::decodeApk)
            }
        }
    }

    fun createDisplayApk(cacheDirectory: File): File? {
        val available = (cacheDirectory.usableSpace - MINIMUM_FREE_CACHE_BYTES).coerceAtLeast(0L)
        if (
            AndroidPackageArchiveValidator.rejectDisplayApk(
                DisplayApkFacts(
                    format = format,
                    baseApkSize = baseApk?.size,
                    availableCacheBytes = available,
                    maxDisplayApkBytes = MAX_DISPLAY_APK_BYTES,
                ),
            ) != null
        ) {
            return null
        }
        val base = baseApk ?: error("Validated display APK is missing")
        if (format == AndroidPackageFormat.APK) return sourceFile
        val directory = createDisplayDirectory(cacheDirectory)
        return try {
            val output = File(directory, "base.apk")
            when (format) {
                AndroidPackageFormat.APK -> error("Direct APK inspection does not require extraction")
                AndroidPackageFormat.AAB -> error("AAB files do not contain a directly inspectable base APK")
                else -> ZipFile(sourceFile).use { zip ->
                    val entry = requireDisplayBaseEntry(zip, base.archivePath)
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().buffered().use { target ->
                            copyDisplayApkBounded(input, target)
                        }
                    }
                }
            }
            output
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    companion object {
        internal const val MAX_DISPLAY_APK_BYTES = PackageInspectionLimits.PACKAGE_BYTES
        internal const val MINIMUM_FREE_CACHE_BYTES = 128L * 1024L * 1024L

        internal fun createDisplayDirectory(
            cacheDirectory: File,
            name: String = "package-info-${UUID.randomUUID()}",
        ): File = File(cacheDirectory, name).apply {
            if (!mkdirs()) {
                AndroidPackageArchiveValidator.reject(
                    AndroidPackageArchiveRejection.DISPLAY_DIRECTORY,
                )
            }
        }

        internal fun copyDisplayApkBounded(
            input: InputStream,
            output: java.io.OutputStream,
            maxBytes: Long = MAX_DISPLAY_APK_BYTES,
        ) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total = Math.addExact(total, read.toLong())
                if (total > maxBytes) {
                    AndroidPackageArchiveValidator.reject(
                        AndroidPackageArchiveRejection.DISPLAY_APK_SIZE,
                    )
                }
                output.write(buffer, 0, read)
            }
        }

        internal fun requireDisplayBaseEntry(zip: ZipFile, path: String): ZipEntry =
            zip.getEntry(path)
                ?.takeUnless { it.isDirectory }
                ?: AndroidPackageArchiveValidator.reject(
                    AndroidPackageArchiveRejection.DISPLAY_BASE_ENTRY_MISSING,
                )
    }
}

internal enum class ArchiveInspectionState {
    COMPATIBLE,
    COMPATIBLE_WITH_OBB,
    AAB_SOURCE,
    INCOMPATIBLE,
    INVALID,
}

internal data class ArchiveApkEntry(
    val archivePath: String,
    val size: Long,
    val manifest: ManifestSummary,
)

internal data class ManifestSummary(
    val packageName: String?,
    val splitName: String?,
    val configForSplit: String?,
    val featureSplit: Boolean,
    val splitRequired: Boolean,
    val versionCode: Long?,
    val versionName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val maxSdk: Int?,
    val applicationLabel: String?,
    val applicationIcon: String?,
    val applicationRoundIcon: String?,
    val requestedPermissions: List<String>,
    val usesSplits: List<String>,
    val declaredPermissionProtectionLevels: Map<String, Int> = emptyMap(),
    val manifestComponents: ManifestComponentSummary = ManifestComponentSummary(),
    /** `android:extractNativeLibs` of `<application>`; null when the manifest does not declare it. */
    val extractNativeLibs: Boolean? = null,
)

internal object AndroidPackageArchiveInspector {

    const val MAX_AAB_COMPONENT_MANIFESTS = 512
    const val MAX_AAB_COMPONENT_MANIFEST_TOTAL_BYTES = 64L * 1024L * 1024L
    const val MAX_AAB_BUNDLE_CONFIG_BYTES = PackageInspectionLimits.METADATA_BYTES

    internal const val MAX_ARCHIVE_ENTRIES = PackageInspectionLimits.ARCHIVE_ENTRIES
    internal const val MAX_GENERIC_APK_ENTRIES = PackageInspectionLimits.GENERIC_APKS
    internal const val MAX_ENTRY_NAME_CHARS = PackageInspectionLimits.ENTRY_NAME_CHARS
    internal const val MAX_DECLARED_ENTRY_BYTES = PackageInspectionLimits.PACKAGE_BYTES
    internal const val MAX_DECLARED_TOTAL_BYTES = PackageInspectionLimits.TOTAL_DECLARED_BYTES
    private const val MAX_NESTED_APK_SCAN_BYTES = PackageInspectionLimits.TOTAL_SCAN_BYTES
    internal const val MAX_AAB_COMPONENT_MANIFEST_BYTES = PackageInspectionLimits.MANIFEST_BYTES
    private const val MAX_AAB_METADATA_ISSUE_DETAIL_CHARS = 240
    private const val AAB_BUNDLE_CONFIG_ENTRY = "BundleConfig.pb"
    private const val AAB_MANIFEST_SUFFIX = "/manifest/AndroidManifest.xml"
    private const val AAB_BASE_MANIFEST_ENTRY = "base$AAB_MANIFEST_SUFFIX"

    /** Shared parsing/selection never prepares digests or exposes installation operations here. */
    fun inspect(file: File, device: PackageDeviceSpec): AndroidPackageArchive {
        val shared = try {
            org.autojs.plugin.packagearchive.AndroidPackageArchiveInspector.inspect(
                file, device, prepareForInstallation = false,
            )
        } catch (error: IOException) {
            val reason = AndroidPackageArchiveRejection.entries.firstOrNull { it.message == error.message }
            if (reason != null) throw AndroidPackageArchiveException(reason)
            throw error
        }
        return ZipFile(file).use { zip ->
            // The shared APK summary need not enumerate resources. Inspector's additional code
            // and resource sections still require the existing bounded, validated entry index.
            val entries = validateAndCollectEntries(zip)
            val entriesByPath = entries.associateBy(ZipEntry::getName)
            if (shared.format == AndroidPackageFormat.APK) {
                val summary = ManifestSummaryParser.parse(shared.decodeDisplayManifest())
                val entry = ArchiveApkEntry(file.name, file.length(), summary)
                val code = NativeLibraryInspector.inspectApkCodeEntries(entries, device.abis)
                return@use AndroidPackageArchive(
                    file, shared.format, shared.subtype, listOf(entry), selectedApks = listOf(entry),
                    obbEntries = shared.obbEntries, aabModules = shared.aabModules, baseManifest = summary,
                    manifestComponents = summary.manifestComponents, nativeLibraries = code.nativeLibraries,
                    dexFiles = code.dexFiles, problems = shared.problems,
                    pageSizeReadiness = PageSizeReadinessInspector.inspectApk(
                        file, zip, entries, summary.extractNativeLibs,
                    ),
                )
            }
            if (shared.format == AndroidPackageFormat.AAB) {
                val manifests = entries.filter { !it.isDirectory && isAabModuleManifestPath(it.name) }.sortedBy(ZipEntry::getName)
                val displayEntry = AndroidPackageArchiveValidator.requireAabDisplayManifest(
                    manifests.firstOrNull { it.name == AAB_BASE_MANIFEST_ENTRY } ?: manifests.firstOrNull(), ZipEntry::getSize,
                )
                val bytes = zip.getInputStream(displayEntry).use { it.readAabManifestBounded(MAX_AAB_COMPONENT_MANIFEST_BYTES) }
                val xml = AabManifestDisplayDecoder.decodeManifest(bytes)
                val summary = ManifestSummaryParser.parse(xml)
                val details = inspectAabManifests(zip, manifests, displayEntry, summary.manifestComponents, xml, bytes.size)
                val code = NativeLibraryInspector.inspectAabCodeEntries(entries, device.abis)
                return@use AndroidPackageArchive(
                    file, shared.format, shared.subtype, emptyList(), selectedApks = emptyList(), obbEntries = emptyList(),
                    aabModules = shared.aabModules, baseManifest = summary, manifestComponents = details.components,
                    nativeLibraries = code.nativeLibraries, dexFiles = code.dexFiles, problems = shared.problems,
                    aabBundleConfig = inspectAabBundleConfig(zip, entries), aabModuleMetadata = details.moduleMetadata,
                    aabModuleMetadataOmittedCount = details.omittedMetadataCount,
                    pageSizeReadiness = PageSizeReadinessInspector.inspectAab(zip, entries),
                )
            }
            val budget = InspectionBudget(MAX_NESTED_APK_SCAN_BYTES)
            val selectedPaths = shared.selectedApks.map { it.archivePath }.toSet()
            val enriched = shared.apkEntries.associate { entry ->
                val summary = if (entry.archivePath in selectedPaths) {
                    try {
                        val nested = entriesByPath.getValue(entry.archivePath)
                        val xml = zip.getInputStream(nested).use {
                            ApkManifestDisplayDecoder.decodeApk(BudgetedInputStream(it, budget))
                        }
                        ManifestSummaryParser.parse(xml)
                    } catch (_: Exception) {
                        // A detail failure cannot erase the shared package/selection result.
                        ManifestSummaryParser.fromShared(entry.manifest).copy(
                            manifestComponents = ManifestComponentSummary(failedManifestCount = 1),
                        )
                    }
                } else ManifestSummaryParser.fromShared(entry.manifest)
                entry.archivePath to ArchiveApkEntry(entry.archivePath, entry.size, summary)
            }
            val selected = shared.selectedApks.map { enriched.getValue(it.archivePath) }
            val code = NativeLibraryInspector.inspectNestedPackageCode(zip, selected, entriesByPath, device.abis)
            AndroidPackageArchive(
                file, shared.format, shared.subtype, shared.apkEntries.map { enriched.getValue(it.archivePath) },
                apkEntryCount = shared.apkEntryCount, selectedApks = selected, obbEntries = shared.obbEntries,
                aabModules = shared.aabModules, baseManifest = selected.firstOrNull { it.manifest.splitName.isNullOrBlank() }?.manifest,
                manifestComponents = ManifestComponentSummary.aggregate(selected.map { it.manifest.manifestComponents }),
                nativeLibraries = code.nativeLibraries, dexFiles = code.dexFiles, problems = shared.problems,
                containerMetadata = ContainerMetadataInspector.inspect(zip, entries, shared.subtype),
            )
        }
    }

    private fun inspectAabManifests(
        zip: ZipFile,
        manifestEntries: List<ZipEntry>,
        displayManifestEntry: ZipEntry,
        displayManifestSummary: ManifestComponentSummary,
        displayManifest: String,
        displayManifestByteCount: Int,
    ): AabManifestInspection {
        val additionalEntries = manifestEntries.asSequence()
            .filterNot { entry -> entry.name == displayManifestEntry.name }
            .take((MAX_AAB_COMPONENT_MANIFESTS - 1).coerceAtLeast(0))
            .toList()
        val omittedCount = (manifestEntries.size - 1 - additionalEntries.size).coerceAtLeast(0)
        val totalBudget = InspectionBudget(
            (MAX_AAB_COMPONENT_MANIFEST_TOTAL_BYTES - displayManifestByteCount).coerceAtLeast(0L),
        )
        val summaries = mutableListOf(displayManifestSummary)
        val moduleMetadata = mutableListOf(
            parseAabModuleMetadata(
                displayManifest,
                displayManifestEntry.name.substringBefore('/'),
            ),
        )
        var failedCount = 0
        additionalEntries.forEach { entry ->
            val moduleName = entry.name.substringBefore('/')
            try {
                if (entry.size > MAX_AAB_COMPONENT_MANIFEST_BYTES) {
                    throw IOException("AAB module manifest exceeds the component scan limit")
                }
                val bytes = zip.getInputStream(entry).use { input ->
                    BudgetedInputStream(input, totalBudget)
                        .readAabManifestBounded(MAX_AAB_COMPONENT_MANIFEST_BYTES)
                }
                val decoded = AabManifestDisplayDecoder.decodeManifest(bytes)
                try {
                    summaries += ManifestComponentSummaryParser.parse(decoded)
                } catch (_: Exception) {
                    failedCount += 1
                }
                moduleMetadata += parseAabModuleMetadata(decoded, moduleName)
            } catch (error: Exception) {
                failedCount += 1
                moduleMetadata += AabModuleMetadata.invalid(moduleName, error.message.orEmpty())
            }
        }
        return AabManifestInspection(
            components = ManifestComponentSummary.aggregate(summaries).withManifestProblems(
                failedCount = failedCount,
                omittedCount = omittedCount,
            ),
            moduleMetadata = moduleMetadata.sortedBy(AabModuleMetadata::name),
            omittedMetadataCount = omittedCount,
        )
    }

    private fun parseAabModuleMetadata(
        xml: String,
        moduleName: String,
    ): AabModuleMetadata = try {
        AabModuleMetadataParser.parse(AabModuleManifest.parse(xml), moduleName)
    } catch (error: Exception) {
        AabModuleMetadata.invalid(moduleName, error.message.orEmpty())
    }

    private fun inspectAabBundleConfig(
        zip: ZipFile,
        entries: List<ZipEntry>,
    ): AabBundleConfigSummary {
        val entry = entries.firstOrNull { candidate ->
            candidate.name == AAB_BUNDLE_CONFIG_ENTRY && !candidate.isDirectory
        } ?: return AabBundleConfigSummary.unavailable(
            AabMetadataIssue(AabMetadataIssueCode.MISSING, "BundleConfig.pb is missing"),
        )
        if (entry.size > MAX_AAB_BUNDLE_CONFIG_BYTES) {
            return unavailableAabBundleConfigLimit()
        }
        return try {
            val bytes = zip.getInputStream(entry).use { input ->
                input.readAabBundleConfigBounded(MAX_AAB_BUNDLE_CONFIG_BYTES)
            }
            AabBundleConfigDecoder.decode(bytes)
        } catch (_: AabBundleConfigLimitException) {
            unavailableAabBundleConfigLimit()
        } catch (error: Exception) {
            AabBundleConfigSummary.unavailable(
                AabMetadataIssue(
                    AabMetadataIssueCode.INVALID,
                    error.message.orEmpty().take(MAX_AAB_METADATA_ISSUE_DETAIL_CHARS),
                ),
            )
        }
    }

    private fun unavailableAabBundleConfigLimit(): AabBundleConfigSummary =
        AabBundleConfigSummary.unavailable(
            AabMetadataIssue(
                AabMetadataIssueCode.LIMIT,
                "BundleConfig.pb exceeds the inspection size limit",
            ),
        )

    private fun isAabModuleManifestPath(path: String): Boolean {
        if (!path.endsWith(AAB_MANIFEST_SUFFIX)) return false
        val moduleName = path.removeSuffix(AAB_MANIFEST_SUFFIX)
        return moduleName.isNotEmpty() && '/' !in moduleName && '\\' !in moduleName
    }

    private fun InputStream.readAabManifestBounded(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read > maxBytes - total) {
                throw IOException("AAB module manifest exceeds the component scan limit")
            }
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun InputStream.readAabBundleConfigBounded(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read > maxBytes - total) {
                throw AabBundleConfigLimitException("BundleConfig.pb exceeds the inspection size limit")
            }
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun validateAndCollectEntries(zip: ZipFile): List<ZipEntry> {
        val result = ArrayList<ZipEntry>()
        val validation = ArchiveEntryValidationState(
            ArchiveEntryValidationLimits(
                maxEntries = MAX_ARCHIVE_ENTRIES,
                maxNameCharacters = MAX_ENTRY_NAME_CHARS,
                maxEntryBytes = MAX_DECLARED_ENTRY_BYTES,
                maxTotalBytes = MAX_DECLARED_TOTAL_BYTES,
            ),
        )
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            validation.accept(entry.name, entry.size)
            result += entry
        }
        return result
    }

    private class InspectionBudget(
        private var remaining: Long,
    ) {
        fun consume(byteCount: Long) {
            if (byteCount <= 0L) return
            remaining -= byteCount
            if (remaining < 0L) {
                throw IOException("Nested APK manifest scans exceed the inspection limit")
            }
        }
    }

    private class BudgetedInputStream(
        input: InputStream,
        private val budget: InspectionBudget,
    ) : FilterInputStream(input) {

        override fun read(): Int = super.read().also { value ->
            if (value >= 0) budget.consume(1L)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { read ->
                if (read > 0) budget.consume(read.toLong())
            }

        override fun skip(byteCount: Long): Long =
            super.skip(byteCount).also(budget::consume)
    }

    private data class AabManifestInspection(
        val components: ManifestComponentSummary,
        val moduleMetadata: List<AabModuleMetadata>,
        val omittedMetadataCount: Int,
    )
}

internal object ManifestSummaryParser {

    private val manifestTag = Regex("""<manifest\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val applicationTag = Regex("""<application\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val declaredPermissionTag =
        Regex("""<permission(?=\s|/?>)([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""(?:^|\s)([A-Za-z_][A-Za-z0-9_.-]*(?::[A-Za-z_][A-Za-z0-9_.-]*)?)\s*=\s*("([^"]*)"|'([^']*)')""")

    fun parse(xml: String): ManifestSummary {
        val shared = try {
            org.autojs.plugin.packagearchive.ManifestSummaryParser.parse(xml)
        } catch (error: IOException) {
            if (!manifestTag.containsMatchIn(xml)) {
                AndroidPackageArchiveValidator.reject(AndroidPackageArchiveRejection.MANIFEST_ROOT_MISSING)
            }
            throw error
        }
        val applicationAttributes = applicationTag.find(xml)?.groupValues?.get(1)?.let(::parseAttributes).orEmpty()
        return fromShared(shared).copy(
            applicationIcon = applicationAttributes.value("icon")?.takeIf(String::isNotBlank),
            applicationRoundIcon = applicationAttributes.value("roundIcon")?.takeIf(String::isNotBlank),
            extractNativeLibs = applicationAttributes.value("extractNativeLibs")?.toBooleanFlexible(),
            declaredPermissionProtectionLevels = declaredPermissionTag.findAll(xml)
                .take(MAX_DECLARED_PERMISSIONS)
                .mapNotNull { match ->
                    val attributes = parseAttributes(match.groupValues[1])
                    val name = attributes.value("name")?.takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val protectionLevel = attributes.value("protectionLevel")
                        ?.toProtectionLevelFlexible()
                        ?: if (attributes.value("protectionLevel") == null) 0 else return@mapNotNull null
                    name to protectionLevel
                }
                .sortedBy { (name) -> name }
                .toMap(),
            manifestComponents = try {
                ManifestComponentSummaryParser.parse(xml)
            } catch (_: Exception) {
                ManifestComponentSummary(failedManifestCount = 1)
            },
        )
    }

    fun fromShared(value: org.autojs.plugin.packagearchive.ManifestSummary): ManifestSummary = ManifestSummary(
        packageName = value.packageName, splitName = value.splitName, configForSplit = value.configForSplit,
        featureSplit = value.featureSplit, splitRequired = value.splitRequired, versionCode = value.versionCode,
        versionName = value.versionName, minSdk = value.minSdk, targetSdk = value.targetSdk, maxSdk = value.maxSdk,
        applicationLabel = value.applicationLabel, applicationIcon = null, applicationRoundIcon = null,
        requestedPermissions = value.requestedPermissions, usesSplits = value.usesSplits,
    )

    private fun parseAttributes(source: String): Map<String, String> = buildMap {
        attribute.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
            put(name, decodeXmlEntities(value))
        }
    }

    private fun Map<String, String>.value(localName: String): String? =
        entries.firstOrNull { (name) -> name.substringAfter(':') == localName }?.value

    private fun String?.toBooleanFlexible(): Boolean =
        this.equals("true", true) || this == "1" || this.equals("0xffffffff", true)

    private fun String.toIntFlexible(): Int? = toLongFlexible()?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()

    private fun String.toProtectionLevelFlexible(): Int? {
        toIntFlexible()?.let { return it }
        return split('|').asSequence()
            .map { token -> token.trim().lowercase(Locale.ROOT).replace("_", "") }
            .mapNotNull { token ->
                when (token) {
                    "normal" -> 0
                    "dangerous" -> 1
                    "signature" -> 2
                    "signatureorsystem" -> 3
                    "internal" -> 4
                    else -> null
                }
            }
            .distinct()
            .singleOrNull()
    }

    private fun String.toLongFlexible(): Long? {
        val value = trim()
        return when {
            value.startsWith("0x", true) -> value.substring(2).toLongOrNull(16)
            else -> value.toLongOrNull()
        }
    }

    private fun decodeXmlEntities(value: String): String =
        ENTITY.replace(value) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val codePoint = when {
                        entity.startsWith("#x", true) -> entity.substring(2).toIntOrNull(16)
                        entity.startsWith('#') -> entity.substring(1).toIntOrNull()
                        else -> null
                    }
                    codePoint?.takeIf(Character::isValidCodePoint)
                        ?.let(Character::toChars)
                        ?.concatToString()
                        ?: match.value
                }
            }
        }

    private val ENTITY = Regex("""&(#x?[0-9A-Fa-f]+|amp|lt|gt|quot|apos);""")
    private const val MAX_DECLARED_PERMISSIONS = 2_048
}
