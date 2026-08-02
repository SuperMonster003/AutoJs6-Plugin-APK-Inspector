package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

internal data class StagedPackage(
    val file: File,
    val displayName: String,
    val byteSize: Long,
    val mimeType: String,
    val sha256: String,
)

/** Creates a bounded immutable app-private snapshot before any archive parser sees untrusted data. */
internal object PackageCacheStager {

    private const val CACHE_DIRECTORY = "inspection-inputs"
    private const val SESSION_PREFIX = "session-"
    private const val MINIMUM_FREE_CACHE_BYTES = 128L * 1024L * 1024L
    private const val STALE_SESSION_AGE_MILLIS = 24L * 60L * 60L * 1000L

    suspend fun stage(context: Context, seed: PackageInputSeed): StagedPackage? = try {
        val resolver = context.contentResolver
        val resolverMime = resolver.getType(seed.targetUri)?.let(PackageRequestPolicy::normalizeMimeType)
        if (
            resolverMime != null &&
            !PackageRequestPolicy.mimeTypesAreCompatible(seed.mimeType, resolverMime, seed.fromExplorer)
        ) {
            return null
        }

        val displayName = resolveDisplayName(resolver, seed) ?: return null
        if (!seed.fromExplorer && !PackageRequestPolicy.isExternalMimeCompatible(displayName, seed.mimeType)) {
            return null
        }

        val queriedSize = queryLong(resolver, seed.targetUri, OpenableColumns.SIZE)
            ?.takeIf(PackageRequestPolicy::isDeclaredSizeAccepted)
        val descriptorSize = resolver.openAssetFileDescriptor(seed.targetUri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it >= 0L }
        }
        val expectedSize = seed.declaredSize ?: queriedSize ?: descriptorSize
        if (expectedSize != null && !PackageRequestPolicy.isDeclaredSizeAccepted(expectedSize)) return null
        if (seed.fromExplorer && seed.declaredSize != null) {
            if (queriedSize != null && queriedSize != seed.declaredSize) return null
            if (descriptorSize != null && descriptorSize != seed.declaredSize) return null
        }

        val cacheRoot = File(context.cacheDir, CACHE_DIRECTORY).apply {
            if (!exists() && !mkdirs()) throw IOException("Unable to create inspection cache")
        }
        cleanupStaleSessions(cacheRoot)
        val storageBound = cacheRoot.usableSpace.takeIf { it > 0L }?.let { usable ->
            (usable - MINIMUM_FREE_CACHE_BYTES).coerceAtLeast(0L)
        } ?: PackageRequestPolicy.MAX_PACKAGE_BYTES
        val copyLimit = minOf(PackageRequestPolicy.MAX_PACKAGE_BYTES, storageBound)
        if (expectedSize != null && expectedSize > copyLimit) return null

        val sessionDirectory = File(cacheRoot, "$SESSION_PREFIX${UUID.randomUUID()}")
        if (!sessionDirectory.mkdir()) throw IOException("Unable to create inspection session")
        val extension = PackageRequestPolicy.extensionOf(displayName)
        val snapshot = File(sessionDirectory, "input.$extension")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val byteSize = resolver.openInputStream(seed.targetUri)?.use { rawInput ->
                BufferedInputStream(rawInput).use { input ->
                    BufferedOutputStream(FileOutputStream(snapshot)).use { output ->
                        copyBounded(input, output, digest, copyLimit)
                    }
                }
            } ?: throw IOException("Unable to open package content")
            if (expectedSize != null && byteSize != expectedSize) {
                throw IOException("Package size changed while it was copied")
            }
            if (!snapshot.setReadOnly()) {
                throw IOException("Unable to make package snapshot read-only")
            }
            sessionDirectory.setLastModified(System.currentTimeMillis())
            StagedPackage(
                file = snapshot,
                displayName = displayName,
                byteSize = byteSize,
                mimeType = resolverMime ?: seed.mimeType,
                sha256 = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xFF)
                },
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

    fun resolveInternalFile(context: Context, path: String?): File? {
        val candidate = resolveSessionFile(context, path) ?: return null
        if (!candidate.name.startsWith("input.")) return null
        return candidate
    }

    fun resolveManifestFile(context: Context, path: String?): File? {
        val candidate = resolveSessionFile(context, path) ?: return null
        return candidate.takeIf { it.name == "manifest.xml" }
    }

    private fun resolveSessionFile(context: Context, path: String?): File? {
        val rawPath = path ?: return null
        val root = File(context.cacheDir, CACHE_DIRECTORY).canonicalFile
        val candidate = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return null
        if (!candidate.isFile || candidate.canWrite()) return null
        val parent = candidate.parentFile ?: return null
        if (parent.parentFile != root || !parent.name.startsWith(SESSION_PREFIX)) return null
        return candidate
    }

    private fun resolveDisplayName(
        resolver: ContentResolver,
        seed: PackageInputSeed,
    ): String? {
        val queriedName = queryString(resolver, seed.targetUri, OpenableColumns.DISPLAY_NAME)
            ?.let(PackageRequestPolicy::validateDisplayName)
        if (seed.fromExplorer) {
            val declared = PackageRequestPolicy.validateDisplayName(seed.displayName) ?: return null
            if (queriedName != null && queriedName != declared) return null
            return declared
        }
        return queriedName ?: seed.targetUri.lastPathSegment
            ?.substringAfterLast('/')
            ?.let(PackageRequestPolicy::validateDisplayName)
    }

    private suspend fun copyBounded(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        digest: MessageDigest,
        maxBytes: Long,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            currentCoroutineContext().ensureActive()
            if (read < 0) break
            total = Math.addExact(total, read.toLong())
            if (total > maxBytes) throw IOException("Package exceeds the inspection size limit")
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun cleanupStaleSessions(cacheRoot: File, now: Long = System.currentTimeMillis()) {
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
}
