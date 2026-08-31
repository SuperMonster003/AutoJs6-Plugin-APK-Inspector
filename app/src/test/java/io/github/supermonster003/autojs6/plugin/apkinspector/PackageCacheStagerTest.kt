package io.github.supermonster003.autojs6.plugin.apkinspector

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class PackageCacheStagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun metadataRejectionMatrixCoversEveryFailClosedDecision() {
        val valid = validExplorerFacts()
        val external = validExternalFacts()
        val cases = listOf(
            PackageStagingRejection.DECLARED_MIME to valid.copy(declaredMime = "APPLICATION/X-APKS"),
            PackageStagingRejection.RESOLVER_MIME to valid.copy(resolverMime = " application/zip"),
            PackageStagingRejection.MIME_MISMATCH to valid.copy(resolverMime = "image/png"),
            PackageStagingRejection.DECLARED_DISPLAY_NAME to valid.copy(declaredDisplayName = "sample.zip"),
            PackageStagingRejection.QUERIED_DISPLAY_NAME to valid.copy(queriedDisplayName = "../sample.apk"),
            PackageStagingRejection.DISPLAY_NAME_MISMATCH to valid.copy(queriedDisplayName = "other.apk"),
            PackageStagingRejection.DISPLAY_NAME_MISSING to external.copy(
                queriedDisplayName = null,
                fallbackDisplayName = null,
            ),
            PackageStagingRejection.EXTERNAL_EXTENSION_MIME to external.copy(
                queriedDisplayName = "sample.apks",
                fallbackDisplayName = "sample.apks",
            ),
            PackageStagingRejection.DECLARED_SIZE to valid.copy(declaredSize = -1L),
            PackageStagingRejection.QUERIED_SIZE to valid.copy(
                queriedSize = PackageRequestPolicy.MAX_PACKAGE_BYTES + 1L,
            ),
            PackageStagingRejection.DESCRIPTOR_SIZE to valid.copy(
                descriptorSize = PackageRequestPolicy.MAX_PACKAGE_BYTES + 1L,
            ),
            PackageStagingRejection.QUERY_SIZE_MISMATCH to valid.copy(queriedSize = 41L),
            PackageStagingRejection.DESCRIPTOR_SIZE_MISMATCH to valid.copy(descriptorSize = 43L),
        )

        val accepted = PackageStagingValidator.evaluate(valid) as PackageStagingEvaluation.Accepted
        assertEquals("sample.apk", accepted.displayName)
        assertEquals(42L, accepted.expectedSize)
        assertEquals("application/vnd.android.package-archive", accepted.mimeType)
        assertTrue(PackageStagingValidator.evaluate(external) is PackageStagingEvaluation.Accepted)

        cases.forEach { (expected, facts) ->
            val rejected = PackageStagingValidator.evaluate(facts) as PackageStagingEvaluation.Rejected
            assertEquals(expected, rejected.reason)
        }
        assertEquals(METADATA_REJECTIONS, cases.map { it.first }.toSet())
    }

    @Test
    fun storageAndIoRejectionHelpersCoverEveryRemainingDecision() {
        val covered = linkedSetOf<PackageStagingRejection>()
        fun expect(expected: PackageStagingRejection, block: () -> Unit) {
            assertEquals(expected, captureStagingException(block).rejection)
            covered += expected
        }

        assertEquals(
            PackageStagingRejection.STORAGE_LIMIT,
            PackageStagingValidator.rejectStorageLimit(expectedSize = 43L, copyLimit = 42L),
        )
        assertNull(PackageStagingValidator.rejectStorageLimit(expectedSize = 42L, copyLimit = 42L))
        assertNull(PackageStagingValidator.rejectStorageLimit(expectedSize = null, copyLimit = 0L))
        covered += PackageStagingRejection.STORAGE_LIMIT

        val cacheParentFailure = temporaryFolder.newFile("cache-parent-is-a-file")
        expect(PackageStagingRejection.CACHE_DIRECTORY) {
            PackageCacheStager.ensureCacheRoot(cacheParentFailure)
        }
        val cacheDirectoryFailure = temporaryFolder.newFolder("cache-path-is-a-file")
        File(cacheDirectoryFailure, PackageCacheStager.CACHE_DIRECTORY).writeText("occupied")
        expect(PackageStagingRejection.CACHE_DIRECTORY) {
            PackageCacheStager.ensureCacheRoot(cacheDirectoryFailure)
        }

        val cacheDirectory = temporaryFolder.newFolder("cache-directory")
        val cacheRoot = PackageCacheStager.ensureCacheRoot(cacheDirectory)
        assertTrue(cacheRoot.isDirectory)
        PackageCacheStager.createSessionDirectory(cacheRoot, "collision")
        expect(PackageStagingRejection.SESSION_DIRECTORY) {
            PackageCacheStager.createSessionDirectory(cacheRoot, "collision")
        }

        expect(PackageStagingRejection.CONTENT_UNAVAILABLE) {
            PackageCacheStager.requirePackageContent(null)
        }
        val input = ByteArrayInputStream(byteArrayOf(1))
        assertSame(input, PackageCacheStager.requirePackageContent(input))

        expect(PackageStagingRejection.COPY_LIMIT) {
            runBlocking {
                PackageCacheStager.copyBounded(
                    input = ByteArrayInputStream(byteArrayOf(1, 2)),
                    output = ByteArrayOutputStream(),
                    digest = MessageDigest.getInstance("SHA-256"),
                    maxBytes = 1L,
                )
            }
        }
        expect(PackageStagingRejection.IDSIG_COPY_LIMIT) {
            runBlocking {
                PackageCacheStager.copyBounded(
                    input = ByteArrayInputStream(byteArrayOf(1, 2)),
                    output = ByteArrayOutputStream(),
                    digest = MessageDigest.getInstance("SHA-256"),
                    maxBytes = 1L,
                    limitRejection = PackageStagingRejection.IDSIG_COPY_LIMIT,
                )
            }
        }
        expect(PackageStagingRejection.COPIED_SIZE_MISMATCH) {
            PackageCacheStager.requireCopiedSize(expectedSize = 2L, actualSize = 1L)
        }
        PackageCacheStager.requireCopiedSize(expectedSize = 1L, actualSize = 1L)
        PackageCacheStager.requireCopiedSize(expectedSize = null, actualSize = 1L)

        expect(PackageStagingRejection.SNAPSHOT_READ_ONLY) {
            PackageCacheStager.requireReadOnly(
                rejection = PackageStagingRejection.SNAPSHOT_READ_ONLY,
                message = "snapshot",
                makeReadOnly = { false },
            )
        }
        expect(PackageStagingRejection.IDSIG_DECLARED_SIZE) {
            PackageCacheStager.requireIdsigDeclaredSize(PackageCacheStager.MAX_V4_IDSIG_BYTES + 1L)
        }
        PackageCacheStager.requireIdsigDeclaredSize(-1L)
        PackageCacheStager.requireIdsigDeclaredSize(PackageCacheStager.MAX_V4_IDSIG_BYTES)
        expect(PackageStagingRejection.IDSIG_READ_ONLY) {
            PackageCacheStager.requireReadOnly(
                rejection = PackageStagingRejection.IDSIG_READ_ONLY,
                message = "idsig",
                makeReadOnly = { false },
            )
        }

        assertEquals(PackageStagingRejection.entries.toSet() - METADATA_REJECTIONS, covered)
    }

    @Test
    fun boundedCopyAcceptsTheExactLimitHashesBytesAndPropagatesCancellation() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4)
        val output = ByteArrayOutputStream()
        val digest = MessageDigest.getInstance("SHA-256")

        val copied = runBlocking {
            PackageCacheStager.copyBounded(
                input = ByteArrayInputStream(bytes),
                output = output,
                digest = digest,
                maxBytes = bytes.size.toLong(),
            )
        }

        assertEquals(bytes.size.toLong(), copied)
        assertArrayEquals(bytes, output.toByteArray())
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(bytes), digest.digest())

        try {
            runBlocking {
                val job = coroutineContext.job
                val cancellingInput = object : InputStream() {
                    override fun read(): Int = -1

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        job.cancel()
                        buffer[offset] = 1
                        return 1
                    }
                }
                PackageCacheStager.copyBounded(
                    input = cancellingInput,
                    output = ByteArrayOutputStream(),
                    digest = MessageDigest.getInstance("SHA-256"),
                    maxBytes = 1L,
                )
            }
            throw AssertionError("Expected CancellationException")
        } catch (_: CancellationException) {
            // Cancellation is deliberately rethrown rather than converted into an ordinary rejection.
        }
    }

    @Test
    fun copyLimitAlwaysPreservesTheConfiguredFreeSpaceReserve() {
        assertEquals(
            PackageRequestPolicy.MAX_PACKAGE_BYTES,
            PackageCacheStager.calculateCopyLimit(usableSpace = 0L),
        )
        assertEquals(
            0L,
            PackageCacheStager.calculateCopyLimit(PackageCacheStager.MINIMUM_FREE_CACHE_BYTES),
        )
        assertEquals(
            42L,
            PackageCacheStager.calculateCopyLimit(PackageCacheStager.MINIMUM_FREE_CACHE_BYTES + 42L),
        )
        assertEquals(
            PackageRequestPolicy.MAX_PACKAGE_BYTES,
            PackageCacheStager.calculateCopyLimit(Long.MAX_VALUE),
        )
    }

    @Test
    fun sessionFileResolversRejectWritableEscapedDeepAndMisnamedPaths() {
        val cacheDirectory = temporaryFolder.newFolder("resolver-cache")
        val root = File(cacheDirectory, PackageCacheStager.CACHE_DIRECTORY).apply { mkdirs() }
        val session = File(root, "${PackageCacheStager.SESSION_PREFIX}valid").apply { mkdir() }
        val wrongPrefix = File(root, "other-valid").apply { mkdir() }
        val nested = File(session, "nested").apply { mkdir() }
        val outside = temporaryFolder.newFolder("outside")
        val madeReadOnly = mutableListOf<File>()

        fun readOnlyFile(parent: File, name: String): File = File(parent, name).apply {
            writeText("fixture")
            assertTrue("unable to mark fixture read-only: $this", setReadOnly())
            assertFalse("fixture remains writable: $this", canWrite())
            madeReadOnly += this
        }

        try {
            val packageFile = readOnlyFile(session, "input.apk")
            val idsigFile = readOnlyFile(session, "input.apk.idsig")
            val manifestFile = readOnlyFile(session, "manifest.xml")
            val unsupported = readOnlyFile(session, "input.zip")
            val misnamed = readOnlyFile(session, "other.apk")
            val escaped = readOnlyFile(outside, "input.apk")
            val directChild = readOnlyFile(root, "input.apk")
            val tooDeep = readOnlyFile(nested, "input.apk")
            val wrongSession = readOnlyFile(wrongPrefix, "input.apk")
            val writable = File(session, "input.aab").apply { writeText("writable") }

            assertEquals(packageFile.canonicalFile, PackageCacheStager.resolveInternalFile(cacheDirectory, packageFile.path))
            assertEquals(idsigFile.canonicalFile, PackageCacheStager.resolveV4IdsigFile(cacheDirectory, idsigFile.path))
            assertEquals(manifestFile.canonicalFile, PackageCacheStager.resolveManifestFile(cacheDirectory, manifestFile.path))

            listOf<String?>(
                null,
                "\u0000",
                File(session, "missing.apk").path,
                writable.path,
                escaped.path,
                directChild.path,
                tooDeep.path,
                wrongSession.path,
                unsupported.path,
                misnamed.path,
            ).forEach { rejected ->
                assertNull("internal path should be rejected: $rejected", PackageCacheStager.resolveInternalFile(cacheDirectory, rejected))
            }
            assertNull(PackageCacheStager.resolveV4IdsigFile(cacheDirectory, packageFile.path))
            assertNull(PackageCacheStager.resolveManifestFile(cacheDirectory, packageFile.path))
        } finally {
            madeReadOnly.forEach { it.setWritable(true) }
        }
    }

    @Test
    fun staleCleanupDeletesOnlyExpiredSessionDirectories() {
        val root = temporaryFolder.newFolder("cleanup-root")
        val now = 10L * PackageCacheStager.STALE_SESSION_AGE_MILLIS
        val stale = File(root, "${PackageCacheStager.SESSION_PREFIX}stale").apply {
            mkdir()
            File(this, "input.apk").writeText("stale")
            setLastModified(now - PackageCacheStager.STALE_SESSION_AGE_MILLIS - 1L)
        }
        val boundary = File(root, "${PackageCacheStager.SESSION_PREFIX}boundary").apply {
            mkdir()
            setLastModified(now - PackageCacheStager.STALE_SESSION_AGE_MILLIS)
        }
        val unrelated = File(root, "unrelated").apply {
            mkdir()
            setLastModified(0L)
        }
        val prefixedFile = File(root, "${PackageCacheStager.SESSION_PREFIX}file").apply {
            writeText("not a directory")
            setLastModified(0L)
        }

        PackageCacheStager.cleanupStaleSessions(root, now)

        assertFalse(stale.exists())
        assertTrue(boundary.isDirectory)
        assertTrue(unrelated.isDirectory)
        assertTrue(prefixedFile.isFile)
    }

    private fun validExplorerFacts(): PackageStagingFacts = PackageStagingFacts(
        declaredMime = "application/vnd.android.package-archive",
        resolverMime = "application/vnd.android.package-archive",
        fromExplorer = true,
        declaredDisplayName = "sample.apk",
        queriedDisplayName = "sample.apk",
        fallbackDisplayName = "sample.apk",
        declaredSize = 42L,
        queriedSize = 42L,
        descriptorSize = 42L,
    )

    private fun validExternalFacts(): PackageStagingFacts = PackageStagingFacts(
        declaredMime = "application/vnd.android.package-archive",
        resolverMime = null,
        fromExplorer = false,
        declaredDisplayName = null,
        queriedDisplayName = "sample.apk",
        fallbackDisplayName = "fallback.apk",
        declaredSize = null,
        queriedSize = 42L,
        descriptorSize = 42L,
    )

    private fun captureStagingException(block: () -> Unit): PackageStagingException = try {
        block()
        throw AssertionError("Expected PackageStagingException")
    } catch (exception: PackageStagingException) {
        exception
    }

    private companion object {
        val METADATA_REJECTIONS = setOf(
            PackageStagingRejection.DECLARED_MIME,
            PackageStagingRejection.RESOLVER_MIME,
            PackageStagingRejection.MIME_MISMATCH,
            PackageStagingRejection.DECLARED_DISPLAY_NAME,
            PackageStagingRejection.QUERIED_DISPLAY_NAME,
            PackageStagingRejection.DISPLAY_NAME_MISMATCH,
            PackageStagingRejection.DISPLAY_NAME_MISSING,
            PackageStagingRejection.EXTERNAL_EXTENSION_MIME,
            PackageStagingRejection.DECLARED_SIZE,
            PackageStagingRejection.QUERIED_SIZE,
            PackageStagingRejection.DESCRIPTOR_SIZE,
            PackageStagingRejection.QUERY_SIZE_MISMATCH,
            PackageStagingRejection.DESCRIPTOR_SIZE_MISMATCH,
        )
    }
}
