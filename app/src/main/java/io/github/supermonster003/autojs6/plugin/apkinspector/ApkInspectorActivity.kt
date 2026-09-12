package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.color.MaterialColors
import io.github.supermonster003.autojs6.plugin.apkinspector.databinding.ActivityApkInspectorBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class ApkInspectorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApkInspectorBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var shareableReport: ShareableReport? = null
    private var renderedReport: InspectionReport? = null
    private var simulationJob: Job? = null
    private var simulationRequestId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MaterialThemeController.applySystemBars(this)
        binding = ActivityApkInspectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureAccessibility()
        configureResponsiveLayout()
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.menu.findItem(R.id.action_share_report).isEnabled = false
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId != R.id.action_share_report) return@setOnMenuItemClickListener false
            shareableReport?.let(::shareReport)
            true
        }

        val packageFile = PackageCacheStager.resolveInternalFile(this, intent.getStringExtra(EXTRA_FILE_PATH))
        val displayName = PackageRequestPolicy.validateDisplayName(intent.getStringExtra(EXTRA_DISPLAY_NAME))
        val byteSize = intent.getLongExtra(EXTRA_BYTE_SIZE, -1L)
        val mimeType = PackageRequestPolicy.normalizeMimeType(intent.getStringExtra(EXTRA_MIME_TYPE))
        val sha256 = intent.getStringExtra(EXTRA_SHA256)?.takeIf(SHA256_PATTERN::matches)
        val v4IdsigFile = PackageCacheStager.resolveV4IdsigFile(
            this,
            intent.getStringExtra(EXTRA_V4_IDSIG_FILE_PATH),
        )
        if (
            packageFile == null || displayName == null ||
            !PackageRequestPolicy.isDeclaredSizeAccepted(byteSize) || packageFile.length() != byteSize ||
            mimeType == null || sha256 == null
        ) {
            showError(getString(R.string.error_cannot_read_package))
            return
        }
        binding.fileSummary.text = listOf(
            getString(R.string.detail_size, Formatter.formatFileSize(this, byteSize)),
            getString(R.string.detail_mime, mimeType),
        ).joinToString("\n")
        binding.viewManifest.isEnabled = false

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    inspect(
                        packageFile,
                        displayName,
                        byteSize,
                        mimeType,
                        sha256,
                        v4IdsigFile,
                    )
                }
                render(result.report)
                configureDeviceSimulation(result.archive, result.deviceSpec)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(
                    buildString {
                        append(getString(R.string.error_cannot_inspect))
                        error.message?.takeIf(String::isNotBlank)?.let { append("\n\n").append(it) }
                    },
                )
            }
        }
    }

    private fun configureAccessibility() {
        listOf(
            binding.packageDetailsHeading,
            binding.componentsHeading,
            binding.deviceSimulationHeading,
            binding.requestedPermissionsHeading,
            binding.findingsHeading,
        ).forEach { heading -> ViewCompat.setAccessibilityHeading(heading, true) }
        ViewCompat.setAccessibilityPaneTitle(
            binding.content,
            getString(R.string.inspection_report_accessibility_title),
        )
    }

    private fun configureResponsiveLayout() {
        val configuration = resources.configuration
        val metrics = resources.displayMetrics
        val screenWidthDp = configuration.screenWidthDp.takeIf { width -> width > 0 }
            ?: (metrics.widthPixels / metrics.density).roundToInt()
        val stacked = ResponsiveLayoutPolicy.shouldUseCompactHeader(
            screenWidthDp = screenWidthDp,
            fontScale = configuration.fontScale,
        )
        val spacing = resources.getDimensionPixelSize(R.dimen.report_header_spacing)
        binding.toolbar.setTitle(
            if (stacked) R.string.inspection_compact_title else R.string.inspection_title,
        )

        binding.reportHeader.apply {
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (stacked) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        binding.appIcon.updateLayoutParams<LinearLayout.LayoutParams> {
            gravity = if (stacked) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        binding.reportHeaderText.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (stacked) ViewGroup.LayoutParams.MATCH_PARENT else 0
            weight = if (stacked) 0f else 1f
            marginStart = if (stacked) 0 else spacing
            topMargin = if (stacked) spacing else 0
            gravity = if (stacked) Gravity.NO_GRAVITY else Gravity.CENTER_VERTICAL
        }
    }

    private fun inspect(
        packageFile: File,
        displayName: String,
        byteSize: Long,
        mimeType: String,
        sha256: String,
        v4IdsigFile: File?,
    ): InspectionResult {
        val deviceSpec = PackageDeviceSpec.from(this)
        val archive = AndroidPackageArchiveInspector.inspect(packageFile, deviceSpec)
        val summary = archive.baseManifest
        val displayApk = when (archive.format) {
            AndroidPackageFormat.APK -> packageFile
            AndroidPackageFormat.AAB -> null
            else -> archive.createDisplayApk(cacheDir)
        }
        val temporaryDirectory = displayApk?.takeUnless { it == packageFile }?.parentFile
        val packageInfo: PackageInfo?
        val icon: Drawable?
        val label: String?
        val signatureVerification: ApkSignatureVerification?
        val signingCertificates: List<SigningCertificateDetails>
        try {
            packageInfo = displayApk?.let(::getPackageInfo)
            val applicationInfo = packageInfo?.applicationInfo?.apply {
                sourceDir = displayApk.absolutePath
                publicSourceDir = displayApk.absolutePath
            }
            label = runCatching { applicationInfo?.loadLabel(packageManager)?.toString() }.getOrNull()
            icon = runCatching { applicationInfo?.loadIcon(packageManager) }.getOrNull()
            signatureVerification = displayApk?.let { apk ->
                runCatching {
                    ApkSignatureVerifier.verify(
                        apkFile = apk,
                        v4IdsigFile = v4IdsigFile?.takeIf { displayApk == packageFile },
                    )
                }.getOrNull()
            }
            signingCertificates = SigningCertificateParser.parseAll(
                getCurrentSignerEncodings(packageInfo),
            )
        } finally {
            temporaryDirectory?.deleteRecursively()
        }

        val resourceFallback = PackageResourceFallbackInspector.inspect(
            archive = archive,
            device = deviceSpec,
            needLabel = label == null,
            needIcon = icon == null,
        )
        val fallbackIcon = resourceFallback.icon?.let(::decodeResourceIcon)
        val resourceFallbackIssues = buildList {
            addAll(resourceFallback.issues)
            if (icon == null && resourceFallback.icon != null && fallbackIcon == null) {
                add(PackageResourceFallbackIssue.ICON_INVALID)
            }
        }.distinct()
        val resolvedIcon = icon ?: fallbackIcon

        val versionCode = packageInfo?.let { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } ?: summary?.versionCode
        val requestedPermissions =
            packageInfo?.requestedPermissions.orEmpty().asList() + summary?.requestedPermissions.orEmpty()
        val declaredProtectionLevels = buildMap {
            summary?.declaredPermissionProtectionLevels.orEmpty().entries.asSequence()
                .take(MAX_DECLARED_PERMISSION_DEFINITIONS)
                .forEach { (name, protectionLevel) -> put(name, protectionLevel) }
            packageInfo?.permissions.orEmpty().asSequence()
                .take(MAX_DECLARED_PERMISSION_DEFINITIONS)
                .forEach { permission ->
                    permission.name?.takeIf(String::isNotBlank)?.let { name ->
                        put(name, getProtectionLevel(permission))
                    }
                }
        }
        val permissionAnalysis = PermissionProtectionAnalyzer.analyze(
            requestedPermissions = requestedPermissions,
            declaredProtectionLevels = declaredProtectionLevels,
            resolvePermission = ::resolvePermissionDefinition,
        )
        val manifestPath = runCatching {
            val xml = archive.decodeDisplayManifest()
            ReadOnlyTextSnapshot.replace(
                File(packageFile.parentFile, "manifest.xml"),
                xml,
            ).absolutePath
        }.getOrNull()

        val unknown = getString(R.string.text_unknown)
        val appLabel = label ?: resourceFallback.label ?: displayName
        val archiveFormat = formatName(archive)
        val packageName = packageInfo?.packageName ?: summary?.packageName ?: unknown
        val versionName = packageInfo?.versionName ?: summary?.versionName ?: unknown
        val versionCodeText = versionCode?.toString() ?: unknown
        val minSdk = packageInfo?.applicationInfo?.minSdkVersion?.takeIf { it > 0 }?.toString()
            ?: summary?.minSdk?.toString() ?: unknown
        val targetSdk = packageInfo?.applicationInfo?.targetSdkVersion?.takeIf { it > 0 }?.toString()
            ?: summary?.targetSdk?.toString() ?: unknown
        val maxSdk = summary?.maxSdk?.toString() ?: unknown
        val signatureSummary = signatureVerification?.let(::formatSignatureVerification) ?: unknown
        val certificateCount = signingCertificates.size.takeIf { it > 0 }?.toString() ?: unknown
        val formattedSize = Formatter.formatFileSize(this, byteSize)
        val detailFields = listOf(
            CopyableReportField(getString(R.string.detail_file, displayName), displayName),
            CopyableReportField(getString(R.string.detail_format, archiveFormat), archiveFormat),
            CopyableReportField(getString(R.string.detail_label, appLabel), appLabel),
            CopyableReportField(getString(R.string.detail_package_name, packageName), packageName),
            CopyableReportField(
                getString(R.string.detail_version, versionName, versionCodeText),
                "$versionName ($versionCodeText)",
            ),
            CopyableReportField(
                getString(R.string.detail_sdk, minSdk, targetSdk, maxSdk),
                "$minSdk | $targetSdk | $maxSdk",
            ),
            CopyableReportField(
                getString(R.string.detail_device_sdk, Build.VERSION.SDK_INT),
                Build.VERSION.SDK_INT.toString(),
            ),
            CopyableReportField(
                getString(R.string.detail_signature, signatureSummary),
                signatureSummary,
            ),
            CopyableReportField(
                getString(R.string.detail_signing_certificates, certificateCount),
                certificateCount,
            ),
            CopyableReportField(getString(R.string.detail_size, formattedSize), formattedSize),
            CopyableReportField(getString(R.string.detail_sha256, sha256), sha256),
        )
        val containerMetadataDetails = formatContainerMetadata(archive.containerMetadata)
        val certificateDetails = formatSigningCertificates(signingCertificates)
        val lineageDetails = signatureVerification?.lineage
            ?.let(::formatSigningCertificateLineage)
            .orEmpty()
        val detailSupplement = listOf(containerMetadataDetails, certificateDetails, lineageDetails)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")

        val componentEntries = when (archive.format) {
            AndroidPackageFormat.AAB -> formatAabModuleEntries(archive)
            else -> archive.selectedApks.joinToString("\n") { apk ->
                val split = apk.manifest.splitName?.takeIf(String::isNotBlank) ?: "base"
                "- $split | ${apk.archivePath} | ${Formatter.formatFileSize(this, apk.size)}"
            }
        }.ifBlank { getString(R.string.text_none) }
        val packageComponents = when (archive.format) {
            AndroidPackageFormat.AAB -> listOf(
                getString(R.string.components_aab, archive.aabModules.size, componentEntries),
                formatAabBundleConfig(archive.aabBundleConfig),
            ).filter(String::isNotEmpty).joinToString("\n\n")
            else -> getString(
                R.string.components_apk,
                archive.apkEntryCount,
                archive.selectedApks.size,
                archive.obbEntries.size,
                componentEntries,
            )
        }
        val components = listOf(
            packageComponents,
            formatManifestComponents(archive),
            formatNativeLibraries(archive.nativeLibraries, archive.pageSizeReadiness),
            formatDexFiles(archive.dexFiles),
        ).joinToString("\n\n")

        val findings = buildList {
            when (archive.inspectionState) {
                ArchiveInspectionState.COMPATIBLE -> Unit
                ArchiveInspectionState.COMPATIBLE_WITH_OBB ->
                    add(getString(R.string.finding_obb, archive.obbEntries.size))
                ArchiveInspectionState.AAB_SOURCE -> add(getString(R.string.finding_aab_conversion))
                ArchiveInspectionState.INCOMPATIBLE -> add(getString(R.string.finding_incompatible))
                ArchiveInspectionState.INVALID -> add(getString(R.string.finding_invalid))
            }
            archive.problems.forEach { problem ->
                add("${if (problem.blocking) "[!]" else "[i]"} ${problem.code}: ${problem.detail}")
            }
            resourceFallbackIssues.forEach { issue ->
                add(getString(resourceFallbackIssueString(issue)))
            }
            if (archive.pageSizeReadiness.state == PageSizeReadinessState.NOT_READY) {
                add(
                    getString(
                        R.string.finding_page_size_not_ready,
                        archive.pageSizeReadiness.unalignedCount + archive.pageSizeReadiness.zipMisalignedCount,
                    ),
                )
            }
        }.distinct().ifEmpty { listOf(getString(R.string.finding_none)) }.joinToString("\n")

        return InspectionResult(
            report = InspectionReport(
                appLabel = appLabel,
                icon = resolvedIcon,
                detailFields = detailFields,
                detailSupplement = detailSupplement,
                components = components,
                permissions = permissionAnalysis,
                findings = findings,
                manifestPath = manifestPath,
            ),
            archive = archive,
            deviceSpec = deviceSpec,
        )
    }

    private fun decodeResourceIcon(icon: PackageResourceIcon): Drawable? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(icon.bytes, 0, icon.bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_FALLBACK_ICON_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_FALLBACK_ICON_DIMENSION
        ) {
            sampleSize = Math.multiplyExact(sampleSize, 2)
        }
        val bitmap = BitmapFactory.decodeByteArray(
            icon.bytes,
            0,
            icon.bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return@runCatching null
        bitmap.toDrawable(resources)
    }.getOrNull()

    private fun resourceFallbackIssueString(issue: PackageResourceFallbackIssue): Int = when (issue) {
        PackageResourceFallbackIssue.RESOURCE_TABLE_MISSING ->
            R.string.resource_fallback_table_missing
        PackageResourceFallbackIssue.RESOURCE_TABLE_LIMIT ->
            R.string.resource_fallback_table_limit
        PackageResourceFallbackIssue.NESTED_SCAN_LIMIT ->
            R.string.resource_fallback_scan_limit
        PackageResourceFallbackIssue.RESOURCE_TABLE_INVALID ->
            R.string.resource_fallback_table_invalid
        PackageResourceFallbackIssue.LABEL_UNRESOLVED ->
            R.string.resource_fallback_label_unresolved
        PackageResourceFallbackIssue.ICON_UNRESOLVED ->
            R.string.resource_fallback_icon_unresolved
        PackageResourceFallbackIssue.ICON_LIMIT ->
            R.string.resource_fallback_icon_limit
        PackageResourceFallbackIssue.ICON_INVALID ->
            R.string.resource_fallback_icon_invalid
    }

    private fun getPackageInfo(apkFile: File): PackageInfo? = runCatching {
        val signerFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS or signerFlag,
        )
    }.getOrNull()

    private fun resolvePermissionDefinition(permissionName: String): PermissionDefinition {
        @Suppress("DEPRECATION")
        val permission = packageManager.getPermissionInfo(permissionName, 0)
        return PermissionDefinition(
            protectionLevel = getProtectionLevel(permission),
            description = runCatching {
                permission.loadDescription(packageManager)?.toString()
            }.getOrNull(),
        )
    }

    private fun getProtectionLevel(permission: PermissionInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permission.protection
        } else {
            @Suppress("DEPRECATION")
            permission.protectionLevel
        }

    private fun getCurrentSignerEncodings(packageInfo: PackageInfo?): List<ByteArray> {
        packageInfo ?: return emptyList()
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        return signatures.orEmpty().map { signature -> signature.toByteArray() }
    }

    private fun formatSigningCertificates(
        certificates: List<SigningCertificateDetails>,
    ): String {
        if (certificates.isEmpty()) return ""
        val dateFormat = SimpleDateFormat(CERTIFICATE_DATE_PATTERN, Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return certificates.mapIndexed { index, certificate ->
            listOf(
                getString(
                    R.string.detail_signing_certificate_title,
                    index + 1,
                    certificates.size,
                ),
                getString(R.string.detail_certificate_subject, certificate.subject),
                getString(R.string.detail_certificate_issuer, certificate.issuer),
                getString(R.string.detail_certificate_serial, certificate.serialNumberHex),
                getString(
                    R.string.detail_certificate_validity,
                    dateFormat.format(Date(certificate.notBeforeMillis)),
                    dateFormat.format(Date(certificate.notAfterMillis)),
                ),
                getString(
                    R.string.detail_certificate_sha256,
                    certificate.sha256Fingerprint,
                ),
            ).joinToString("\n")
        }.joinToString("\n\n")
    }

    private fun formatSigningCertificateLineage(
        lineage: ApkSigningCertificateLineage,
    ): String {
        if (lineage.certificates.isEmpty()) return ""
        val relation = lineage.certificates.mapIndexed { index, node ->
            val role = formatLineageRole(index, lineage.certificates.size)
            "$role (${node.certificate.sha256Fingerprint.take(FINGERPRINT_PREVIEW_CHARS)}…)"
        }.joinToString(" → ")
        val overview = listOf(
            getString(R.string.detail_signing_lineage_title, lineage.certificates.size),
            getString(R.string.detail_signing_lineage_relation, relation),
        ).joinToString("\n")
        val certificateEntries = lineage.certificates.mapIndexed { index, node ->
            listOf(
                getString(
                    R.string.detail_signing_lineage_certificate_title,
                    index + 1,
                    lineage.certificates.size,
                    formatLineageRole(index, lineage.certificates.size),
                ),
                getString(R.string.detail_certificate_subject, node.certificate.subject),
                getString(
                    R.string.detail_certificate_sha256,
                    node.certificate.sha256Fingerprint,
                ),
                getString(
                    R.string.detail_signing_lineage_capabilities,
                    formatLineageCapabilities(node.capabilityFlags),
                ),
            ).joinToString("\n")
        }
        return (listOf(overview) + certificateEntries).joinToString("\n\n")
    }

    private fun formatLineageRole(index: Int, certificateCount: Int): String = getString(
        when {
            index == certificateCount - 1 -> R.string.lineage_role_current
            index == 0 -> R.string.lineage_role_original
            else -> R.string.lineage_role_intermediate
        },
    )

    private fun formatLineageCapabilities(flags: Int): String {
        val labels = ApkSigningCertificateCapability.entries.mapNotNull { capability ->
            if (flags and capability.mask == 0) return@mapNotNull null
            getString(
                when (capability) {
                    ApkSigningCertificateCapability.INSTALLED_DATA ->
                        R.string.lineage_capability_installed_data
                    ApkSigningCertificateCapability.SHARED_UID ->
                        R.string.lineage_capability_shared_uid
                    ApkSigningCertificateCapability.SIGNATURE_PERMISSION ->
                        R.string.lineage_capability_signature_permission
                    ApkSigningCertificateCapability.ROLLBACK ->
                        R.string.lineage_capability_rollback
                    ApkSigningCertificateCapability.AUTHENTICATION ->
                        R.string.lineage_capability_authentication
                },
            )
        }.toMutableList()
        val unknownFlags = flags and LINEAGE_CAPABILITY_MASK.inv()
        if (unknownFlags != 0) {
            labels += getString(
                R.string.lineage_capability_unknown,
                "0x${Integer.toUnsignedString(unknownFlags, 16).padStart(8, '0')}",
            )
        }
        return labels.joinToString(", ").ifEmpty { getString(R.string.text_none) }
    }

    private fun formatSignatureVerification(
        verification: ApkSignatureVerification,
    ): String = buildList {
        if (verification.hasV1) {
            add("V1 — ${getString(R.string.signature_state_present)}")
        }
        verification.v2?.let { add(formatSchemeVerification("V2", it)) }
        verification.v3?.let { add(formatSchemeVerification("V3", it)) }
        verification.v31?.let { add(formatSchemeVerification("V3.1", it)) }
        verification.v4?.let { add(formatSchemeVerification("V4", it)) }
    }.joinToString("\n").ifBlank { getString(R.string.text_none) }

    private fun formatSchemeVerification(
        scheme: String,
        verification: ApkSchemeVerification,
    ): String {
        val conclusion = when (verification.state) {
            ApkSignatureVerificationState.PRESENT -> verification.reason?.let {
                getString(
                    R.string.signature_state_present_reason,
                    formatSignatureReason(verification),
                )
            } ?: getString(R.string.signature_state_present)

            ApkSignatureVerificationState.VERIFIED -> getString(
                R.string.signature_state_verified,
                verification.signerCount,
            )

            ApkSignatureVerificationState.FAILED -> getString(
                R.string.signature_state_failed,
                formatSignatureReason(verification),
            )
        }
        val sdkDetails = buildList {
            verification.minimumSdkVersion?.let { minimumSdk ->
                add(getString(R.string.signature_scheme_min_sdk, minimumSdk))
            }
            verification.rotationMinSdkVersion?.let { rotationMinSdk ->
                add(getString(R.string.signature_scheme_rotation_min_sdk, rotationMinSdk))
            }
        }
        return buildString {
            append(scheme).append(" — ").append(conclusion)
            if (sdkDetails.isNotEmpty()) {
                append(" [").append(sdkDetails.joinToString("; ")).append(']')
            }
        }
    }

    private fun formatSignatureReason(verification: ApkSchemeVerification): String =
        buildString {
            append(verification.reason?.name ?: getString(R.string.text_unknown))
            verification.detail?.takeIf(String::isNotBlank)?.let { detail ->
                append(": ").append(detail)
            }
        }

    private fun formatName(archive: AndroidPackageArchive): String = when (archive.subtype) {
        AndroidPackageSubtype.SINGLE_APK -> "APK"
        AndroidPackageSubtype.BUNDLETOOL_APKS -> "APKS (bundletool)"
        AndroidPackageSubtype.SAI_APKS -> "APKS (SAI)"
        AndroidPackageSubtype.GENERIC_APKS -> "APKS"
        AndroidPackageSubtype.XAPK -> "XAPK"
        AndroidPackageSubtype.APKM -> "APKM"
        AndroidPackageSubtype.APKZ -> "APKZ"
        AndroidPackageSubtype.ANDROID_APP_BUNDLE -> "AAB"
    }

    private fun formatContainerMetadata(metadata: ContainerMetadataSummary): String {
        val sourceEntry = metadata.sourceEntry ?: return ""
        val unknown = getString(R.string.text_unknown)
        return buildList {
            add(getString(R.string.container_metadata_heading, sourceEntry))
            val packagerName = metadata.packager?.displayName ?: unknown
            val packager = metadata.packagerVersion
                ?.let { version -> "$packagerName v$version" }
                ?: packagerName
            add(getString(R.string.container_metadata_packager, packager))
            when (metadata.issue) {
                ContainerMetadataIssue.METADATA_LIMIT -> add(
                    getString(
                        R.string.container_metadata_limit,
                        ContainerMetadataInspector.MAX_METADATA_BYTES / (1024 * 1024),
                    ),
                )

                ContainerMetadataIssue.METADATA_INVALID -> add(
                    getString(R.string.container_metadata_invalid),
                )

                null -> {
                    add(
                        getString(
                            R.string.container_metadata_declared_version,
                            metadata.declaredVersionName ?: unknown,
                            metadata.declaredVersionCode ?: unknown,
                        ),
                    )
                    add(
                        getString(
                            R.string.container_metadata_icon_entry,
                            metadata.iconEntry ?: getString(R.string.text_none),
                        ),
                    )
                }
            }
        }.joinToString("\n")
    }

    private fun formatAabModuleEntries(archive: AndroidPackageArchive): String {
        val metadataByName = archive.aabModuleMetadata.associateBy(AabModuleMetadata::name)
        return buildList {
            archive.aabModules.forEach { moduleName ->
                val metadata = metadataByName[moduleName]
                if (metadata == null) {
                    add("- $moduleName | ${getString(R.string.aab_module_metadata_not_scanned)}")
                } else {
                    add(formatAabModule(metadata))
                }
            }
            if (archive.aabModuleMetadataOmittedCount > 0) {
                add(
                    getString(
                        R.string.aab_module_metadata_limit,
                        archive.aabModuleMetadataOmittedCount,
                        AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFESTS,
                    ),
                )
            }
        }.joinToString("\n")
    }

    private fun formatAabModule(metadata: AabModuleMetadata): String {
        val type = when (metadata.type) {
            AabModuleType.BASE -> "base"
            AabModuleType.FEATURE -> "feature"
            AabModuleType.ASSET_PACK -> "asset-pack"
            AabModuleType.ML_PACK -> "ml-pack"
            AabModuleType.AI_PACK -> "ai-pack"
            AabModuleType.SDK -> "sdk"
            AabModuleType.UNKNOWN -> metadata.declaredType ?: getString(R.string.text_unknown)
        }
        val delivery = metadata.deliveryModes.joinToString(" + ") { mode ->
            getString(
                when (mode) {
                    AabModuleDeliveryMode.INSTALL_TIME -> R.string.aab_delivery_install_time
                    AabModuleDeliveryMode.ON_DEMAND -> R.string.aab_delivery_on_demand
                    AabModuleDeliveryMode.FAST_FOLLOW -> R.string.aab_delivery_fast_follow
                    AabModuleDeliveryMode.UNKNOWN -> R.string.text_unknown
                },
            )
        }.ifBlank { getString(R.string.text_unknown) }.let { modes ->
            if (metadata.conditions.isNotEmpty() || metadata.omittedConditionCount > 0) {
                "$modes (${getString(R.string.aab_delivery_conditional)})"
            } else {
                modes
            }
        }
        val details = buildList {
            add(type)
            add(delivery)
            formatAabConditions(metadata).takeIf(String::isNotEmpty)?.let(::add)
            metadata.fusingIncluded?.let { included ->
                add(getString(R.string.aab_module_fusing, formatAabBoolean(included)))
            }
            metadata.installTimeRemovable?.let { removable ->
                add(getString(R.string.aab_module_removable, formatAabBoolean(removable)))
            }
            if (metadata.issue != null) add(getString(R.string.aab_module_metadata_invalid))
        }
        return "- ${metadata.name} | ${details.joinToString(" | ")}"
    }

    private fun formatAabConditions(metadata: AabModuleMetadata): String = buildList {
        metadata.conditions.filter { it.kind == AabModuleConditionKind.MIN_SDK }.forEach { condition ->
            add(getString(R.string.aab_condition_min_sdk, condition.value))
        }
        metadata.conditions.filter { it.kind == AabModuleConditionKind.MAX_SDK }.forEach { condition ->
            add(getString(R.string.aab_condition_max_sdk, condition.value))
        }
        metadata.conditions.filter { it.kind == AabModuleConditionKind.DEVICE_FEATURE }
            .forEach { condition ->
                val value = condition.version?.let { version -> "${condition.value}@$version" }
                    ?: condition.value
                add(getString(R.string.aab_condition_device_feature, value))
            }
        listOf(
            AabModuleConditionKind.INCLUDED_COUNTRY to R.string.aab_condition_countries_included,
            AabModuleConditionKind.EXCLUDED_COUNTRY to R.string.aab_condition_countries_excluded,
            AabModuleConditionKind.DEVICE_GROUP to R.string.aab_condition_device_groups,
        ).forEach { (kind, stringResource) ->
            val values = metadata.conditions.filter { condition -> condition.kind == kind }
                .map(AabModuleCondition::value)
            if (values.isNotEmpty()) add(getString(stringResource, values.joinToString(", ")))
        }
        metadata.conditions.filter { it.kind == AabModuleConditionKind.UNKNOWN }.forEach { condition ->
            add(getString(R.string.aab_condition_unknown, condition.value))
        }
        if (metadata.omittedConditionCount > 0) {
            add(getString(R.string.aab_condition_omitted, metadata.omittedConditionCount))
        }
    }.joinToString("; ")

    private fun formatAabBundleConfig(summary: AabBundleConfigSummary?): String {
        summary ?: return ""
        val issue = summary.issue
        if (issue != null) {
            return buildList {
                add(getString(R.string.aab_bundle_config_heading))
                add(
                    when (issue.code) {
                        AabMetadataIssueCode.MISSING -> getString(R.string.aab_bundle_config_missing)
                        AabMetadataIssueCode.LIMIT -> getString(
                            R.string.aab_bundle_config_limit,
                            AndroidPackageArchiveInspector.MAX_AAB_BUNDLE_CONFIG_BYTES / (1024 * 1024),
                        )
                        AabMetadataIssueCode.INVALID -> getString(R.string.aab_bundle_config_invalid)
                    },
                )
            }.joinToString("\n")
        }

        val type = if (summary.bundleType == AabBundleType.UNKNOWN) {
            "UNKNOWN(${summary.rawBundleType})"
        } else {
            summary.bundleType.name
        }
        val splitDimensions = summary.splitDimensions.joinToString(", ") { dimension ->
            val name = if (dimension.dimension == AabSplitDimension.UNKNOWN) {
                "UNKNOWN(${dimension.rawDimension})"
            } else {
                dimension.dimension.name
            }
            buildString {
                append(name)
                append('=')
                append(formatAabEnabled(dimension.splitEnabled))
                dimension.suffixStrippingEnabled?.let { enabled ->
                    append(" [suffixStripping=").append(formatAabEnabled(enabled))
                    dimension.defaultSuffix?.let { suffix -> append(", default=").append(suffix) }
                    append(']')
                }
            }
        }.ifBlank { getString(R.string.text_none) }
        val compression = buildList {
            add("uncompressedGlobs=${summary.uncompressedGlobCount}")
            summary.installTimeAssetCompression?.let { compression ->
                val value = if (compression == AabAssetModuleCompression.UNKNOWN) {
                    "UNKNOWN(${summary.rawInstallTimeAssetCompression})"
                } else {
                    compression.name
                }
                add("installTimeAssets=$value")
            }
            summary.apkCompressionAlgorithm?.let { algorithm ->
                val value = if (algorithm == AabApkCompressionAlgorithm.UNKNOWN) {
                    "UNKNOWN(${summary.rawApkCompressionAlgorithm})"
                } else {
                    algorithm.name
                }
                add("APK=$value")
            }
        }.joinToString(" | ")
        val optimizations = buildList {
            summary.uncompressNativeLibraries?.let { add("nativeLibraries=${formatAabEnabled(it)}") }
            summary.uncompressDexFiles?.let { add("DEX=${formatAabEnabled(it)}") }
            summary.injectLocaleConfig?.let { add("localeConfig=${formatAabEnabled(it)}") }
        }.joinToString(" | ").ifBlank { getString(R.string.text_none) }
        return listOf(
            getString(R.string.aab_bundle_config_heading),
            getString(
                R.string.aab_bundle_config_bundletool,
                summary.bundletoolVersion ?: getString(R.string.text_unknown),
            ),
            getString(R.string.aab_bundle_config_type, type),
            getString(R.string.aab_bundle_config_splits, splitDimensions),
            getString(R.string.aab_bundle_config_compression, compression),
            getString(R.string.aab_bundle_config_optimizations, optimizations),
        ).joinToString("\n")
    }

    private fun formatAabEnabled(enabled: Boolean): String = getString(
        if (enabled) R.string.aab_value_enabled else R.string.aab_value_disabled,
    )

    private fun formatAabBoolean(value: Boolean): String = getString(
        if (value) R.string.aab_value_yes else R.string.aab_value_no,
    )

    private fun render(report: InspectionReport) {
        binding.progress.isVisible = false
        binding.error.isVisible = false
        binding.content.isVisible = true
        renderedReport = report
        binding.appLabel.text = report.appLabel
        report.icon?.let(binding.appIcon::setImageDrawable)
        binding.packageFields.removeAllViews()
        report.detailFields.forEach { field ->
            val fieldView = layoutInflater.inflate(
                R.layout.item_copyable_report_field,
                binding.packageFields,
                false,
            ) as TextView
            fieldView.text = field.displayText
            fieldView.contentDescription = getString(
                R.string.report_field_copy_description,
                field.displayText,
            )
            fieldView.setOnLongClickListener {
                copyReportField(field)
                true
            }
            binding.packageFields.addView(fieldView)
        }
        binding.packageDetails.text = report.detailSupplement
        binding.packageDetails.isVisible = report.detailSupplement.isNotEmpty()
        binding.components.text = report.components
        val permissionsText = formatRequestedPermissions(report.permissions)
        binding.permissions.text = permissionsText
        binding.findings.text = report.findings
        binding.viewManifest.isVisible = report.manifestPath != null
        binding.viewManifest.isEnabled = report.manifestPath != null
        binding.viewManifest.setOnClickListener {
            report.manifestPath?.let { path -> startActivity(ManifestViewerActivity.createIntent(this, path)) }
        }
        refreshShareableReport()
    }

    private fun configureDeviceSimulation(
        archive: AndroidPackageArchive,
        actualDevice: PackageDeviceSpec,
    ) {
        val supported = archive.format != AndroidPackageFormat.APK &&
            archive.format != AndroidPackageFormat.AAB && archive.apkEntryCount > 0
        binding.deviceSimulation.isVisible = supported
        if (!supported) {
            refreshShareableReport()
            return
        }

        val actualConfiguration = PackageSimulationConfiguration.from(actualDevice)
        val languageOptions = (listOf(actualConfiguration.languageTag) + SIMULATION_LANGUAGE_TAGS)
            .filter(String::isNotBlank)
            .distinctBy { languageTag -> languageTag.lowercase(Locale.ROOT) }
        val densityOptions = (listOf(actualConfiguration.densityDpi) + SIMULATION_DENSITIES)
            .filter { density -> density > 0 }
            .distinct()
        val abiOptions = (listOf(actualConfiguration.abi) + SIMULATION_ABIS)
            .filter(String::isNotBlank)
            .distinctBy { abi -> abi.lowercase(Locale.ROOT) }

        binding.deviceSimulationActual.text = formatActualDevice(actualDevice)
        bindSpinner(binding.deviceSimulationLanguage, languageOptions) { languageTag -> languageTag }
        bindSpinner(binding.deviceSimulationDensity, densityOptions) { density -> "$density dpi" }
        bindSpinner(binding.deviceSimulationAbi, abiOptions) { abi -> abi }

        val actualSimulation = PackageSelectionSimulator.simulate(
            actualArchive = archive,
            actualDevice = actualDevice,
            simulatedDevice = actualDevice,
        )
        renderDeviceSimulation(actualSimulation)

        binding.applyDeviceSimulation.setOnClickListener {
            val configuration = PackageSimulationConfiguration(
                languageTag = languageOptions[binding.deviceSimulationLanguage.selectedItemPosition],
                densityDpi = densityOptions[binding.deviceSimulationDensity.selectedItemPosition],
                abi = abiOptions[binding.deviceSimulationAbi.selectedItemPosition],
            )
            launchDeviceSimulation(
                archive = archive,
                actualDevice = actualDevice,
                simulatedDevice = configuration.applyTo(actualDevice),
            )
        }
        binding.resetDeviceSimulation.setOnClickListener {
            simulationRequestId += 1
            simulationJob?.cancel()
            simulationJob = null
            binding.deviceSimulationLanguage.setSelection(0)
            binding.deviceSimulationDensity.setSelection(0)
            binding.deviceSimulationAbi.setSelection(0)
            setDeviceSimulationLoading(false)
            renderDeviceSimulation(actualSimulation)
        }
        refreshShareableReport()
    }

    private fun launchDeviceSimulation(
        archive: AndroidPackageArchive,
        actualDevice: PackageDeviceSpec,
        simulatedDevice: PackageDeviceSpec,
    ) {
        simulationJob?.cancel()
        val requestId = ++simulationRequestId
        simulationJob = scope.launch {
            setDeviceSimulationLoading(true)
            try {
                val simulation = withContext(Dispatchers.IO) {
                    PackageSelectionSimulator.simulate(
                        actualArchive = archive,
                        actualDevice = actualDevice,
                        simulatedDevice = simulatedDevice,
                    )
                }
                if (requestId == simulationRequestId) {
                    renderDeviceSimulation(simulation)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == simulationRequestId) {
                    val detail = error.message?.takeIf(String::isNotBlank)
                        ?: getString(R.string.text_unknown)
                    binding.deviceSimulationResult.text = getString(
                        R.string.device_simulation_error,
                        detail,
                    )
                    refreshShareableReport()
                }
            } finally {
                if (requestId == simulationRequestId) {
                    setDeviceSimulationLoading(false)
                    simulationJob = null
                }
            }
        }
    }

    private fun renderDeviceSimulation(simulation: PackageSelectionSimulation) {
        val simulatedConfiguration = getString(
            R.string.device_simulation_simulated,
            simulation.simulatedDevice.locales.joinToString(", ").ifBlank {
                getString(R.string.text_unknown)
            },
            simulation.simulatedDevice.densityDpi,
            simulation.simulatedDevice.abis.joinToString(", ").ifBlank {
                getString(R.string.text_unknown)
            },
        )
        val configurationDifference = if (simulation.changedDimensions.isEmpty()) {
            getString(R.string.device_simulation_uses_actual)
        } else {
            getString(
                R.string.device_simulation_changed_dimensions,
                simulation.changedDimensions.joinToString(", ") { dimension ->
                    getString(
                        when (dimension) {
                            PackageDeviceDimension.LANGUAGE -> R.string.device_simulation_language
                            PackageDeviceDimension.DENSITY -> R.string.device_simulation_density
                            PackageDeviceDimension.ABI -> R.string.device_simulation_abi
                        },
                    )
                },
            )
        }
        val state = getString(
            when (simulation.simulatedInspectionState) {
                ArchiveInspectionState.COMPATIBLE,
                ArchiveInspectionState.COMPATIBLE_WITH_OBB,
                -> R.string.device_simulation_status_compatible
                ArchiveInspectionState.INCOMPATIBLE -> R.string.device_simulation_status_incompatible
                ArchiveInspectionState.INVALID,
                ArchiveInspectionState.AAB_SOURCE,
                -> R.string.device_simulation_status_invalid
            },
        )
        val overview = listOf(
            simulatedConfiguration,
            configurationDifference,
            getString(R.string.device_simulation_state, state),
            getString(
                if (simulation.selectionMatchesActual) {
                    R.string.device_simulation_selection_same
                } else {
                    R.string.device_simulation_selection_different
                },
            ),
        ).joinToString("\n")
        val selected = buildList {
            add(
                getString(
                    R.string.device_simulation_selected_apks,
                    simulation.simulatedSelectedApkPaths.size,
                ),
            )
            if (simulation.simulatedSelectedApkPaths.isEmpty()) {
                add("- ${getString(R.string.text_none)}")
            } else {
                simulation.simulatedSelectedApkPaths.forEach { path -> add("- $path") }
            }
        }.joinToString("\n")
        val differences = buildList {
            if (simulation.addedApkPaths.isNotEmpty()) {
                add(getString(R.string.device_simulation_added_apks, simulation.addedApkPaths.size))
                simulation.addedApkPaths.forEach { path -> add("+ $path") }
            }
            if (simulation.removedApkPaths.isNotEmpty()) {
                add(getString(R.string.device_simulation_removed_apks, simulation.removedApkPaths.size))
                simulation.removedApkPaths.forEach { path -> add("- $path") }
            }
        }.joinToString("\n")
        val problems = simulation.simulatedProblems.asSequence()
            .filter(ArchiveProblem::blocking)
            .map { problem -> "[!] ${problem.code}: ${problem.detail}" }
            .joinToString("\n")
        binding.deviceSimulationResult.text = listOf(overview, selected, differences, problems)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")
        refreshShareableReport()
    }

    private fun formatActualDevice(device: PackageDeviceSpec): String = getString(
        R.string.device_simulation_actual,
        device.locales.joinToString(", ").ifBlank { getString(R.string.text_unknown) },
        device.densityDpi,
        device.abis.joinToString(", ").ifBlank { getString(R.string.text_unknown) },
    )

    private fun setDeviceSimulationLoading(loading: Boolean) {
        binding.deviceSimulationLanguage.isEnabled = !loading
        binding.deviceSimulationDensity.isEnabled = !loading
        binding.deviceSimulationAbi.isEnabled = !loading
        binding.applyDeviceSimulation.isEnabled = !loading
        binding.resetDeviceSimulation.isEnabled = !loading
        binding.deviceSimulationProgress.isVisible = loading
    }

    private fun <T> bindSpinner(
        spinner: Spinner,
        values: List<T>,
        format: (T) -> String,
    ) {
        spinner.adapter = ArrayAdapter(
            this,
            R.layout.item_device_simulation_spinner,
            values.map(format),
        ).apply {
            setDropDownViewResource(R.layout.item_device_simulation_spinner)
        }
        spinner.setSelection(0, false)
    }

    private fun refreshShareableReport() {
        val report = renderedReport ?: return
        val sections = buildList {
            add(
                InspectionReportTextSection(
                    getString(R.string.section_package_details),
                    report.details,
                ),
            )
            add(
                InspectionReportTextSection(
                    getString(R.string.section_components),
                    binding.components.text,
                ),
            )
            if (binding.deviceSimulation.isVisible) {
                val simulationText = listOf(
                    binding.deviceSimulationDescription.text,
                    binding.deviceSimulationActual.text,
                    binding.deviceSimulationResult.text,
                ).filter(CharSequence::isNotBlank).joinToString("\n\n")
                if (simulationText.isNotBlank()) {
                    add(
                        InspectionReportTextSection(
                            getString(R.string.section_device_simulation),
                            simulationText,
                        ),
                    )
                }
            }
            add(
                InspectionReportTextSection(
                    getString(R.string.section_requested_permissions),
                    binding.permissions.text,
                ),
            )
            add(
                InspectionReportTextSection(
                    getString(R.string.section_findings),
                    binding.findings.text,
                ),
            )
        }
        shareableReport = ShareableReport(
            subject = getString(R.string.report_share_subject, report.appLabel),
            text = InspectionReportTextFormatter.format(
                appLabel = binding.appLabel.text,
                fileSummary = binding.fileSummary.text,
                sections = sections,
            ),
        )
        binding.toolbar.menu.findItem(R.id.action_share_report).isEnabled = true
    }

    private fun copyReportField(field: CopyableReportField) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), field.copyValue))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.report_field_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareReport(report: ShareableReport) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, report.subject)
            putExtra(Intent.EXTRA_TEXT, report.text)
        }
        try {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.action_share_report)))
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.error_cannot_share_report, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatManifestComponents(archive: AndroidPackageArchive): String {
        val summary = archive.manifestComponents
        return buildList {
            add(
                getString(
                    if (archive.format == AndroidPackageFormat.AAB) {
                        R.string.component_manifest_heading_aab
                    } else {
                        R.string.component_manifest_heading_apk
                    },
                ),
            )
            ManifestComponentKind.entries.forEach { kind ->
                val counts = summary.countsFor(kind)
                val label = getString(
                    when (kind) {
                        ManifestComponentKind.ACTIVITY -> R.string.component_type_activity
                        ManifestComponentKind.SERVICE -> R.string.component_type_service
                        ManifestComponentKind.RECEIVER -> R.string.component_type_receiver
                        ManifestComponentKind.PROVIDER -> R.string.component_type_provider
                    },
                )
                add(
                    getString(
                        R.string.component_stats_line,
                        label,
                        counts.total,
                        counts.exported,
                        counts.notExported,
                        counts.exportedUnspecified,
                    ),
                )
            }
            if (summary.hasUnspecifiedExported) {
                add(getString(R.string.component_exported_unspecified_note))
            }
            if (summary.scanLimitReached) {
                add(
                    getString(
                        R.string.component_stats_scan_limit,
                        ManifestComponentSummaryParser.MAX_COMPONENTS_PER_MANIFEST,
                    ),
                )
            }
            if (summary.omittedManifestCount > 0) {
                add(
                    getString(
                        R.string.component_stats_manifest_limit,
                        summary.omittedManifestCount,
                        AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFESTS,
                    ),
                )
            }
            if (summary.failedManifestCount > 0) {
                add(
                    getString(
                        R.string.component_stats_manifest_failure,
                        summary.failedManifestCount,
                    ),
                )
            }
        }.joinToString("\n")
    }

    private fun formatNativeLibraries(
        summary: NativeLibrarySummary,
        readiness: PageSizeReadinessSummary,
    ): String =
        buildList {
            add(getString(R.string.native_library_heading))
            if (summary.totalLibraryCount == 0) {
                add(getString(R.string.native_library_none))
            } else {
                add(
                    getString(
                        R.string.native_library_summary,
                        summary.totalLibraryCount,
                        Formatter.formatFileSize(
                            this@ApkInspectorActivity,
                            summary.totalUncompressedBytes,
                        ),
                    ),
                )
                summary.abiGroups.forEach { group ->
                    val compatibility = getString(
                        when (group.compatibility) {
                            NativeAbiCompatibility.PREFERRED ->
                                R.string.native_library_status_preferred
                            NativeAbiCompatibility.COMPATIBLE ->
                                R.string.native_library_status_compatible
                            NativeAbiCompatibility.UNSUPPORTED ->
                                R.string.native_library_status_unsupported
                        },
                    )
                    add(
                        getString(
                            R.string.native_library_line,
                            group.abi,
                            group.libraryCount,
                            Formatter.formatFileSize(
                                this@ApkInspectorActivity,
                                group.uncompressedBytes,
                            ),
                            compatibility,
                        ),
                    )
                }
            }
            addAll(formatPageSizeReadiness(readiness))
            if (summary.omittedLibraryCount > 0) {
                add(
                    getString(
                        R.string.native_library_entry_limit,
                        summary.omittedLibraryCount,
                        NativeLibraryInspector.MAX_NATIVE_LIBRARY_ENTRIES,
                    ),
                )
            }
            if (summary.omittedAbiCount > 0) {
                add(
                    getString(
                        R.string.native_library_abi_limit,
                        summary.omittedAbiCount,
                        NativeLibraryInspector.MAX_DISPLAYED_NATIVE_ABIS,
                    ),
                )
            }
            if (summary.failedApkCount > 0 || summary.omittedApkCount > 0) {
                add(
                    getString(
                        R.string.native_library_apk_partial,
                        summary.failedApkCount,
                        summary.omittedApkCount,
                    ),
                )
            }
            if (summary.nestedScanLimitReached) {
                add(
                    getString(
                        R.string.native_library_nested_limit,
                        Formatter.formatFileSize(
                            this@ApkInspectorActivity,
                            NativeLibraryInspector.MAX_NESTED_APK_SCAN_BYTES,
                        ),
                        Formatter.formatFileSize(
                            this@ApkInspectorActivity,
                            NativeLibraryInspector
                                .MAX_NESTED_APK_CENTRAL_DIRECTORY_BYTES
                                .toLong(),
                        ),
                    ),
                )
            }
            if (summary.invalidEntryCount > 0) {
                add(
                    getString(
                        R.string.native_library_invalid_entries,
                        summary.invalidEntryCount,
                    ),
                )
            }
        }.joinToString("\n")

    private fun formatPageSizeReadiness(readiness: PageSizeReadinessSummary): List<String> =
        buildList {
            add(getString(R.string.page_size_heading))
            add(
                when (readiness.state) {
                    PageSizeReadinessState.READY ->
                        getString(R.string.page_size_state_ready, readiness.libraryCount)
                    PageSizeReadinessState.NOT_READY ->
                        getString(
                            R.string.page_size_state_not_ready,
                            readiness.unalignedCount,
                            readiness.zipMisalignedCount,
                        )
                    PageSizeReadinessState.UNVERIFIED ->
                        getString(
                            R.string.page_size_state_unverified,
                            readiness.unreadableCount,
                            readiness.omittedLibraryCount,
                        )
                    PageSizeReadinessState.NO_64BIT_LIBRARIES ->
                        getString(R.string.page_size_state_no_64bit)
                    PageSizeReadinessState.NOT_EVALUATED ->
                        getString(R.string.page_size_state_not_evaluated)
                },
            )
            readiness.abis.forEach { abi ->
                add(
                    getString(
                        R.string.page_size_line,
                        abi.abi,
                        abi.libraryCount,
                        abi.alignedCount,
                        abi.unalignedCount,
                        abi.unreadableCount,
                    ),
                )
                if (abi.examples.isNotEmpty()) {
                    add(getString(R.string.page_size_examples, abi.examples.joinToString(", ")))
                }
            }
            if (readiness.zipOffsetsChecked) {
                add(getString(R.string.page_size_zip_offsets, readiness.zipMisalignedCount))
            }
            readiness.extractNativeLibs?.let { declared ->
                add(getString(R.string.page_size_extract_native_libs, declared.toString()))
            }
            if (readiness.omittedLibraryCount > 0) {
                add(
                    getString(
                        R.string.page_size_entry_limit,
                        readiness.omittedLibraryCount,
                        PageSizeReadinessInspector.MAX_LIBRARIES,
                    ),
                )
            }
        }

    private fun formatDexFiles(summary: DexFileSummary): String =
        buildList {
            add(getString(R.string.dex_file_heading))
            if (summary.totalFileCount == 0) {
                add(getString(R.string.dex_file_none))
            } else {
                add(
                    getString(
                        R.string.dex_file_summary,
                        summary.totalFileCount,
                        Formatter.formatFileSize(
                            this@ApkInspectorActivity,
                            summary.totalUncompressedBytes,
                        ),
                    ),
                )
                summary.files.forEach { file ->
                    add(
                        getString(
                            R.string.dex_file_line,
                            file.path,
                            Formatter.formatFileSize(
                                this@ApkInspectorActivity,
                                file.uncompressedBytes,
                            ),
                        ),
                    )
                }
            }
            if (summary.omittedFileCount > 0) {
                add(
                    getString(
                        R.string.dex_file_entry_limit,
                        summary.omittedFileCount,
                        DexFileSummary.MAX_DISPLAYED_FILES,
                    ),
                )
            }
            if (summary.failedApkCount > 0 || summary.omittedApkCount > 0) {
                add(
                    getString(
                        R.string.dex_file_apk_partial,
                        summary.failedApkCount,
                        summary.omittedApkCount,
                    ),
                )
            }
            if (summary.nestedScanLimitReached) {
                add(
                    getString(
                        R.string.dex_file_nested_limit,
                        Formatter.formatFileSize(
                            this@ApkInspectorActivity,
                            NativeLibraryInspector.MAX_NESTED_APK_SCAN_BYTES,
                        ),
                        Formatter.formatFileSize(
                            this@ApkInspectorActivity,
                            NativeLibraryInspector
                                .MAX_NESTED_APK_CENTRAL_DIRECTORY_BYTES
                                .toLong(),
                        ),
                    ),
                )
            }
            if (summary.invalidEntryCount > 0) {
                add(
                    getString(
                        R.string.dex_file_invalid_entries,
                        summary.invalidEntryCount,
                    ),
                )
            }
        }.joinToString("\n")

    private fun formatRequestedPermissions(
        analysis: RequestedPermissionAnalysis,
    ): CharSequence {
        if (analysis.permissions.isEmpty() && analysis.omittedCount == 0) {
            return getString(R.string.text_none)
        }
        val output = SpannableStringBuilder()
        val runtimeColor = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorError,
            0xFFBA1A1A.toInt(),
        )

        fun appendGroup(
            group: PermissionProtectionGroup,
            titleResource: Int,
            highlight: Boolean = false,
        ) {
            val permissions = analysis.permissionsIn(group)
            if (permissions.isEmpty()) return
            if (output.isNotEmpty()) output.append("\n\n")
            val titleStart = output.length
            output.append(getString(titleResource, permissions.size))
            output.setSpan(
                StyleSpan(Typeface.BOLD),
                titleStart,
                output.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (highlight) {
                output.setSpan(
                    ForegroundColorSpan(runtimeColor),
                    titleStart,
                    output.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            permissions.forEach { permission ->
                output.append("\n- ")
                val nameStart = output.length
                output.append(permission.name)
                if (highlight) {
                    output.setSpan(
                        StyleSpan(Typeface.BOLD),
                        nameStart,
                        output.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    output.setSpan(
                        ForegroundColorSpan(runtimeColor),
                        nameStart,
                        output.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                if (group == PermissionProtectionGroup.RUNTIME) {
                    output.append("\n  ").append(
                        permission.description
                            ?: getString(R.string.permission_runtime_description_fallback),
                    )
                } else if (!permission.protectionLevelResolved) {
                    output.append("\n  ").append(
                        getString(R.string.permission_protection_unavailable),
                    )
                }
            }
        }

        appendGroup(
            PermissionProtectionGroup.RUNTIME,
            R.string.permission_group_runtime,
            highlight = true,
        )
        appendGroup(
            PermissionProtectionGroup.SIGNATURE,
            R.string.permission_group_signature,
        )
        appendGroup(
            PermissionProtectionGroup.NORMAL,
            R.string.permission_group_normal,
        )
        if (analysis.omittedCount > 0) {
            if (output.isNotEmpty()) output.append("\n\n")
            output.append(
                getString(
                    R.string.permission_omitted,
                    analysis.omittedCount,
                    PermissionProtectionAnalyzer.MAX_DISPLAYED_PERMISSIONS,
                ),
            )
        }
        return if (output.isEmpty()) getString(R.string.text_none) else output
    }

    private fun showError(message: String) {
        simulationRequestId += 1
        simulationJob?.cancel()
        simulationJob = null
        shareableReport = null
        renderedReport = null
        binding.toolbar.menu.findItem(R.id.action_share_report)?.isEnabled = false
        binding.progress.isVisible = false
        binding.content.isVisible = false
        binding.error.text = message
        binding.error.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class InspectionReport(
        val appLabel: String,
        val icon: Drawable?,
        val detailFields: List<CopyableReportField>,
        val detailSupplement: String,
        val components: String,
        val permissions: RequestedPermissionAnalysis,
        val findings: String,
        val manifestPath: String?,
    ) {
        val details: String = listOf(
            detailFields.joinToString("\n", transform = CopyableReportField::displayText),
            detailSupplement,
        ).filter(String::isNotEmpty).joinToString("\n\n")
    }

    private data class InspectionResult(
        val report: InspectionReport,
        val archive: AndroidPackageArchive,
        val deviceSpec: PackageDeviceSpec,
    )

    private data class ShareableReport(
        val subject: String,
        val text: String,
    )

    companion object {
        private const val EXTRA_PREFIX =
            "io.github.supermonster003.autojs6.plugin.apkinspector.extra."
        private const val EXTRA_FILE_PATH = "${EXTRA_PREFIX}FILE_PATH"
        private const val EXTRA_DISPLAY_NAME = "${EXTRA_PREFIX}DISPLAY_NAME"
        private const val EXTRA_BYTE_SIZE = "${EXTRA_PREFIX}BYTE_SIZE"
        private const val EXTRA_MIME_TYPE = "${EXTRA_PREFIX}MIME_TYPE"
        private const val EXTRA_SHA256 = "${EXTRA_PREFIX}SHA256"
        private const val EXTRA_V4_IDSIG_FILE_PATH = "${EXTRA_PREFIX}V4_IDSIG_FILE_PATH"
        private const val CERTIFICATE_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss 'UTC'"
        private const val FINGERPRINT_PREVIEW_CHARS = 12
        private const val LINEAGE_CAPABILITY_MASK = 0x1F
        private const val MAX_DECLARED_PERMISSION_DEFINITIONS = 2_048
        private const val MAX_FALLBACK_ICON_DIMENSION = 512
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val SIMULATION_LANGUAGE_TAGS = listOf(
            "en-US",
            "zh-CN",
            "zh-TW",
            "fr-FR",
            "es-ES",
            "ja-JP",
            "ko-KR",
            "ru-RU",
            "ar",
        )
        private val SIMULATION_DENSITIES = listOf(120, 160, 213, 240, 320, 480, 640)
        private val SIMULATION_ABIS = listOf(
            "arm64-v8a",
            "armeabi-v7a",
            "x86_64",
            "x86",
            "riscv64",
        )

        internal fun createIntent(context: Context, staged: StagedPackage): Intent =
            Intent(context, ApkInspectorActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, staged.file.absolutePath)
                putExtra(EXTRA_DISPLAY_NAME, staged.displayName)
                putExtra(EXTRA_BYTE_SIZE, staged.byteSize)
                putExtra(EXTRA_MIME_TYPE, staged.mimeType.lowercase(Locale.ROOT))
                putExtra(EXTRA_SHA256, staged.sha256)
                staged.v4IdsigFile?.let { putExtra(EXTRA_V4_IDSIG_FILE_PATH, it.absolutePath) }
            }
    }
}
