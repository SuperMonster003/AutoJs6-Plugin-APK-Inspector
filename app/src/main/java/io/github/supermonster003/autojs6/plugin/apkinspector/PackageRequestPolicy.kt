package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionIntentValues
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import java.util.Locale

internal data class PackageInputSeed(
    val targetUri: Uri,
    val displayName: String?,
    val declaredSize: Long?,
    val mimeType: String,
    val fromExplorer: Boolean,
)

/** Validates every value crossing the exported Explorer Action and ACTION_VIEW boundaries. */
internal object PackageRequestPolicy {

    const val MAX_PACKAGE_BYTES = 4L * 1024L * 1024L * 1024L
    const val MAX_DISPLAY_NAME_LENGTH = 255

    private val mimeTokenPattern = Regex("[a-z0-9][a-z0-9!#$&^_.+-]*")

    private val mimeTypesByExtension = mapOf(
        "apk" to setOf("application/vnd.android.package-archive"),
        "apks" to setOf("application/x-apks"),
        "apkm" to setOf("application/vnd.apkm"),
        "xapk" to setOf("application/xapk-package-archive"),
        "apkz" to setOf("application/x-apkz"),
        "aab" to setOf("application/x-aab", "application/vnd.android.aab"),
    )

    val supportedExtensions: Set<String> = mimeTypesByExtension.keys
    val supportedExternalMimeTypes: Set<String> = mimeTypesByExtension.values.flatten().toSet()

    fun resolveExplorer(intent: Intent?): PackageInputSeed? = try {
        intent ?: return null
        if (intent.action != ExplorerActionPluginActions.EXECUTE) return null
        if (intent.getStringExtra(ExplorerActionIntentExtras.ACTION_ID) != ApkInspectorPlugin.ACTION_ID) {
            return null
        }
        if (
            intent.getIntExtra(ExplorerActionIntentExtras.PROTOCOL_VERSION, Int.MIN_VALUE) !=
            ExplorerActionProtocol.VERSION
        ) {
            return null
        }
        if (
            intent.getStringExtra(ExplorerActionIntentExtras.SOURCE_SURFACE) !=
            ExplorerActionIntentValues.SOURCE_SURFACE_MAIN
        ) {
            return null
        }
        if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return null
        if (intent.flags and FORBIDDEN_EXPLORER_GRANTS != 0) return null

        val targetUri = intent.data?.takeIf(::isPlainContentUri) ?: return null
        val parentUri = intent.parcelableUriExtra(ExplorerActionIntentExtras.PARENT_URI)
            ?.takeIf(::isPlainContentUri)
            ?: return null
        if (!isStrictDescendant(parentUri, targetUri)) return null

        val clipData = intent.clipData ?: return null
        if (clipData.itemCount != REQUIRED_EXPLORER_CLIP_ITEM_COUNT) return null
        if (!clipData.getItemAt(ExplorerActionIntentValues.CLIP_ITEM_TARGET_INDEX).isExactUri(targetUri)) {
            return null
        }
        if (!clipData.getItemAt(ExplorerActionIntentValues.CLIP_ITEM_PARENT_INDEX).isExactUri(parentUri)) {
            return null
        }

        val displayName = validateDisplayName(
            intent.getStringExtra(ExplorerActionIntentExtras.DISPLAY_NAME),
        ) ?: return null
        if (targetUri.pathSegments.lastOrNull() != displayName) return null
        if (!intent.hasExtra(ExplorerActionIntentExtras.SIZE)) return null
        val declaredSize = intent.getLongExtra(ExplorerActionIntentExtras.SIZE, -1L)
            .takeIf(::isDeclaredSizeAccepted) ?: return null
        val mimeType = normalizeMimeType(intent.type) ?: return null
        if (!isExplorerMimeAccepted(mimeType)) return null

        PackageInputSeed(targetUri, displayName, declaredSize, mimeType, true)
    } catch (_: RuntimeException) {
        null
    }

    fun resolveExternal(intent: Intent?): PackageInputSeed? = try {
        intent ?: return null
        if (intent.action != Intent.ACTION_VIEW) return null
        if (!hasReadOnlyExternalGrant(intent)) return null
        val targetUri = intent.data?.takeIf(::isPlainContentUri) ?: return null
        val mimeType = normalizeMimeType(intent.type)
            ?.takeIf(supportedExternalMimeTypes::contains)
            ?: return null
        PackageInputSeed(targetUri, null, null, mimeType, false)
    } catch (_: RuntimeException) {
        null
    }

