package io.github.supermonster003.autojs6.plugin.apkinspector

import android.os.Bundle
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerHostFileInfoKeys
import java.util.Locale

internal data class HostFileInfoRequest(
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val expectedSourceSha256: String,
)

internal object HostFileInfoRequestPolicy {

    fun resolve(bundle: Bundle?): HostFileInfoRequest? {
        bundle ?: return null
        if (
            !bundle.containsKey(ExplorerHostFileInfoKeys.VERSION) ||
            !bundle.containsKey(ExplorerHostFileInfoKeys.ACTION_ID) ||
            !bundle.containsKey(ExplorerHostFileInfoKeys.DISPLAY_NAME) ||
            !bundle.containsKey(ExplorerHostFileInfoKeys.SIZE) ||
            !bundle.containsKey(ExplorerHostFileInfoKeys.LAST_MODIFIED) ||
            !bundle.containsKey(ExplorerHostFileInfoKeys.EXPECTED_SOURCE_SHA256)
        ) {
            return null
        }
        return validate(
            version = bundle.getInt(ExplorerHostFileInfoKeys.VERSION, 0),
            actionId = bundle.getString(ExplorerHostFileInfoKeys.ACTION_ID),
            displayName = bundle.getString(ExplorerHostFileInfoKeys.DISPLAY_NAME),
            size = bundle.getLong(ExplorerHostFileInfoKeys.SIZE, -1L),
            lastModified = bundle.getLong(ExplorerHostFileInfoKeys.LAST_MODIFIED, -1L),
            expectedSourceSha256 = bundle.getString(
                ExplorerHostFileInfoKeys.EXPECTED_SOURCE_SHA256,
            ),
        )
    }

    internal fun validate(
        version: Int,
        actionId: String?,
        displayName: String?,
        size: Long,
        lastModified: Long,
        expectedSourceSha256: String?,
    ): HostFileInfoRequest? {
        if (version != ExplorerActionProtocol.HOST_FILE_INFO_VERSION) return null
        if (actionId != ApkInspectorPlugin.ACTION_ID) return null
        val acceptedDisplayName = PackageRequestPolicy.validateDisplayName(displayName) ?: return null
        if (!PackageRequestPolicy.isDeclaredSizeAccepted(size)) return null
        if (lastModified < 0L) return null
        val sha256 = expectedSourceSha256
            ?.takeIf(SHA256_PATTERN::matches)
            ?.lowercase(Locale.ROOT)
            ?: return null
        return HostFileInfoRequest(
            displayName = acceptedDisplayName,
            size = size,
            lastModified = lastModified,
            expectedSourceSha256 = sha256,
        )
    }

    private val SHA256_PATTERN = Regex("[0-9A-Fa-f]{64}")
}
