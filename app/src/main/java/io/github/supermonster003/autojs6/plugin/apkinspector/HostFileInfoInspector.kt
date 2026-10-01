package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.PackageDeviceSpec
import org.autojs.plugin.packagearchive.AndroidPackageFormat

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerHostFileInfoKeys
import org.autojs.plugin.explorer.api.ExplorerHostFileInfoValues
import java.io.File

internal object HostFileInfoInspector {

    fun inspect(context: Context, staged: StagedPackage): Bundle {
        val archive = AndroidPackageArchiveInspector.inspect(
            staged.file,
            PackageDeviceSpec.from(context),
        )
        val displayApk = when (archive.format) {
            AndroidPackageFormat.APK -> staged.file
            AndroidPackageFormat.AAB -> null
            else -> archive.createDisplayApk(context.cacheDir)
        }
        val temporaryDirectory = displayApk?.takeUnless { it == staged.file }?.parentFile
        val packageInfo: PackageInfo?
        val signatureVerification: ApkSignatureVerification?
        try {
            packageInfo = displayApk?.let { apk -> getPackageInfo(context, apk) }
            signatureVerification = displayApk?.let { apk ->
                runCatching { ApkSignatureVerifier.verify(apk) }.getOrNull()
            }
        } finally {
            temporaryDirectory?.deleteRecursively()
        }

        val manifest = archive.baseManifest
        val requestedPermissions =
            packageInfo?.requestedPermissions.orEmpty().asList() + manifest?.requestedPermissions.orEmpty()
        val declaredProtectionLevels = buildMap {
            manifest?.declaredPermissionProtectionLevels.orEmpty().entries.asSequence()
                .take(MAX_DECLARED_PERMISSION_DEFINITIONS)
                .forEach { (name, protectionLevel) -> put(name, protectionLevel) }
            packageInfo?.permissions.orEmpty().asSequence()
                .take(MAX_DECLARED_PERMISSION_DEFINITIONS)
                .forEach { permission ->
                    permission.name?.takeIf(String::isNotBlank)?.let { name ->
                        put(name, protectionLevel(permission))
                    }
                }
        }
        val permissionAnalysis = PermissionProtectionAnalyzer.analyze(
            requestedPermissions = requestedPermissions,
            declaredProtectionLevels = declaredProtectionLevels,
            resolvePermission = { name -> resolvePermission(context, name) },
        )
        val summary = formatSummary(
            context = context,
            archive = archive,
            signatureVerification = signatureVerification,
            permissionAnalysis = permissionAnalysis,
            sha256 = staged.sha256,
        )
        return Bundle().apply {
            putInt(ExplorerHostFileInfoKeys.VERSION, ExplorerActionProtocol.HOST_FILE_INFO_VERSION)
            putInt(ExplorerHostFileInfoKeys.ERROR_CODE, ExplorerHostFileInfoValues.ERROR_NONE)
            putString(ExplorerHostFileInfoKeys.TITLE, context.getString(R.string.inspection_title))
            putString(ExplorerHostFileInfoKeys.SUMMARY, summary)
            putString(ExplorerHostFileInfoKeys.SOURCE_SHA256, staged.sha256)
        }
    }

    private fun formatSummary(
        context: Context,
        archive: AndroidPackageArchive,
        signatureVerification: ApkSignatureVerification?,
        permissionAnalysis: RequestedPermissionAnalysis,
        sha256: String,
    ): String {
        val lines = buildList {
            signatureVerification?.let { verification ->
                add(
                    context.getString(
                        R.string.detail_signature,
                        formatSignatureVerification(context, verification),
                    ),
                )
            }
            val components = archive.manifestComponents
            add(
                context.getString(
                    if (archive.format == AndroidPackageFormat.AAB) {
                        R.string.component_manifest_heading_aab
                    } else {
                        R.string.component_manifest_heading_apk
                    },
                ),
            )
            ManifestComponentKind.entries.forEach { kind ->
                val counts = components.countsFor(kind)
                add(
                    context.getString(
                        R.string.component_stats_line,
                        context.getString(componentLabel(kind)),
                        counts.total,
                        counts.exported,
                        counts.notExported,
                        counts.exportedUnspecified,
                    ),
                )
            }
            add(formatPermissionCounts(context, permissionAnalysis))
            add(formatNativeLibraries(context, archive.nativeLibraries, archive.pageSizeReadiness))
            add(formatDexFiles(context, archive.dexFiles))
            add(
                "${context.getString(R.string.section_findings)}: " +
                    formatFindings(context, archive),
            )
            add(context.getString(R.string.detail_sha256, sha256))
        }
        return boundSummary(lines)
    }

