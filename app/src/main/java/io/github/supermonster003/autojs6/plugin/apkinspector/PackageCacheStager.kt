package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

internal data class StagedPackage(
    val file: File,
    val displayName: String,
    val byteSize: Long,
    val mimeType: String,
    val sha256: String,
    val v4IdsigFile: File?,
)

/** Creates a bounded immutable app-private snapshot before any archive parser sees untrusted data. */
internal object PackageCacheStager {

    internal const val CACHE_DIRECTORY = "inspection-inputs"
    internal const val SESSION_PREFIX = "session-"
    internal const val MINIMUM_FREE_CACHE_BYTES = 128L * 1024L * 1024L
    internal const val STALE_SESSION_AGE_MILLIS = 24L * 60L * 60L * 1000L

    suspend fun stage(context: Context, seed: PackageInputSeed): StagedPackage? {
        return try {
            val resolver = context.contentResolver
            val queriedSize = queryLong(resolver, seed.targetUri, OpenableColumns.SIZE)
            if (queriedSize != null && !PackageRequestPolicy.isDeclaredSizeAccepted(queriedSize)) {
                return null
            }
            val descriptorSize = resolver.openAssetFileDescriptor(seed.targetUri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
            }
            val evaluation = PackageStagingValidator.evaluate(
                PackageStagingFacts(
                    declaredMime = seed.mimeType,
                    resolverMime = resolver.getType(seed.targetUri),
                    fromExplorer = seed.fromExplorer,
                    declaredDisplayName = seed.displayName,
                    queriedDisplayName = queryString(
                        resolver,
                        seed.targetUri,
                        OpenableColumns.DISPLAY_NAME,
                    ),
                    fallbackDisplayName = seed.targetUri.lastPathSegment?.substringAfterLast('/'),
                    declaredSize = seed.declaredSize,
                    queriedSize = queriedSize,
                    descriptorSize = descriptorSize,
                ),
            )
            val accepted = evaluation as? PackageStagingEvaluation.Accepted ?: return null

            val cacheRoot = ensureCacheRoot(context.cacheDir)
            cleanupStaleSessions(cacheRoot)
            val copyLimit = calculateCopyLimit(cacheRoot.usableSpace)
            if (PackageStagingValidator.rejectStorageLimit(accepted.expectedSize, copyLimit) != null) {
                return null
            }

            val sessionDirectory = createSessionDirectory(cacheRoot, UUID.randomUUID().toString())
            val extension = PackageRequestPolicy.extensionOf(accepted.displayName)
            val snapshot = File(sessionDirectory, "input.$extension")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val byteSize = requirePackageContent(resolver.openInputStream(seed.targetUri)).use { rawInput ->
                    BufferedInputStream(rawInput).use { input ->
                        BufferedOutputStream(FileOutputStream(snapshot)).use { output ->
                            copyBounded(
                                input = input,
                                output = output,
                                digest = digest,
                                maxBytes = copyLimit,
                                limitRejection = PackageStagingRejection.COPY_LIMIT,
                            )
                        }
                    }
                }
                requireCopiedSize(accepted.expectedSize, byteSize)
                requireReadOnly(
                    rejection = PackageStagingRejection.SNAPSHOT_READ_ONLY,
                    message = "Unable to make package snapshot read-only",
                    makeReadOnly = snapshot::setReadOnly,
                )
                currentCoroutineContext().ensureActive()
                val v4IdsigFile = if (
                    extension == "apk" && seed.hostSession != null && seed.targetId != null
                ) {
                    stageV4Idsig(seed.hostSession, seed.targetId, sessionDirectory)
                } else {
                    null
                }
                currentCoroutineContext().ensureActive()
                sessionDirectory.setLastModified(System.currentTimeMillis())
                StagedPackage(
                    file = snapshot,
                    displayName = accepted.displayName,
                    byteSize = byteSize,
                    mimeType = accepted.mimeType,
                    sha256 = digest.digest().joinToString("") { byte ->
                        "%02x".format(byte.toInt() and 0xFF)
                    },
                    v4IdsigFile = v4IdsigFile,
                )
            } catch (error: Throwable) {
                sessionDirectory.deleteRecursively()
                throw error
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    fun resolveInternalFile(context: Context, path: String?): File? {
        return resolveInternalFile(context.cacheDir, path)
    }

    internal fun resolveInternalFile(cacheDirectory: File, path: String?): File? {
        val candidate = resolveSessionFile(cacheDirectory, path) ?: return null
        if (!candidate.name.startsWith("input.")) return null
        return candidate.takeIf { PackageRequestPolicy.extensionOf(it.name) in PackageRequestPolicy.supportedExtensions }
    }

    fun resolveV4IdsigFile(context: Context, path: String?): File? {
        return resolveV4IdsigFile(context.cacheDir, path)
    }

    internal fun resolveV4IdsigFile(cacheDirectory: File, path: String?): File? {
        val candidate = resolveSessionFile(cacheDirectory, path) ?: return null
        return candidate.takeIf { it.name == V4_IDSIG_SNAPSHOT_NAME }
    }

    fun resolveManifestFile(context: Context, path: String?): File? {
        return resolveManifestFile(context.cacheDir, path)
    }

    internal fun resolveManifestFile(cacheDirectory: File, path: String?): File? {
        val candidate = resolveSessionFile(cacheDirectory, path) ?: return null
        return candidate.takeIf { it.name == "manifest.xml" }
    }

    private fun resolveSessionFile(cacheDirectory: File, path: String?): File? {
        val rawPath = path ?: return null
        val root = File(cacheDirectory, CACHE_DIRECTORY).canonicalFile
        val candidate = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return null
        if (!candidate.isFile || candidate.canWrite()) return null
        val parent = candidate.parentFile ?: return null
        if (parent.parentFile != root || !parent.name.startsWith(SESSION_PREFIX)) return null
        return candidate
    }

    internal suspend fun copyBounded(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        digest: MessageDigest,
        maxBytes: Long,
        limitRejection: PackageStagingRejection = PackageStagingRejection.COPY_LIMIT,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            currentCoroutineContext().ensureActive()
            if (read < 0) break
            total = Math.addExact(total, read.toLong())
            if (total > maxBytes) {
                throw PackageStagingException(limitRejection, "Input exceeds the inspection size limit")
            }
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        return total
    }

    private suspend fun stageV4Idsig(
        hostSession: org.autojs.plugin.explorer.api.IExplorerActionHostSession,
        targetId: String,
        sessionDirectory: File,
    ): File? {
        val descriptor = hostSession.openRelatedFile(targetId, V4_IDSIG_SUFFIX) ?: return null
        try {
            requireIdsigDeclaredSize(descriptor.statSize)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
        val snapshot = File(sessionDirectory, V4_IDSIG_SNAPSHOT_NAME)
        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { rawInput ->
                BufferedInputStream(rawInput).use { input ->
                    BufferedOutputStream(FileOutputStream(snapshot)).use { output ->
                        copyBounded(
                            input = input,
                            output = output,
                            digest = MessageDigest.getInstance("SHA-256"),
                            maxBytes = MAX_V4_IDSIG_BYTES,
                            limitRejection = PackageStagingRejection.IDSIG_COPY_LIMIT,
                        )
                    }
                }
            }
            requireReadOnly(
                rejection = PackageStagingRejection.IDSIG_READ_ONLY,
                message = "Unable to make V4 idsig snapshot read-only",
                makeReadOnly = snapshot::setReadOnly,
            )
            return snapshot
        } catch (error: Throwable) {
            snapshot.delete()
            runCatching { descriptor.close() }
            throw error
        }
    }

    internal fun cleanupStaleSessions(cacheRoot: File, now: Long = System.currentTimeMillis()) {
        cacheRoot.listFiles()?.forEach { child ->
            if (
                child.isDirectory &&
                child.name.startsWith(SESSION_PREFIX) &&
                now - child.lastModified() > STALE_SESSION_AGE_MILLIS
            ) {
                child.deleteRecursively()
            }
        }
    }

    private fun queryString(resolver: ContentResolver, uri: Uri, column: String): String? =
        querySingleValue(resolver, uri, column) { cursor, index -> cursor.getString(index) }

    private fun queryLong(resolver: ContentResolver, uri: Uri, column: String): Long? =
        querySingleValue(resolver, uri, column) { cursor, index ->
            cursor.takeUnless { it.isNull(index) }?.getLong(index)
        }

    private fun <T> querySingleValue(
        resolver: ContentResolver,
        uri: Uri,
        column: String,
        read: (Cursor, Int) -> T?,
    ): T? = runCatching {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(column)
            if (index < 0) null else read(cursor, index)
        }
    }.getOrNull()

    internal fun ensureCacheRoot(cacheDirectory: File): File {
        val cacheRoot = File(cacheDirectory, CACHE_DIRECTORY)
        if (!cacheRoot.exists() && !cacheRoot.mkdirs()) {
            throw PackageStagingException(
                PackageStagingRejection.CACHE_DIRECTORY,
                "Unable to create inspection cache",
            )
        }
        if (!cacheRoot.isDirectory) {
            throw PackageStagingException(
                PackageStagingRejection.CACHE_DIRECTORY,
                "Inspection cache path is not a directory",
            )
        }
        return cacheRoot
    }

    internal fun createSessionDirectory(cacheRoot: File, sessionId: String): File {
        val directory = File(cacheRoot, "$SESSION_PREFIX$sessionId")
        if (!directory.mkdir()) {
            throw PackageStagingException(
                PackageStagingRejection.SESSION_DIRECTORY,
                "Unable to create inspection session",
            )
        }
        return directory
    }

    internal fun calculateCopyLimit(usableSpace: Long): Long {
        val storageBound = usableSpace.takeIf { it > 0L }?.let { usable ->
            (usable - MINIMUM_FREE_CACHE_BYTES).coerceAtLeast(0L)
        } ?: PackageRequestPolicy.MAX_PACKAGE_BYTES
        return minOf(PackageRequestPolicy.MAX_PACKAGE_BYTES, storageBound)
    }

    internal fun requirePackageContent(input: java.io.InputStream?): java.io.InputStream =
        input ?: throw PackageStagingException(
            PackageStagingRejection.CONTENT_UNAVAILABLE,
            "Unable to open package content",
        )

    internal fun requireCopiedSize(expectedSize: Long?, actualSize: Long) {
        if (expectedSize != null && actualSize != expectedSize) {
            throw PackageStagingException(
                PackageStagingRejection.COPIED_SIZE_MISMATCH,
                "Package size changed while it was copied",
            )
        }
    }

    internal fun requireReadOnly(
        rejection: PackageStagingRejection,
        message: String,
        makeReadOnly: () -> Boolean,
    ) {
        if (!makeReadOnly()) throw PackageStagingException(rejection, message)
    }

    internal fun requireIdsigDeclaredSize(size: Long) {
        if (size > MAX_V4_IDSIG_BYTES) {
            throw PackageStagingException(
                PackageStagingRejection.IDSIG_DECLARED_SIZE,
                "V4 idsig exceeds the inspection size limit",
            )
        }
    }

    private const val V4_IDSIG_SUFFIX = ".idsig"
    private const val V4_IDSIG_SNAPSHOT_NAME = "input.apk.idsig"
    internal const val MAX_V4_IDSIG_BYTES = 40L * 1024L * 1024L
}
