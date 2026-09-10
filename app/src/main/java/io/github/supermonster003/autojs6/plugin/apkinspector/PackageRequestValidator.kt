package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Intent
import org.autojs.plugin.explorer.api.ExplorerActionIntentValues
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import java.util.UUID

internal enum class PackageRequestRejection {
    EXPLORER_INTENT_MISSING,
    EXPLORER_ACTION,
    EXPLORER_ACTION_ID,
    EXPLORER_PROTOCOL_VERSION,
    EXPLORER_SOURCE_SURFACE,
    EXPLORER_READ_GRANT_MISSING,
    EXPLORER_FORBIDDEN_GRANT,
    EXPLORER_REQUEST_ID,
    EXPLORER_HOST_VERSION,
    EXPLORER_TARGET_URI,
    EXPLORER_PARENT_URI,
    EXPLORER_TARGET_NOT_DESCENDANT,
    EXPLORER_CLIP_DATA_MISSING,
    EXPLORER_CLIP_ITEM_COUNT,
    EXPLORER_CLIP_ITEM,
    EXPLORER_DISPLAY_NAME,
    EXPLORER_URI_NAME_MISMATCH,
    EXPLORER_SIZE_MISSING,
    EXPLORER_SIZE,
    EXPLORER_MIME,
    EXPLORER_TARGETS_MISSING,
    EXPLORER_TARGET_COUNT,
    EXPLORER_TARGET_ID,
    EXPLORER_TARGET_URI_MISMATCH,
    EXPLORER_TARGET_NAME_MISMATCH,
    EXPLORER_TARGET_KIND,
    EXPLORER_TARGET_MIME_MISMATCH,
    EXPLORER_TARGET_SIZE_MISSING,
    EXPLORER_TARGET_SIZE_MISMATCH,
    EXPLORER_TARGET_LAST_MODIFIED_MISSING,
    EXPLORER_HOST_SESSION,
    EXTERNAL_INTENT_MISSING,
    EXTERNAL_ACTION,
    EXTERNAL_READ_GRANT_MISSING,
    EXTERNAL_FORBIDDEN_GRANT,
    EXTERNAL_TARGET_URI,
    EXTERNAL_MIME,
}

internal data class PackageUriFacts(
    val identity: String,
    val isHierarchical: Boolean,
    val scheme: String?,
    val authority: String?,
    val host: String?,
    val userInfo: String?,
    val port: Int,
    val query: String?,
    val fragment: String?,
    val encodedPath: String?,
    val pathSegments: List<String>,
)

internal data class ExplorerClipItemFacts(
    val uri: PackageUriFacts?,
    val hasText: Boolean,
    val hasHtmlText: Boolean,
    val hasIntent: Boolean,
)

internal data class ExplorerTargetFacts(
    val id: String?,
    val uri: PackageUriFacts?,
    val displayName: String?,
    val kind: Int,
    val mimeType: String?,
    val sizePresent: Boolean,
    val size: Long,
    val lastModifiedPresent: Boolean,
)

internal data class ExplorerRequestFacts(
    val action: String?,
    val actionId: String?,
    val protocolVersion: Int,
    val sourceSurface: String?,
    val flags: Int,
    val requestId: String?,
    val hostVersionPresent: Boolean,
    val hostVersion: Long,
    val targetUri: PackageUriFacts?,
    val parentUri: PackageUriFacts?,
    val clipDataPresent: Boolean,
    val clipItemCount: Int,
    val clipItem: ExplorerClipItemFacts?,
    val displayName: String?,
    val sizePresent: Boolean,
    val declaredSize: Long,
    val mimeType: String?,
    val targetsPresent: Boolean,
    val targetCount: Int,
    val target: ExplorerTargetFacts?,
    val hostSessionPresent: Boolean,
)

internal data class ExternalRequestFacts(
    val action: String?,
    val flags: Int,
    val targetUri: PackageUriFacts?,
    val mimeType: String?,
)

internal object PackageRequestValidator {