    fun normalizeMimeType(value: String?): String? {
        val raw = value ?: return null
        if (raw.isEmpty() || raw != raw.trim() || ';' in raw) return null
        val normalized = raw.lowercase(Locale.ROOT)
        if (normalized != raw) return null
        val parts = normalized.split('/')
        if (parts.size != 2) return null
        if (parts[0] == "*" && parts[1] == "*") return normalized
        if (!mimeTokenPattern.matches(parts[0]) || !mimeTokenPattern.matches(parts[1])) return null
        return normalized
    }

    fun validateDisplayName(value: String?): String? {
        val name = value ?: return null
        if (name.length !in 1..MAX_DISPLAY_NAME_LENGTH || name.isBlank()) return null
        if (name == "." || name == ".." || name.any(::isUnsafeNameCharacter)) return null
        return name.takeIf { extensionOf(it) in supportedExtensions }
    }

    fun extensionOf(displayName: String): String =
        displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun isDeclaredSizeAccepted(size: Long): Boolean = size in 0L..MAX_PACKAGE_BYTES

    fun isExternalMimeCompatible(displayName: String, mimeType: String): Boolean {
        val extension = extensionOf(displayName)
        val normalizedMime = normalizeMimeType(mimeType) ?: return false
        return normalizedMime in mimeTypesByExtension[extension].orEmpty()
    }

    fun mimeTypesAreCompatible(declared: String, resolved: String, fromExplorer: Boolean): Boolean {
        val normalizedDeclared = normalizeMimeType(declared) ?: return false
        val normalizedResolved = normalizeMimeType(resolved) ?: return false
        if (fromExplorer && isExplorerMimeAccepted(normalizedDeclared)) {
            return normalizedResolved in supportedExternalMimeTypes ||
                normalizedResolved == GENERIC_BINARY_MIME ||
                normalizedResolved == GENERIC_ZIP_MIME
        }
        return normalizedDeclared == normalizedResolved
    }

    private fun isExplorerMimeAccepted(mimeType: String): Boolean {
        return mimeType in supportedExternalMimeTypes ||
            mimeType == GLOBAL_WILDCARD_MIME ||
            mimeType == GENERIC_BINARY_MIME ||
            mimeType == GENERIC_ZIP_MIME
    }

    private fun hasReadOnlyExternalGrant(intent: Intent): Boolean {
        if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return false
        return intent.flags and FORBIDDEN_EXTERNAL_GRANTS == 0
    }

    private fun isPlainContentUri(uri: Uri): Boolean {
        if (!uri.isHierarchical || uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        if (uri.authority.isNullOrBlank() || uri.host.isNullOrBlank()) return false
        if (uri.userInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null) return false
        val encodedPath = uri.encodedPath ?: return false
        if (!encodedPath.startsWith('/') || encodedPath.length <= 1) return false
        if (encodedPath.split('/').drop(1).any(String::isEmpty)) return false
        return uri.pathSegments.isNotEmpty() && uri.pathSegments.none { segment ->
            segment.isEmpty() || segment == "." || segment == ".." || segment.any(::isUnsafeUriCharacter)
        }
    }

    private fun isStrictDescendant(parentUri: Uri, targetUri: Uri): Boolean {
        if (parentUri.scheme != targetUri.scheme || parentUri.authority != targetUri.authority) return false
        val parentSegments = parentUri.pathSegments
        val targetSegments = targetUri.pathSegments
        return targetSegments.size > parentSegments.size &&
            targetSegments.take(parentSegments.size) == parentSegments
    }

    private fun ClipData.Item.isExactUri(expected: Uri): Boolean =
        uri == expected && text == null && htmlText == null && intent == null

    private fun isUnsafeNameCharacter(character: Char): Boolean =
        character == '/' || character == '\\' || isUnsafeUnicodeCharacter(character)

    private fun isUnsafeUriCharacter(character: Char): Boolean =
        character == '/' || character == '\\' || isUnsafeUnicodeCharacter(character)

    private fun isUnsafeUnicodeCharacter(character: Char): Boolean =
        character.isISOControl() || Character.getType(character) == Character.FORMAT.toInt()

    @Suppress("DEPRECATION")
    private fun Intent.parcelableUriExtra(name: String): Uri? = getParcelableExtra(name)

    private const val GLOBAL_WILDCARD_MIME = "*/*"
    private const val GENERIC_BINARY_MIME = "application/octet-stream"
    private const val GENERIC_ZIP_MIME = "application/zip"
    private const val REQUIRED_EXPLORER_CLIP_ITEM_COUNT = 2
    private const val FORBIDDEN_EXPLORER_GRANTS =
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    private const val FORBIDDEN_EXTERNAL_GRANTS =
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
}
