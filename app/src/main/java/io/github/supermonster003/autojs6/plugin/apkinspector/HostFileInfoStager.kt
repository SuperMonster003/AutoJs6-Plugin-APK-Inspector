package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Copies the host-granted descriptor into the same bounded, read-only private staging area. */
internal object HostFileInfoStager {

    suspend fun stage(
        context: Context,
        source: ParcelFileDescriptor,
        request: HostFileInfoRequest,
    ): StagedPackage {
        val descriptorSize = source.statSize.takeIf { it >= 0L }
        if (descriptorSize != null && descriptorSize != request.size) {
            throw IllegalArgumentException("Host file descriptor size changed")
        }
        val cacheRoot = PackageCacheStager.ensureCacheRoot(context.cacheDir)
        PackageCacheStager.cleanupStaleSessions(cacheRoot)
        val copyLimit = PackageCacheStager.calculateCopyLimit(cacheRoot.usableSpace)
        if (PackageStagingValidator.rejectStorageLimit(request.size, copyLimit) != null) {
            throw IllegalArgumentException("Host file exceeds the available staging limit")
        }
        val sessionDirectory = PackageCacheStager.createSessionDirectory(
            cacheRoot,
            UUID.randomUUID().toString(),
        )
        val extension = PackageRequestPolicy.extensionOf(request.displayName)
        val snapshot = File(sessionDirectory, "input.$extension")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val byteSize = ParcelFileDescriptor.AutoCloseInputStream(source).use { rawInput ->
                BufferedInputStream(rawInput).use { input ->
                    BufferedOutputStream(FileOutputStream(snapshot)).use { output ->
                        PackageCacheStager.copyBounded(
                            input = input,
                            output = output,
                            digest = digest,
                            maxBytes = copyLimit,
                        )
                    }
                }
            }
            PackageCacheStager.requireCopiedSize(request.size, byteSize)
            PackageCacheStager.requireReadOnly(
                rejection = PackageStagingRejection.SNAPSHOT_READ_ONLY,
                message = "Unable to make host file snapshot read-only",
                makeReadOnly = snapshot::setReadOnly,
            )
            sessionDirectory.setLastModified(System.currentTimeMillis())
            return StagedPackage(
                file = snapshot,
                displayName = request.displayName,
                byteSize = byteSize,
                mimeType = MIME_GENERIC_BINARY,
                sha256 = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xFF)
                },
                v4IdsigFile = null,
            )
        } catch (error: Throwable) {
            sessionDirectory.deleteRecursively()
            throw error
        }
    }

    private const val MIME_GENERIC_BINARY = "application/octet-stream"
}
