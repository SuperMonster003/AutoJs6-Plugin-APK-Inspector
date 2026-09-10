package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionIntentValues
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionTargetKeys
import org.autojs.plugin.explorer.api.IExplorerActionHostSession
import java.util.Locale

internal data class PackageInputSeed(
    val targetUri: Uri,
    val displayName: String?,
    val declaredSize: Long?,
    val mimeType: String,
    val fromExplorer: Boolean,
    val targetId: String? = null,
    val hostSession: IExplorerActionHostSession? = null,
)

/** Validates every value crossing the exported Explorer Action and ACTION_VIEW boundaries. */
internal object PackageRequestPolicy {

    const val MAX_PACKAGE_BYTES = PackageInspectionLimits.PACKAGE_BYTES
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

    fun resolveExplorer(intent: Intent?): PackageInputSeed? {
        return try {
            val extracted = intent?.let(::extractExplorerRequest)
            if (PackageRequestValidator.rejectExplorer(extracted?.facts) != null) return null
            extracted ?: return null
            val facts = extracted.facts

            PackageInputSeed(
                targetUri = extracted.targetUri ?: return null,
                displayName = facts.displayName,
                declaredSize = facts.declaredSize,
                mimeType = normalizeMimeType(facts.mimeType) ?: return null,
                fromExplorer = true,
                targetId = facts.target?.id ?: return null,
                hostSession = extracted.hostSession ?: return null,
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    fun resolveExternal(intent: Intent?): PackageInputSeed? {
        return try {
            val extracted = intent?.let(::extractExternalRequest)
            if (PackageRequestValidator.rejectExternal(extracted?.facts) != null) return null
            extracted ?: return null
            PackageInputSeed(
                targetUri = extracted.targetUri ?: return null,
                displayName = null,
                declaredSize = null,
                mimeType = normalizeMimeType(extracted.facts.mimeType) ?: return null,
                fromExplorer = false,
            )
        } catch (_: RuntimeException) {
            null
        }
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

    internal fun isExplorerMimeAccepted(mimeType: String): Boolean {
        return mimeType in supportedExternalMimeTypes ||
            mimeType == GLOBAL_WILDCARD_MIME ||
            mimeType == GENERIC_BINARY_MIME ||
            mimeType == GENERIC_ZIP_MIME
    }

    private fun isUnsafeNameCharacter(character: Char): Boolean =
        character == '/' || character == '\\' || isUnsafeUnicodeCharacter(character)

    private fun isUnsafeUnicodeCharacter(character: Char): Boolean =
        character.isISOControl() || Character.getType(character) == Character.FORMAT.toInt()

    private fun extractExplorerRequest(intent: Intent): ExtractedExplorerRequest {
        val targetUri = intent.data
        val parentUri = intent.parcelableUriExtra(ExplorerActionIntentExtras.PARENT_URI)
        val clipData = intent.clipData
        val clipItem = clipData?.takeIf { it.itemCount > 0 }
            ?.getItemAt(ExplorerActionIntentValues.CLIP_ITEM_TARGET_INDEX)
        val targetBundles = intent.bundleListExtra(ExplorerActionIntentExtras.TARGETS)
        val targetBundle = targetBundles?.firstOrNull()
        val hostSession = intent.getBundleExtra(ExplorerActionIntentExtras.HOST_SESSION)
            ?.getBinder(ExplorerActionHostSessionKeys.BINDER)
            ?.let(IExplorerActionHostSession.Stub::asInterface)
        return ExtractedExplorerRequest(
            facts = ExplorerRequestFacts(
                action = intent.action,
                actionId = intent.getStringExtra(ExplorerActionIntentExtras.ACTION_ID),
                protocolVersion = intent.getIntExtra(
                    ExplorerActionIntentExtras.PROTOCOL_VERSION,
                    Int.MIN_VALUE,
                ),
                sourceSurface = intent.getStringExtra(ExplorerActionIntentExtras.SOURCE_SURFACE),
                flags = intent.flags,
                requestId = intent.getStringExtra(ExplorerActionIntentExtras.REQUEST_ID),
                hostVersionPresent = intent.hasExtra(ExplorerActionIntentExtras.HOST_VERSION_CODE),
                hostVersion = intent.getLongExtra(ExplorerActionIntentExtras.HOST_VERSION_CODE, -1L),
                targetUri = targetUri?.toPolicyFacts(),
                parentUri = parentUri?.toPolicyFacts(),
                clipDataPresent = clipData != null,
                clipItemCount = clipData?.itemCount ?: 0,
                clipItem = clipItem?.let { item ->
                    ExplorerClipItemFacts(
                        uri = item.uri?.toPolicyFacts(),
                        hasText = item.text != null,
                        hasHtmlText = item.htmlText != null,
                        hasIntent = item.intent != null,
                    )
                },
                displayName = intent.getStringExtra(ExplorerActionIntentExtras.DISPLAY_NAME),
                sizePresent = intent.hasExtra(ExplorerActionIntentExtras.SIZE),
                declaredSize = intent.getLongExtra(ExplorerActionIntentExtras.SIZE, -1L),
                mimeType = intent.type,
                targetsPresent = targetBundles != null,
                targetCount = targetBundles?.size ?: 0,
                target = targetBundle?.let { target ->
                    ExplorerTargetFacts(
                        id = target.getString(ExplorerActionTargetKeys.ID),
                        uri = target.parcelableUri(ExplorerActionTargetKeys.URI)?.toPolicyFacts(),
                        displayName = target.getString(ExplorerActionTargetKeys.DISPLAY_NAME),
                        kind = target.getInt(ExplorerActionTargetKeys.KIND, 0),
                        mimeType = target.getString(ExplorerActionTargetKeys.MIME_TYPE),
                        sizePresent = target.containsKey(ExplorerActionTargetKeys.SIZE),
                        size = target.getLong(ExplorerActionTargetKeys.SIZE, -1L),
                        lastModifiedPresent = target.containsKey(ExplorerActionTargetKeys.LAST_MODIFIED),
                    )
                },
                hostSessionPresent = hostSession != null,
            ),
            targetUri = targetUri,
            hostSession = hostSession,
        )
    }

    private fun extractExternalRequest(intent: Intent): ExtractedExternalRequest =
        ExtractedExternalRequest(
            facts = ExternalRequestFacts(
                action = intent.action,
                flags = intent.flags,
                targetUri = intent.data?.toPolicyFacts(),
                mimeType = intent.type,
            ),
            targetUri = intent.data,
        )

    private fun Uri.toPolicyFacts(): PackageUriFacts = PackageUriFacts(
        identity = toString(),
        isHierarchical = isHierarchical,
        scheme = scheme,
        authority = authority,
        host = host,
        userInfo = userInfo,
        port = port,
        query = query,
        fragment = fragment,
        encodedPath = encodedPath,
        pathSegments = pathSegments.toList(),
    )

    @Suppress("DEPRECATION")
    private fun Intent.parcelableUriExtra(name: String): Uri? = getParcelableExtra(name)

    @Suppress("DEPRECATION")
    private fun Intent.bundleListExtra(name: String): ArrayList<Bundle>? =
        getParcelableArrayListExtra(name)

    @Suppress("DEPRECATION")
    private fun Bundle.parcelableUri(name: String): Uri? = getParcelable(name)

    private data class ExtractedExplorerRequest(
        val facts: ExplorerRequestFacts,
        val targetUri: Uri?,
        val hostSession: IExplorerActionHostSession?,
    )

    private data class ExtractedExternalRequest(
        val facts: ExternalRequestFacts,
        val targetUri: Uri?,
    )

    private const val GLOBAL_WILDCARD_MIME = "*/*"
    private const val GENERIC_BINARY_MIME = "application/octet-stream"
    private const val GENERIC_ZIP_MIME = "application/zip"
}
