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
import java.util.Locale

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
                    inspect(packageFile, displayName, byteSize, mimeType, sha256)
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
        val signatureSchemes: String?
        try {
            packageInfo = displayApk?.let(::getPackageInfo)
            val applicationInfo = packageInfo?.applicationInfo?.apply {
                sourceDir = displayApk.absolutePath
                publicSourceDir = displayApk.absolutePath
            }
            label = runCatching { applicationInfo?.loadLabel(packageManager)?.toString() }.getOrNull()
            icon = runCatching { applicationInfo?.loadIcon(packageManager) }.getOrNull()
            signatureSchemes = displayApk?.let { runCatching { ApkSignatureDetector.detectSchemes(it) }.getOrNull() }
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
        val details = listOf(
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
            getString(R.string.detail_signature, signatureSchemes ?: unknown),
            getString(R.string.detail_size, Formatter.formatFileSize(this, byteSize)),
            getString(R.string.detail_sha256, sha256),
        ).joinToString("\n")

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
        packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS,
        )
    }.getOrNull()

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
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")

        internal fun createIntent(context: Context, staged: StagedPackage): Intent =
            Intent(context, ApkInspectorActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, staged.file.absolutePath)
                putExtra(EXTRA_DISPLAY_NAME, staged.displayName)
                putExtra(EXTRA_BYTE_SIZE, staged.byteSize)
                putExtra(EXTRA_MIME_TYPE, staged.mimeType.lowercase(Locale.ROOT))
                putExtra(EXTRA_SHA256, staged.sha256)
            }
    }
}