    private fun formatSignatureVerification(
        context: Context,
        verification: ApkSignatureVerification,
    ): String = buildList {
        if (verification.hasV1) {
            add("V1 — ${context.getString(R.string.signature_state_present)}")
        }
        verification.v2?.let { add(formatScheme(context, "V2", it)) }
        verification.v3?.let { add(formatScheme(context, "V3", it)) }
        verification.v31?.let { add(formatScheme(context, "V3.1", it)) }
        verification.v4?.let { add(formatScheme(context, "V4", it)) }
    }.joinToString(" | ").ifBlank { context.getString(R.string.text_none) }

    private fun formatScheme(
        context: Context,
        scheme: String,
        verification: ApkSchemeVerification,
    ): String {
        val reason = buildString {
            append(verification.reason?.name ?: context.getString(R.string.text_unknown))
            verification.detail?.takeIf(String::isNotBlank)?.let { detail ->
                append(": ").append(detail)
            }
        }
        val conclusion = when (verification.state) {
            ApkSignatureVerificationState.PRESENT -> verification.reason?.let {
                context.getString(R.string.signature_state_present_reason, reason)
            } ?: context.getString(R.string.signature_state_present)
            ApkSignatureVerificationState.VERIFIED -> context.getString(
                R.string.signature_state_verified,
                verification.signerCount,
            )
            ApkSignatureVerificationState.FAILED -> context.getString(
                R.string.signature_state_failed,
                reason,
            )
        }
        return "$scheme — $conclusion"
    }

    private fun formatPermissionCounts(
        context: Context,
        analysis: RequestedPermissionAnalysis,
    ): String {
        val groups = listOf(
            PermissionProtectionGroup.RUNTIME to R.string.permission_group_runtime,
            PermissionProtectionGroup.SIGNATURE to R.string.permission_group_signature,
            PermissionProtectionGroup.NORMAL to R.string.permission_group_normal,
        ).joinToString(" | ") { (group, label) ->
            context.getString(label, analysis.permissionsIn(group).size)
        }
        return "${context.getString(R.string.section_requested_permissions)}: $groups"
    }

    private fun formatNativeLibraries(
        context: Context,
        summary: NativeLibrarySummary,
        readiness: PageSizeReadinessSummary,
    ): String {
        val summaryText = if (summary.totalLibraryCount == 0) {
            context.getString(R.string.native_library_none)
        } else {
            context.getString(
                R.string.native_library_summary,
                summary.totalLibraryCount,
                Formatter.formatFileSize(context, summary.totalUncompressedBytes),
            )
        }
        val abis = summary.abiGroups.take(MAX_SUMMARY_ABIS).joinToString(", ") { group ->
            val status = when (group.compatibility) {
                NativeAbiCompatibility.PREFERRED -> R.string.native_library_status_preferred
                NativeAbiCompatibility.COMPATIBLE -> R.string.native_library_status_compatible
                NativeAbiCompatibility.UNSUPPORTED -> R.string.native_library_status_unsupported
            }
            "${group.abi} (${context.getString(status)})"
        }
        val pageSize = when (readiness.state) {
            PageSizeReadinessState.READY -> context.getString(R.string.page_size_short_ready)
            PageSizeReadinessState.NOT_READY -> context.getString(
                R.string.page_size_short_not_ready,
                readiness.unalignedCount + readiness.zipMisalignedCount,
            )
            PageSizeReadinessState.UNVERIFIED -> context.getString(R.string.page_size_short_unverified)
            PageSizeReadinessState.NO_64BIT_LIBRARIES -> context.getString(R.string.page_size_short_no_64bit)
            PageSizeReadinessState.NOT_EVALUATED -> context.getString(R.string.page_size_short_not_evaluated)
        }
        return buildString {
            append(context.getString(R.string.native_library_heading)).append(": ").append(summaryText)
            if (abis.isNotEmpty()) append(" | ").append(abis)
            append(" | ").append(pageSize)
        }
    }

