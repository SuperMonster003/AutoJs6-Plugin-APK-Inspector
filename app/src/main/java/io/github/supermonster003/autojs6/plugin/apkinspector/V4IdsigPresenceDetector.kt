package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.ContentResolver
import android.net.Uri
import java.io.Closeable

/**
 * Probes one exact V4 sidecar candidate without enumerating its parent or reading its contents.
 * Presence is only a hint: this class deliberately performs no idsig parsing or verification.
 */
internal object V4IdsigPresenceDetector {

    private const val IDSIG_SUFFIX = ".idsig"

    fun candidateName(packageDisplayName: String): String? {
        if (PackageRequestPolicy.validateDisplayName(packageDisplayName) != packageDisplayName) return null
        if (PackageRequestPolicy.extensionOf(packageDisplayName) != "apk") return null
        return "$packageDisplayName$IDSIG_SUFFIX"
            .takeIf { it.length <= PackageRequestPolicy.MAX_DISPLAY_NAME_LENGTH }
    }

    fun candidateUri(parentUri: Uri, packageDisplayName: String): Uri? {
        val name = candidateName(packageDisplayName) ?: return null
        return parentUri.buildUpon().appendPath(name).build()
    }

    fun isPresent(contentResolver: ContentResolver, candidateUri: Uri): Boolean =
        probePresence { contentResolver.openAssetFileDescriptor(candidateUri, "r") }

    internal fun probePresence(openReadOnly: () -> Closeable?): Boolean = try {
        openReadOnly()?.use { true } ?: false
    } catch (_: Exception) {
        false
    }
}