    fun rejectExplorer(request: ExplorerRequestFacts?): PackageRequestRejection? {
        request ?: return PackageRequestRejection.EXPLORER_INTENT_MISSING
        if (request.action != ExplorerActionPluginActions.EXECUTE) {
            return PackageRequestRejection.EXPLORER_ACTION
        }
        if (!ApkInspectorPlugin.acceptsActionId(request.actionId)) {
            return PackageRequestRejection.EXPLORER_ACTION_ID
        }
        if (request.protocolVersion != ExplorerActionProtocol.VERSION) {
            return PackageRequestRejection.EXPLORER_PROTOCOL_VERSION
        }
        if (request.sourceSurface != ExplorerActionIntentValues.SOURCE_SURFACE_MAIN) {
            return PackageRequestRejection.EXPLORER_SOURCE_SURFACE
        }
        if (request.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            return PackageRequestRejection.EXPLORER_READ_GRANT_MISSING
        }
        if (request.flags and FORBIDDEN_EXPLORER_GRANTS != 0) {
            return PackageRequestRejection.EXPLORER_FORBIDDEN_GRANT
        }
        if (!isCanonicalUuid(request.requestId)) {
            return PackageRequestRejection.EXPLORER_REQUEST_ID
        }
        if (!request.hostVersionPresent || request.hostVersion < ApkInspectorPlugin.REQUIRED_HOST_VERSION) {
            return PackageRequestRejection.EXPLORER_HOST_VERSION
        }

        val targetUri = request.targetUri?.takeIf(::isPlainContentUri)
            ?: return PackageRequestRejection.EXPLORER_TARGET_URI
        val parentUri = request.parentUri?.takeIf(::isPlainContentUri)
            ?: return PackageRequestRejection.EXPLORER_PARENT_URI
        if (!isStrictDescendant(parentUri, targetUri)) {
            return PackageRequestRejection.EXPLORER_TARGET_NOT_DESCENDANT
        }
        if (!request.clipDataPresent) return PackageRequestRejection.EXPLORER_CLIP_DATA_MISSING
        if (request.clipItemCount != REQUIRED_EXPLORER_CLIP_ITEM_COUNT) {
            return PackageRequestRejection.EXPLORER_CLIP_ITEM_COUNT
        }
        if (!request.clipItem.isExactUri(targetUri)) {
            return PackageRequestRejection.EXPLORER_CLIP_ITEM
        }

        val displayName = PackageRequestPolicy.validateDisplayName(request.displayName)
            ?: return PackageRequestRejection.EXPLORER_DISPLAY_NAME
        if (targetUri.pathSegments.lastOrNull() != displayName) {
            return PackageRequestRejection.EXPLORER_URI_NAME_MISMATCH
        }
        if (!request.sizePresent) return PackageRequestRejection.EXPLORER_SIZE_MISSING
        if (!PackageRequestPolicy.isDeclaredSizeAccepted(request.declaredSize)) {
            return PackageRequestRejection.EXPLORER_SIZE
        }
        val mimeType = PackageRequestPolicy.normalizeMimeType(request.mimeType)
            ?.takeIf(PackageRequestPolicy::isExplorerMimeAccepted)
            ?: return PackageRequestRejection.EXPLORER_MIME

        if (!request.targetsPresent) return PackageRequestRejection.EXPLORER_TARGETS_MISSING
        if (request.targetCount != 1) return PackageRequestRejection.EXPLORER_TARGET_COUNT
        val target = request.target ?: return PackageRequestRejection.EXPLORER_TARGET_COUNT
        if (!isValidTargetId(target.id)) return PackageRequestRejection.EXPLORER_TARGET_ID
        if (target.uri != targetUri) return PackageRequestRejection.EXPLORER_TARGET_URI_MISMATCH
        if (target.displayName != displayName) {
            return PackageRequestRejection.EXPLORER_TARGET_NAME_MISMATCH
        }
        if (target.kind != ExplorerActionValues.TARGET_FILE) {
            return PackageRequestRejection.EXPLORER_TARGET_KIND
        }
        if (PackageRequestPolicy.normalizeMimeType(target.mimeType) != mimeType) {
            return PackageRequestRejection.EXPLORER_TARGET_MIME_MISMATCH
        }
        if (!target.sizePresent) return PackageRequestRejection.EXPLORER_TARGET_SIZE_MISSING
        if (target.size != request.declaredSize) {
            return PackageRequestRejection.EXPLORER_TARGET_SIZE_MISMATCH
        }
        if (!target.lastModifiedPresent) {
            return PackageRequestRejection.EXPLORER_TARGET_LAST_MODIFIED_MISSING
        }
        if (!request.hostSessionPresent) return PackageRequestRejection.EXPLORER_HOST_SESSION
        return null
    }

