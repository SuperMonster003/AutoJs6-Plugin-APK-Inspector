package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.abs

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

internal enum class AndroidPackageFormat {
    APK,
    APKS,
    XAPK,
    APKM,
    APKZ,
    AAB,
}

internal enum class AndroidPackageSubtype {
    SINGLE_APK,
    BUNDLETOOL_APKS,
    SAI_APKS,
    GENERIC_APKS,
    XAPK,
    APKM,
    APKZ,
    ANDROID_APP_BUNDLE,
}

internal enum class ArchiveInspectionState {
    COMPATIBLE,
    COMPATIBLE_WITH_OBB,
    AAB_SOURCE,
    INCOMPATIBLE,
    INVALID,
}

internal enum class ArchiveProblemCode {
    INVALID_ARCHIVE,
    NO_BASE_APK,
    AMBIGUOUS_BASE_APK,
    INCOMPATIBLE_DEVICE,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    DUPLICATE_SPLIT,
    MISSING_SPLIT_DEPENDENCY,
    MALFORMED_APK,
}

internal data class ArchiveProblem(
    val code: ArchiveProblemCode,
    val detail: String,
    val blocking: Boolean = true,
)

internal data class ArchiveAssetEntry(
    val archivePath: String,
    val size: Long,
)

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

internal data class PackageDeviceSpec(
    val sdk: Int,
    val abis: List<String>,
    val densityDpi: Int,
    val locales: List<String>,
) {
    companion object {
        fun from(context: Context): PackageDeviceSpec {
            val configuration = context.resources.configuration
            val locales = (0 until configuration.locales.size()).map {
                configuration.locales[it].toLanguageTag()
            }
            return PackageDeviceSpec(
                sdk = Build.VERSION.SDK_INT,
                abis = Build.SUPPORTED_ABIS.toList(),
                densityDpi = context.resources.displayMetrics.densityDpi,
                locales = locales,
            )
        }
    }
}

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

    fun inspect(
        file: File,
        device: PackageDeviceSpec,
    ): AndroidPackageArchive {
        if (!file.isFile) {
            AndroidPackageArchiveValidator.reject(
                AndroidPackageArchiveRejection.PACKAGE_FILE_MISSING,
            )
        }
        return ZipFile(file).use { zip ->
            val entries = validateAndCollectEntries(zip)
            val rootManifest = entries.firstOrNull { it.name == "AndroidManifest.xml" && !it.isDirectory }
            if (rootManifest != null) {
                val xml = ApkManifestDisplayDecoder.decode(file)
                val summary = ManifestSummaryParser.parse(xml)
                val entry = ArchiveApkEntry(
                    archivePath = file.name,
                    size = file.length(),
                    manifest = summary,
                )
                val codeSummary = NativeLibraryInspector.inspectApkCodeEntries(
                    entries = entries,
                    deviceAbis = device.abis,
                )
                return AndroidPackageArchive(
                    sourceFile = file,
                    format = AndroidPackageFormat.APK,
                    subtype = AndroidPackageSubtype.SINGLE_APK,
                    apkEntries = listOf(entry),
                    selectedApks = listOf(entry),
                    obbEntries = emptyList(),
                    aabModules = emptyList(),
                    baseManifest = summary,
                    manifestComponents = summary.manifestComponents,
                    nativeLibraries = codeSummary.nativeLibraries,
                    dexFiles = codeSummary.dexFiles,
                    problems = validateSelected(listOf(entry), device),
                    pageSizeReadiness = PageSizeReadinessInspector.inspectApk(
                        file = file,
                        zip = zip,
                        entries = entries,
                        extractNativeLibs = summary.extractNativeLibs,
                    ),
                )
            }

            val entryNames = entries.asSequence().map { it.name }.toSet()
            val extension = file.extension.lowercase(Locale.ROOT)
            val format = detectContainerFormat(extension, entryNames)
            if (format == AndroidPackageFormat.AAB) {
                val moduleManifestEntries = entries.asSequence()
                    .filter { entry ->
                        !entry.isDirectory && isAabModuleManifestPath(entry.name)
                    }
                    .sortedBy(ZipEntry::getName)
                    .toList()
                val displayManifestEntry = AndroidPackageArchiveValidator.requireAabDisplayManifest(
                    moduleManifestEntries.firstOrNull { entry -> entry.name == AAB_BASE_MANIFEST_ENTRY }
                        ?: moduleManifestEntries.firstOrNull(),
                    ZipEntry::getSize,
                )
                val displayManifestBytes = zip.getInputStream(displayManifestEntry).use { input ->
                    input.readAabManifestBounded(MAX_AAB_COMPONENT_MANIFEST_BYTES)
                }
                val displayManifest = AabManifestDisplayDecoder.decodeManifestDocument(
                    displayManifestBytes,
                )
                val summary = ManifestSummaryParser.parse(displayManifest.xml)
                val modules = moduleManifestEntries.asSequence()
                    .map { entry -> entry.name.substringBefore('/') }
                    .distinct()
                    .sorted()
                    .toList()
                val manifestInspection = inspectAabManifests(
                    zip = zip,
                    manifestEntries = moduleManifestEntries,
                    displayManifestEntry = displayManifestEntry,
                    displayManifestSummary = summary.manifestComponents,
                    displayManifest = displayManifest,
                    displayManifestByteCount = displayManifestBytes.size,
                )
                val codeSummary = NativeLibraryInspector.inspectAabCodeEntries(
                    entries = entries,
                    deviceAbis = device.abis,
                )
                return AndroidPackageArchive(
                    sourceFile = file,
                    format = format,
                    subtype = AndroidPackageSubtype.ANDROID_APP_BUNDLE,
                    apkEntries = emptyList(),
                    selectedApks = emptyList(),
                    obbEntries = emptyList(),
                    aabModules = modules,
                    baseManifest = summary,
                    manifestComponents = manifestInspection.components,
                    nativeLibraries = codeSummary.nativeLibraries,
                    dexFiles = codeSummary.dexFiles,
                    problems = emptyList(),
                    aabBundleConfig = inspectAabBundleConfig(zip, entries),
                    aabModuleMetadata = manifestInspection.moduleMetadata,
                    aabModuleMetadataOmittedCount = manifestInspection.omittedMetadataCount,
                    pageSizeReadiness = PageSizeReadinessInspector.inspectAab(zip = zip, entries = entries),
                )
            }

            val subtype = detectSubtype(format, entryNames)
            val containerMetadata = ContainerMetadataInspector.inspect(
                zip = zip,
                entries = entries,
                subtype = subtype,
            )
            val apkZipEntries = entries.filter {
                !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true)
            }
            AndroidPackageArchiveValidator.requireGenericApkEntryCount(
                isBundletool = subtype == AndroidPackageSubtype.BUNDLETOOL_APKS,
                count = apkZipEntries.size,
            )

            val problems = mutableListOf<ArchiveProblem>()
            val tocSelection = if (subtype == AndroidPackageSubtype.BUNDLETOOL_APKS) {
                try {
                    BundletoolTocDecoder.select(file, device)
                } catch (error: Exception) {
                    problems += ArchiveProblem(
                        ArchiveProblemCode.INVALID_ARCHIVE,
                        "Unable to select APKs from toc.pb: ${error.message.orEmpty()}",
                    )
                    null
                }
            } else {
                null
            }
            val entriesByPath = entries.associateBy(ZipEntry::getName)
            val entriesToInspect = if (subtype == AndroidPackageSubtype.BUNDLETOOL_APKS) {
                tocSelection
                    ?.apkPaths
                    .orEmpty()
                    .distinct()
                    .mapNotNull(entriesByPath::get)
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
            } else {
                apkZipEntries
            }
            val scanBudget = InspectionBudget(MAX_NESTED_APK_SCAN_BYTES)
            val apkEntries = entriesToInspect.mapNotNull { entry ->
                try {
                    if (entry.size > MAX_DECLARED_ENTRY_BYTES) {
                        throw IOException("Nested APK exceeds the inspection limit")
                    }
                    val xml = zip.getInputStream(entry).use { input ->
                        ApkManifestDisplayDecoder.decodeApk(BudgetedInputStream(input, scanBudget))
                    }
                    ArchiveApkEntry(entry.name, entry.size.coerceAtLeast(0L), ManifestSummaryParser.parse(xml))
                } catch (error: Exception) {
                    problems += ArchiveProblem(
                        code = ArchiveProblemCode.MALFORMED_APK,
                        detail = "${entry.name}: ${error.message.orEmpty()}",
                        blocking = false,
                    )
                    null
                }
            }
            val obbEntries = entries.asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".obb", ignoreCase = true) }
                .map { ArchiveAssetEntry(it.name, it.size.coerceAtLeast(0L)) }
                .toList()

            val selection = when (subtype) {
                AndroidPackageSubtype.BUNDLETOOL_APKS ->
                    tocSelection?.let {
                        selectBundletoolApks(
                            tocSelection = it,
                            apks = apkEntries,
                            archiveEntriesByPath = entriesByPath,
                        )
                    } ?: Selection(emptyList(), emptyList())
                else -> selectApks(apkEntries, device, containerMetadata.packageName)
            }
            problems += selection.problems
            val selectedApks = selection.entries
            problems += validateSelected(selectedApks, device)
            val codeSummary = NativeLibraryInspector.inspectNestedPackageCode(
                zip = zip,
                selectedApks = selectedApks,
                entriesByPath = entriesByPath,
                deviceAbis = device.abis,
            )

            AndroidPackageArchive(
                sourceFile = file,
                format = format,
                subtype = subtype,
                apkEntries = apkEntries,
                apkEntryCount = apkZipEntries.size,
                selectedApks = selectedApks,
                obbEntries = obbEntries,
                aabModules = emptyList(),
                baseManifest = selectedApks.firstOrNull { it.manifest.splitName.isNullOrBlank() }?.manifest,
                manifestComponents = ManifestComponentSummary.aggregate(
                    selectedApks.map { apk -> apk.manifest.manifestComponents },
                ),
                nativeLibraries = codeSummary.nativeLibraries,
                dexFiles = codeSummary.dexFiles,
                problems = problems.distinctBy { Triple(it.code, it.detail, it.blocking) },
                containerMetadata = containerMetadata,
            )
        }
    }

    private fun inspectAabManifests(
        zip: ZipFile,
        manifestEntries: List<ZipEntry>,
        displayManifestEntry: ZipEntry,
        displayManifestSummary: ManifestComponentSummary,
        displayManifest: AabManifestDisplayDecoder.DecodedManifest,
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
                displayManifest.root,
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
                val decoded = AabManifestDisplayDecoder.decodeManifestDocument(bytes)
                try {
                    summaries += ManifestComponentSummaryParser.parse(decoded.xml)
                } catch (_: Exception) {
                    failedCount += 1
                }
                moduleMetadata += parseAabModuleMetadata(decoded.root, moduleName)
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
        root: AabManifestDisplayDecoder.XmlElement,
        moduleName: String,
    ): AabModuleMetadata = try {
        AabModuleMetadataParser.parse(root, moduleName)
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

    private fun detectContainerFormat(extension: String, entryNames: Set<String>): AndroidPackageFormat {
        return when {
            "BundleConfig.pb" in entryNames -> AndroidPackageFormat.AAB
            "toc.pb" in entryNames ||
                "meta.sai_v1.json" in entryNames ||
                "meta.sai_v2.json" in entryNames -> AndroidPackageFormat.APKS
            "apkz.json" in entryNames -> AndroidPackageFormat.APKZ
            entryNames.any { it.startsWith("META-INF/APKMIRRO", true) } -> AndroidPackageFormat.APKM
            "manifest.json" in entryNames -> AndroidPackageFormat.XAPK
            extension == "aab" -> AndroidPackageFormat.AAB
            extension == "apks" -> AndroidPackageFormat.APKS
            extension == "xapk" -> AndroidPackageFormat.XAPK
            extension == "apkm" -> AndroidPackageFormat.APKM
            extension == "apkz" -> AndroidPackageFormat.APKZ
            else -> AndroidPackageArchiveValidator.reject(
                AndroidPackageArchiveRejection.UNSUPPORTED_CONTAINER,
            )
        }
    }

    private fun detectSubtype(
        format: AndroidPackageFormat,
        entryNames: Set<String>,
    ): AndroidPackageSubtype = when (format) {
        AndroidPackageFormat.APK -> AndroidPackageSubtype.SINGLE_APK
        AndroidPackageFormat.APKS -> when {
            "toc.pb" in entryNames -> AndroidPackageSubtype.BUNDLETOOL_APKS
            "meta.sai_v1.json" in entryNames || "meta.sai_v2.json" in entryNames -> AndroidPackageSubtype.SAI_APKS
            else -> AndroidPackageSubtype.GENERIC_APKS
        }
        AndroidPackageFormat.XAPK -> AndroidPackageSubtype.XAPK
        AndroidPackageFormat.APKM -> AndroidPackageSubtype.APKM
        AndroidPackageFormat.APKZ -> AndroidPackageSubtype.APKZ
        AndroidPackageFormat.AAB -> AndroidPackageSubtype.ANDROID_APP_BUNDLE
    }

    private data class Selection(
        val entries: List<ArchiveApkEntry>,
        val problems: List<ArchiveProblem>,
    )

    private data class AabManifestInspection(
        val components: ManifestComponentSummary,
        val moduleMetadata: List<AabModuleMetadata>,
        val omittedMetadataCount: Int,
    )

    private fun selectBundletoolApks(
        tocSelection: BundletoolTocDecoder.Selection,
        apks: List<ArchiveApkEntry>,
        archiveEntriesByPath: Map<String, ZipEntry>,
    ): Selection {
        val readableApksByPath = apks.associateBy(ArchiveApkEntry::archivePath)
        val problems = mutableListOf<ArchiveProblem>()
        val selected = tocSelection.apkPaths.mapNotNull { path ->
            readableApksByPath[path] ?: run {
                val archiveEntry = archiveEntriesByPath[path]
                val detail = when {
                    archiveEntry == null || archiveEntry.isDirectory ->
                        "toc.pb selected a missing component: $path"
                    !path.endsWith(".apk", ignoreCase = true) ->
                        "toc.pb selected a component that is not an APK: $path"
                    else -> "toc.pb selected an unreadable APK: $path"
                }
                problems += ArchiveProblem(
                    if (archiveEntry == null || archiveEntry.isDirectory) {
                        ArchiveProblemCode.INVALID_ARCHIVE
                    } else {
                        ArchiveProblemCode.MALFORMED_APK
                    },
                    detail,
                )
                null
            }
        }
        tocSelection.packageName?.let { expected ->
            selected.filter { it.manifest.packageName != expected }.forEach { apk ->
                problems += ArchiveProblem(
                    ArchiveProblemCode.PACKAGE_MISMATCH,
                    "toc.pb package mismatch: ${apk.archivePath}",
                )
            }
        }
        return Selection(selected, problems)
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

    private fun selectApks(
        apks: List<ArchiveApkEntry>,
        device: PackageDeviceSpec,
        packageHint: String?,
    ): Selection {
        val problems = mutableListOf<ArchiveProblem>()
        if (apks.isEmpty()) {
            return Selection(
                emptyList(),
                listOf(ArchiveProblem(ArchiveProblemCode.NO_BASE_APK, "No readable APK entries were found")),
            )
        }
        var bases = apks.filter { it.manifest.splitName.isNullOrBlank() }
        packageHint?.let { hint ->
            bases.filter { it.manifest.packageName == hint }.takeIf { it.isNotEmpty() }?.let { bases = it }
        }
        if (bases.isEmpty()) {
            return Selection(
                emptyList(),
                listOf(ArchiveProblem(ArchiveProblemCode.NO_BASE_APK, "No base APK was found")),
            )
        }

        val base = chooseBase(bases, device)
        if (base == null) {
            return Selection(
                emptyList(),
                listOf(
                    ArchiveProblem(
                        ArchiveProblemCode.AMBIGUOUS_BASE_APK,
                        "More than one base APK matches this device",
                    ),
                ),
            )
        }
        if (base.manifest.minSdk?.let { it > device.sdk } == true ||
            base.manifest.maxSdk?.let { it < device.sdk } == true
        ) {
            problems += ArchiveProblem(
                ArchiveProblemCode.INCOMPATIBLE_DEVICE,
                "The base APK does not support Android API ${device.sdk}",
            )
        }

        val lowerPath = base.archivePath.lowercase(Locale.ROOT)
        if ("universal" in lowerPath || lowerPath.startsWith("standalones/")) {
            return Selection(listOf(base), problems)
        }
        val family = apks.filter { candidate ->
            candidate !== base &&
                candidate.manifest.packageName == base.manifest.packageName &&
                candidate.manifest.versionCode == base.manifest.versionCode &&
                when {
                    lowerPath.startsWith("splits/") -> candidate.archivePath.startsWith("splits/", true)
                    else -> !candidate.archivePath.startsWith("standalones/", true)
                }
        }

        val selected = mutableListOf(base)
        val targets = family.groupBy { it.manifest.configForSplit.orEmpty() }
        targets.values.forEach { group ->
            val classified = group.groupBy(::classifyTarget)
            classified[TargetKind.OTHER].orEmpty().let(selected::addAll)

            val abiCandidates = classified[TargetKind.ABI].orEmpty()
            if (abiCandidates.isNotEmpty()) {
                val chosen = abiCandidates.minByOrNull { candidate ->
                    abiIndex(candidate.archivePath, candidate.manifest.splitName, device.abis)
                }
                if (chosen == null ||
                    abiIndex(chosen.archivePath, chosen.manifest.splitName, device.abis) == Int.MAX_VALUE
                ) {
                    problems += ArchiveProblem(
                        ArchiveProblemCode.INCOMPATIBLE_DEVICE,
                        "No APK matches the device ABI",
                    )
                } else {
                    selected += chosen
                }
            }

            classified[TargetKind.DENSITY].orEmpty().minByOrNull {
                abs(densityOf(it) - device.densityDpi)
            }?.let(selected::add)

            classified[TargetKind.LOCALE].orEmpty()
                .filter { localeMatches(it, device.locales) }
                .let(selected::addAll)

            classified[TargetKind.SDK].orEmpty()
                .mapNotNull { entry -> sdkOf(entry)?.let { sdk -> entry to sdk } }
                .filter { (_, sdk) -> sdk <= device.sdk }
                .maxByOrNull { (_, sdk) -> sdk }
                ?.first
                ?.let(selected::add)
        }

        return Selection(selected.distinctBy { it.archivePath }, problems)
    }

    private fun chooseBase(
        bases: List<ArchiveApkEntry>,
        device: PackageDeviceSpec,
    ): ArchiveApkEntry? {
        if (bases.size == 1) return bases.single()
        bases.singleOrNull { it.archivePath.substringAfterLast('/').equals("base.apk", true) }?.let { return it }
        bases.singleOrNull { "universal" in it.archivePath.lowercase(Locale.ROOT) }?.let { return it }

        val ranked = bases.mapNotNull { candidate ->
            val abi = abiIndex(candidate.archivePath, null, device.abis)
            if (containsKnownAbi(candidate.archivePath) && abi == Int.MAX_VALUE) return@mapNotNull null
            val sdk = sdkOf(candidate)
            if (sdk != null && sdk > device.sdk) return@mapNotNull null
            val densityPenalty = densityOf(candidate)
                .takeIf { it > 0 }
                ?.let { abs(it - device.densityDpi) }
                ?: 0
            candidate to (abi.coerceAtMost(1_000) * 100_000 + densityPenalty)
        }.sortedBy { it.second }
        return ranked.firstOrNull()?.takeIf { ranked.size == 1 || ranked[0].second < ranked[1].second }?.first
    }

    private enum class TargetKind {
        ABI,
        DENSITY,
        LOCALE,
        SDK,
        OTHER,
    }

    private fun classifyTarget(entry: ArchiveApkEntry): TargetKind {
        val value = "${entry.archivePath} ${entry.manifest.splitName.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            containsKnownAbi(value) -> TargetKind.ABI
            DENSITIES.keys.any { qualifier -> qualifierIn(value, qualifier) } -> TargetKind.DENSITY
            SDK_QUALIFIER.find(value) != null -> TargetKind.SDK
            localeQualifier(entry) != null -> TargetKind.LOCALE
            else -> TargetKind.OTHER
        }
    }

    private fun containsKnownAbi(value: String): Boolean =
        ABI_ALIASES.keys.any { qualifier -> qualifierIn(value.lowercase(Locale.ROOT), qualifier) }

    private fun abiIndex(path: String, splitName: String?, supportedAbis: List<String>): Int {
        val value = "$path ${splitName.orEmpty()}".lowercase(Locale.ROOT)
        val archiveAbi = ABI_ALIASES.entries.firstOrNull { (qualifier) -> qualifierIn(value, qualifier) }?.value
            ?: return 999
        return supportedAbis.indexOfFirst { supported ->
            ABI_ALIASES[supported.lowercase(Locale.ROOT)] == archiveAbi ||
                supported.equals(archiveAbi, ignoreCase = true)
        }.takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    private fun densityOf(entry: ArchiveApkEntry): Int {
        val value = "${entry.archivePath} ${entry.manifest.splitName.orEmpty()}".lowercase(Locale.ROOT)
        return DENSITIES.entries.firstOrNull { (qualifier) -> qualifierIn(value, qualifier) }?.value ?: -1
    }

    private fun sdkOf(entry: ArchiveApkEntry): Int? {
        val value = "${entry.archivePath} ${entry.manifest.splitName.orEmpty()}".lowercase(Locale.ROOT)
        return SDK_QUALIFIER.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun localeMatches(entry: ArchiveApkEntry, deviceLocales: List<String>): Boolean {
        val qualifier = localeQualifier(entry) ?: return false
        val normalized = qualifier.replace("-r", "-", ignoreCase = true).replace('+', '-')
        val wanted = Locale.forLanguageTag(normalized)
        return deviceLocales.any { tag ->
            val actual = Locale.forLanguageTag(tag)
            actual.language.equals(wanted.language, true) &&
                (wanted.country.isBlank() || actual.country.equals(wanted.country, true)) &&
                (wanted.script.isBlank() || actual.script.equals(wanted.script, true))
        }
    }

    private fun localeQualifier(entry: ArchiveApkEntry): String? {
        val split = entry.manifest.splitName.orEmpty()
        val suffix = split.substringAfter("config.", "")
        if (LOCALE_QUALIFIER.matches(suffix)) return suffix
        val file = entry.archivePath.substringAfterLast('/').substringBeforeLast('.')
        return LOCALE_FROM_FILE.find(file)?.groupValues?.getOrNull(1)
    }

    private fun qualifierIn(value: String, qualifier: String): Boolean =
        Regex("(^|[._/+\\-])${Regex.escape(qualifier)}($|[._/+\\-])", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)

    private fun validateSelected(
        selected: List<ArchiveApkEntry>,
        device: PackageDeviceSpec,
    ): List<ArchiveProblem> {
        if (selected.isEmpty()) return emptyList()
        val problems = mutableListOf<ArchiveProblem>()
        val bases = selected.count { it.manifest.splitName.isNullOrBlank() }
        if (bases != 1) {
            problems += ArchiveProblem(ArchiveProblemCode.NO_BASE_APK, "The APK set must contain exactly one base APK")
        }
        val base = selected.firstOrNull { it.manifest.splitName.isNullOrBlank() } ?: return problems
        if (base.manifest.splitRequired && selected.size == 1) {
            problems += ArchiveProblem(
                ArchiveProblemCode.MISSING_SPLIT_DEPENDENCY,
                "The base APK requires split APK components",
            )
        }
        selected.filter { it.manifest.packageName != base.manifest.packageName }.forEach {
            problems += ArchiveProblem(
                ArchiveProblemCode.PACKAGE_MISMATCH,
                "APK package mismatch: ${it.archivePath}",
            )
        }
        selected.filter { it.manifest.versionCode != base.manifest.versionCode }.forEach {
            problems += ArchiveProblem(
                ArchiveProblemCode.VERSION_MISMATCH,
                "APK version mismatch: ${it.archivePath}",
            )
        }
        selected.mapNotNull { it.manifest.splitName }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { split ->
                problems += ArchiveProblem(ArchiveProblemCode.DUPLICATE_SPLIT, "Duplicate split APK: $split")
            }
        val availableSplits = selected.mapNotNull { it.manifest.splitName }.toSet()
        selected.flatMap { it.manifest.usesSplits }
            .filterNot { it in availableSplits }
            .distinct()
            .forEach { dependency ->
                problems += ArchiveProblem(
                    ArchiveProblemCode.MISSING_SPLIT_DEPENDENCY,
                    "Missing required split APK: $dependency",
                )
            }
        if (base.manifest.minSdk?.let { it > device.sdk } == true ||
            base.manifest.maxSdk?.let { it < device.sdk } == true
        ) {
            problems += ArchiveProblem(
                ArchiveProblemCode.INCOMPATIBLE_DEVICE,
                "Package SDK range does not include Android API ${device.sdk}",
            )
        }
        return problems
    }

    private val ABI_ALIASES = mapOf(
        "arm64-v8a" to "arm64-v8a",
        "arm64_v8a" to "arm64-v8a",
        "armeabi-v7a" to "armeabi-v7a",
        "armeabi_v7a" to "armeabi-v7a",
        "armeabi" to "armeabi",
        "x86_64" to "x86_64",
        "x86-64" to "x86_64",
        "x86" to "x86",
        "riscv64" to "riscv64",
    )

    private val DENSITIES = mapOf(
        "ldpi" to 120,
        "mdpi" to 160,
        "tvdpi" to 213,
        "hdpi" to 240,
        "xhdpi" to 320,
        "xxhdpi" to 480,
        "xxxhdpi" to 640,
    )

    private val SDK_QUALIFIER = Regex("""(?:^|[._/+\-])(?:sdk|api)[._-]?(\d{1,3})(?:$|[._/+\-])""")
    private val LOCALE_QUALIFIER = Regex("""(?:b\+)?[a-z]{2,3}(?:(?:-r|\+)[A-Z]{2}|\+[A-Z][a-z]{3})?""")
    private val LOCALE_FROM_FILE = Regex("""(?:^|[._+\-])((?:b\+)?[a-z]{2,3}(?:(?:-r|\+)[A-Z]{2})?)(?:$|[._+\-])""")
}

internal object ManifestSummaryParser {

    private val manifestTag = Regex("""<manifest\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val applicationTag = Regex("""<application\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val usesSdkTag = Regex("""<uses-sdk\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val permissionTag = Regex("""<uses-permission(?:-sdk-\d+)?\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val declaredPermissionTag =
        Regex("""<permission(?=\s|/?>)([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val usesSplitTag = Regex("""<uses-split\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""(?:^|\s)([A-Za-z_][A-Za-z0-9_.-]*(?::[A-Za-z_][A-Za-z0-9_.-]*)?)\s*=\s*("([^"]*)"|'([^']*)')""")

    fun parse(xml: String): ManifestSummary {
        val root = manifestTag.find(xml)?.groupValues?.get(1)
            ?: AndroidPackageArchiveValidator.reject(
                AndroidPackageArchiveRejection.MANIFEST_ROOT_MISSING,
            )
        val rootAttributes = parseAttributes(root)
        val sdkAttributes = usesSdkTag.find(xml)?.groupValues?.get(1)?.let(::parseAttributes).orEmpty()
        val applicationAttributes = applicationTag.find(xml)
            ?.groupValues
            ?.get(1)
            ?.let(::parseAttributes)
            .orEmpty()
        val lowVersion = rootAttributes.value("versionCode")?.toLongFlexible()
        val highVersion = rootAttributes.value("versionCodeMajor")?.toLongFlexible()
        val versionCode = when {
            lowVersion == null -> null
            highVersion == null -> lowVersion
            else -> (highVersion shl 32) or (lowVersion and 0xFFFF_FFFFL)
        }
        return ManifestSummary(
            packageName = rootAttributes["package"]?.takeIf(String::isNotBlank),
            splitName = rootAttributes["split"]?.takeIf(String::isNotBlank),
            configForSplit = rootAttributes.value("configForSplit")?.takeIf(String::isNotBlank),
            featureSplit = rootAttributes.value("isFeatureSplit").toBooleanFlexible(),
            splitRequired = rootAttributes.value("isSplitRequired").toBooleanFlexible(),
            versionCode = versionCode,
            versionName = rootAttributes.value("versionName")?.takeIf(String::isNotBlank),
            minSdk = sdkAttributes.value("minSdkVersion")?.toIntFlexible(),
            targetSdk = sdkAttributes.value("targetSdkVersion")?.toIntFlexible(),
            maxSdk = sdkAttributes.value("maxSdkVersion")?.toIntFlexible(),
            applicationLabel = applicationAttributes.value("label")?.takeIf(String::isNotBlank),
            applicationIcon = applicationAttributes.value("icon")?.takeIf(String::isNotBlank),
            applicationRoundIcon = applicationAttributes.value("roundIcon")?.takeIf(String::isNotBlank),
            extractNativeLibs = applicationAttributes.value("extractNativeLibs")?.let { value -> value.toBooleanFlexible() },
            requestedPermissions = permissionTag.findAll(xml)
                .mapNotNull { match -> parseAttributes(match.groupValues[1]).value("name") }
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .toList(),
            usesSplits = usesSplitTag.findAll(xml)
                .mapNotNull { match -> parseAttributes(match.groupValues[1]).value("name") }
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .toList(),
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
