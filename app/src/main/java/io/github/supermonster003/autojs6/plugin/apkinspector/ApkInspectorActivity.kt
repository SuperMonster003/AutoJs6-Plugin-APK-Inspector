package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import io.github.supermonster003.autojs6.plugin.apkinspector.databinding.ActivityApkInspectorBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ApkInspectorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApkInspectorBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApkInspectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

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
        binding.toolbar.title = displayName
        binding.fileSummary.text = listOf(
            getString(R.string.detail_size, Formatter.formatFileSize(this, byteSize)),
            getString(R.string.detail_mime, mimeType),
        ).joinToString("\n")
        binding.viewManifest.isEnabled = false

        scope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    inspect(
                        packageFile,
                        displayName,
                        byteSize,
                        mimeType,
                        sha256,
                        v4IdsigFile,
                    )
                }
                render(report)
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

    private fun inspect(
        packageFile: File,
        displayName: String,
        byteSize: Long,
        mimeType: String,
        sha256: String,
        v4IdsigFile: File?,
    ): InspectionReport {
        val archive = AndroidPackageArchiveInspector.inspect(packageFile, PackageDeviceSpec.from(this))
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

        val versionCode = packageInfo?.let { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } ?: summary?.versionCode
        val requestedPermissions = (
            packageInfo?.requestedPermissions.orEmpty().asList() + summary?.requestedPermissions.orEmpty()
        ).filter(String::isNotBlank).distinct().sorted()
        val manifestPath = runCatching {
            val xml = archive.decodeDisplayManifest()
            File(packageFile.parentFile, "manifest.xml").apply {
                writeText(xml, Charsets.UTF_8)
                if (!setReadOnly()) error("Unable to protect manifest snapshot")
            }.absolutePath
        }.getOrNull()

        val unknown = getString(R.string.text_unknown)
        val appLabel = label ?: summary?.applicationLabel ?: displayName
        val detailLines = listOf(
            getString(R.string.detail_file, displayName),
            getString(R.string.detail_format, formatName(archive)),
            getString(R.string.detail_label, appLabel),
            getString(R.string.detail_package_name, packageInfo?.packageName ?: summary?.packageName ?: unknown),
            getString(
                R.string.detail_version,
                packageInfo?.versionName ?: summary?.versionName ?: unknown,
                versionCode?.toString() ?: unknown,
            ),
            getString(
                R.string.detail_sdk,
                packageInfo?.applicationInfo?.minSdkVersion?.takeIf { it > 0 }?.toString()
                    ?: summary?.minSdk?.toString() ?: unknown,
                packageInfo?.applicationInfo?.targetSdkVersion?.takeIf { it > 0 }?.toString()
                    ?: summary?.targetSdk?.toString() ?: unknown,
                summary?.maxSdk?.toString() ?: unknown,
            ),
            getString(R.string.detail_device_sdk, Build.VERSION.SDK_INT),
            getString(
                R.string.detail_signature,
                signatureVerification?.let(::formatSignatureVerification) ?: unknown,
            ),
            getString(
                R.string.detail_signing_certificates,
                signingCertificates.size.takeIf { it > 0 }?.toString() ?: unknown,
            ),
            getString(R.string.detail_size, Formatter.formatFileSize(this, byteSize)),
            getString(R.string.detail_sha256, sha256),
        ).joinToString("\n")
        val certificateDetails = formatSigningCertificates(signingCertificates)
        val lineageDetails = signatureVerification?.lineage
            ?.let(::formatSigningCertificateLineage)
            .orEmpty()
        val details = listOf(detailLines, certificateDetails, lineageDetails)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")

        val componentEntries = when (archive.format) {
            AndroidPackageFormat.AAB -> archive.aabModules.joinToString("\n") { "- $it" }
            else -> archive.selectedApks.joinToString("\n") { apk ->
                val split = apk.manifest.splitName?.takeIf(String::isNotBlank) ?: "base"
                "- $split | ${apk.archivePath} | ${Formatter.formatFileSize(this, apk.size)}"
            }
        }.ifBlank { getString(R.string.text_none) }
        val components = when (archive.format) {
            AndroidPackageFormat.AAB -> getString(R.string.components_aab, archive.aabModules.size, componentEntries)
            else -> getString(
                R.string.components_apk,
                archive.apkEntryCount,
                archive.selectedApks.size,
                archive.obbEntries.size,
                componentEntries,
            )
        }

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
        }.distinct().ifEmpty { listOf(getString(R.string.finding_none)) }.joinToString("\n")

        return InspectionReport(
            appLabel = appLabel,
            icon = icon,
            details = details,
            components = components,
            permissions = requestedPermissions.joinToString("\n") { "- $it" }
                .ifBlank { getString(R.string.text_none) },
            findings = findings,
            manifestPath = manifestPath,
        )
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

    private fun render(report: InspectionReport) {
        binding.progress.isVisible = false
        binding.error.isVisible = false
        binding.content.isVisible = true
        binding.appLabel.text = report.appLabel
        report.icon?.let(binding.appIcon::setImageDrawable)
        binding.packageDetails.text = report.details
        binding.components.text = report.components
        binding.permissions.text = report.permissions
        binding.findings.text = report.findings
        binding.viewManifest.isVisible = report.manifestPath != null
        binding.viewManifest.isEnabled = report.manifestPath != null
        binding.viewManifest.setOnClickListener {
            report.manifestPath?.let { path -> startActivity(ManifestViewerActivity.createIntent(this, path)) }
        }
    }

    private fun showError(message: String) {
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
        val details: String,
        val components: String,
        val permissions: String,
        val findings: String,
        val manifestPath: String?,
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
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")

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