    private fun formatDexFiles(context: Context, summary: DexFileSummary): String {
        val detail = if (summary.totalFileCount == 0) {
            context.getString(R.string.dex_file_none)
        } else {
            context.getString(
                R.string.dex_file_summary,
                summary.totalFileCount,
                Formatter.formatFileSize(context, summary.totalUncompressedBytes),
            )
        }
        return "${context.getString(R.string.dex_file_heading)}: $detail"
    }

    private fun formatFindings(context: Context, archive: AndroidPackageArchive): String {
        val status = when (archive.inspectionState) {
            ArchiveInspectionState.COMPATIBLE -> context.getString(R.string.finding_none)
            ArchiveInspectionState.COMPATIBLE_WITH_OBB -> context.getString(
                R.string.finding_obb,
                archive.obbEntries.size,
            )
            ArchiveInspectionState.AAB_SOURCE -> context.getString(R.string.finding_aab_conversion)
            ArchiveInspectionState.INCOMPATIBLE -> context.getString(R.string.finding_incompatible)
            ArchiveInspectionState.INVALID -> context.getString(R.string.finding_invalid)
        }
        val problemCodes = archive.problems.asSequence()
            .map { problem -> problem.code.name }
            .distinct()
            .take(MAX_SUMMARY_PROBLEM_CODES)
            .toList()
        return if (problemCodes.isEmpty()) status else "$status [${problemCodes.joinToString()}]"
    }

    private fun componentLabel(kind: ManifestComponentKind): Int = when (kind) {
        ManifestComponentKind.ACTIVITY -> R.string.component_type_activity
        ManifestComponentKind.SERVICE -> R.string.component_type_service
        ManifestComponentKind.RECEIVER -> R.string.component_type_receiver
        ManifestComponentKind.PROVIDER -> R.string.component_type_provider
    }

    private fun getPackageInfo(context: Context, apkFile: File): PackageInfo? = runCatching {
        context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS,
        )
    }.getOrNull()

    private fun resolvePermission(context: Context, name: String): PermissionDefinition {
        @Suppress("DEPRECATION")
        val permission = context.packageManager.getPermissionInfo(name, 0)
        return PermissionDefinition(protectionLevel(permission))
    }

    private fun protectionLevel(permission: PermissionInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permission.protection
        } else {
            @Suppress("DEPRECATION")
            permission.protectionLevel
        }

    private fun boundSummary(sourceLines: List<String>): String {
        val result = ArrayList<String>()
        var totalLength = 0
        sourceLines.asSequence()
            .flatMap { value -> value.replace("\r\n", "\n").replace('\r', '\n').split('\n').asSequence() }
            .map { line ->
                line.map { character ->
                    if (
                        character.isISOControl() ||
                        Character.getType(character) == Character.FORMAT.toInt()
                    ) {
                        ' '
                    } else {
                        character
                    }
                }.joinToString("").replace(HORIZONTAL_WHITESPACE, " ").trim()
            }
            .filter(String::isNotEmpty)
            .take(ExplorerActionProtocol.MAX_HOST_FILE_INFO_SUMMARY_LINES)
            .forEach { line ->
                val boundedLine = line.take(ExplorerActionProtocol.MAX_HOST_FILE_INFO_SUMMARY_LINE_LENGTH)
                val addedLength = boundedLine.length + if (result.isEmpty()) 0 else 1
                if (totalLength + addedLength <= ExplorerActionProtocol.MAX_HOST_FILE_INFO_SUMMARY_LENGTH) {
                    result += boundedLine
                    totalLength += addedLength
                }
            }
        return result.joinToString("\n").ifBlank { throw IllegalStateException("Empty host summary") }
    }

    private const val MAX_DECLARED_PERMISSION_DEFINITIONS = 2_048
    private const val MAX_SUMMARY_ABIS = 8
    private const val MAX_SUMMARY_PROBLEM_CODES = 8
    private val HORIZONTAL_WHITESPACE = Regex("[\\t\\x0B\\f ]+")
}