    fun rejectExternal(request: ExternalRequestFacts?): PackageRequestRejection? {
        request ?: return PackageRequestRejection.EXTERNAL_INTENT_MISSING
        if (request.action != Intent.ACTION_VIEW) return PackageRequestRejection.EXTERNAL_ACTION
        if (request.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            return PackageRequestRejection.EXTERNAL_READ_GRANT_MISSING
        }
        if (request.flags and FORBIDDEN_EXTERNAL_GRANTS != 0) {
            return PackageRequestRejection.EXTERNAL_FORBIDDEN_GRANT
        }
        if (request.targetUri?.takeIf(::isPlainContentUri) == null) {
            return PackageRequestRejection.EXTERNAL_TARGET_URI
        }
        if (
            PackageRequestPolicy.normalizeMimeType(request.mimeType)
                ?.takeIf(PackageRequestPolicy.supportedExternalMimeTypes::contains) == null
        ) {
            return PackageRequestRejection.EXTERNAL_MIME
        }
        return null
    }

    internal fun isPlainContentUri(uri: PackageUriFacts): Boolean {
        if (!uri.isHierarchical || uri.scheme != CONTENT_SCHEME) return false
        if (uri.authority.isNullOrBlank() || uri.host.isNullOrBlank()) return false
        if (uri.userInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null) return false
        val encodedPath = uri.encodedPath ?: return false
        if (!encodedPath.startsWith('/') || encodedPath.length <= 1) return false
        if (encodedPath.split('/').drop(1).any(String::isEmpty)) return false
        return uri.pathSegments.isNotEmpty() && uri.pathSegments.none { segment ->
            segment.isEmpty() || segment == "." || segment == ".." || segment.any(::isUnsafeUriCharacter)
        }
    }

    internal fun isStrictDescendant(parentUri: PackageUriFacts, targetUri: PackageUriFacts): Boolean {
        if (parentUri.scheme != targetUri.scheme || parentUri.authority != targetUri.authority) return false
        val parentSegments = parentUri.pathSegments
        val targetSegments = targetUri.pathSegments
        return targetSegments.size > parentSegments.size &&
            targetSegments.take(parentSegments.size) == parentSegments
    }

    private fun ExplorerClipItemFacts?.isExactUri(expected: PackageUriFacts): Boolean =
        this != null && uri == expected && !hasText && !hasHtmlText && !hasIntent

    private fun isValidTargetId(value: String?): Boolean =
        value != null &&
            value.length in 1..ExplorerActionProtocol.MAX_TARGET_ID_LENGTH &&
            value.none { it.isWhitespace() || it.isISOControl() }

    private fun isCanonicalUuid(value: String?): Boolean {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull() ?: return false
        return parsed.toString().equals(value, ignoreCase = true)
    }

    private fun isUnsafeUriCharacter(character: Char): Boolean =
        character == '/' || character == '\\' || isUnsafeUnicodeCharacter(character)

    private fun isUnsafeUnicodeCharacter(character: Char): Boolean =
        character.isISOControl() || Character.getType(character) == Character.FORMAT.toInt()

    private const val CONTENT_SCHEME = "content"
    private const val REQUIRED_EXPLORER_CLIP_ITEM_COUNT = 1
    private const val FORBIDDEN_EXPLORER_GRANTS =
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    private const val FORBIDDEN_EXTERNAL_GRANTS =
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
}
