package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class AndroidPackageArchiveRejectionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val device = PackageDeviceSpec(
        sdk = 35,
        abis = listOf("arm64-v8a"),
        densityDpi = 440,
        locales = listOf("en-US"),
    )

    @Test
    fun everyArchiveRejectionHasAnExplicitFailClosedCase() {
        val covered = linkedSetOf<AndroidPackageArchiveRejection>()
        fun expect(expected: AndroidPackageArchiveRejection, block: () -> Unit) {
            val failure = captureArchiveException(block)
            assertEquals(expected.message, failure.message)
            assertEquals(expected, failure.rejection)
            covered += expected
        }

        expect(AndroidPackageArchiveRejection.PACKAGE_FILE_MISSING) {
            AndroidPackageArchiveInspector.inspect(
                temporaryFolder.root.resolve("missing.apk"),
                device,
            )
        }

        val limits = ArchiveEntryValidationLimits(
            maxEntries = 1,
            maxNameCharacters = 8,
            maxEntryBytes = 10L,
            maxTotalBytes = 15L,
        )
        expect(AndroidPackageArchiveRejection.ARCHIVE_ENTRY_COUNT) {
            ArchiveEntryValidationState(limits).apply {
                accept("one.apk", 1L)
                accept("two.apk", 1L)
            }
        }
        listOf(
            "",
            " ",
            "123456789",
            "bad\u0000name",
            "bad\\name",
            "/base.apk",
            "C:base.apk",
            "dir/../base.apk",
        ).forEach { name ->
            expect(AndroidPackageArchiveRejection.ARCHIVE_ENTRY_NAME) {
                ArchiveEntryValidationState(limits).accept(name, 1L)
            }
        }
        expect(AndroidPackageArchiveRejection.ARCHIVE_DUPLICATE_ENTRY) {
            ArchiveEntryValidationState(limits.copy(maxEntries = 2)).apply {
                accept("same.apk", 1L)
                accept("same.apk", 1L)
            }
        }
        expect(AndroidPackageArchiveRejection.ARCHIVE_ENTRY_SIZE) {
            ArchiveEntryValidationState(limits).accept("base.apk", 11L)
        }
        expect(AndroidPackageArchiveRejection.ARCHIVE_TOTAL_SIZE) {
            ArchiveEntryValidationState(limits.copy(maxEntries = 2)).apply {
                accept("one.apk", 8L)
                accept("two.apk", 8L)
            }
        }
        expect(AndroidPackageArchiveRejection.ARCHIVE_TOTAL_SIZE) {
            ArchiveEntryValidationState(
                limits.copy(
                    maxEntries = 2,
                    maxEntryBytes = Long.MAX_VALUE,
                    maxTotalBytes = Long.MAX_VALUE,
                ),
            ).apply {
                accept("one.apk", Long.MAX_VALUE)
                accept("two.apk", 1L)
            }
        }

        expect(AndroidPackageArchiveRejection.AAB_MANIFEST_MISSING) {
            AndroidPackageArchiveValidator.requireAabDisplayManifest<Long>(null) { it }
        }
        expect(AndroidPackageArchiveRejection.AAB_DISPLAY_MANIFEST_SIZE) {
            AndroidPackageArchiveValidator.requireAabDisplayManifest(
                AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFEST_BYTES + 1L,
            ) { it }
        }
        expect(AndroidPackageArchiveRejection.GENERIC_APK_ENTRY_COUNT) {
            AndroidPackageArchiveValidator.requireGenericApkEntryCount(
                isBundletool = false,
                count = AndroidPackageArchiveInspector.MAX_GENERIC_APK_ENTRIES + 1,
            )
        }

        val unsupported = temporaryFolder.newFile("unsupported.bin")
        ZipOutputStream(FileOutputStream(unsupported)).use { Unit }
        expect(AndroidPackageArchiveRejection.UNSUPPORTED_CONTAINER) {
            AndroidPackageArchiveInspector.inspect(unsupported, device)
        }

        val displayCases = listOf(
            AndroidPackageArchiveRejection.DISPLAY_BASE_APK_MISSING to DisplayApkFacts(
                format = AndroidPackageFormat.APK,
                baseApkSize = null,
                availableCacheBytes = Long.MAX_VALUE,
                maxDisplayApkBytes = 10L,
            ),
            AndroidPackageArchiveRejection.DISPLAY_AAB_UNAVAILABLE to DisplayApkFacts(
                format = AndroidPackageFormat.AAB,
                baseApkSize = 1L,
                availableCacheBytes = Long.MAX_VALUE,
                maxDisplayApkBytes = 10L,
            ),
            AndroidPackageArchiveRejection.DISPLAY_APK_SIZE to DisplayApkFacts(
                format = AndroidPackageFormat.APKS,
                baseApkSize = 11L,
                availableCacheBytes = Long.MAX_VALUE,
                maxDisplayApkBytes = 10L,
            ),
            AndroidPackageArchiveRejection.DISPLAY_CACHE_SPACE to DisplayApkFacts(
                format = AndroidPackageFormat.APKS,
                baseApkSize = 1L,
                availableCacheBytes = 0L,
                maxDisplayApkBytes = 10L,
            ),
        )
        displayCases.forEach { (expected, facts) ->
            assertEquals(expected, AndroidPackageArchiveValidator.rejectDisplayApk(facts))
            covered += expected
        }

        val cacheFile = temporaryFolder.newFile("cache-is-a-file")
        expect(AndroidPackageArchiveRejection.DISPLAY_DIRECTORY) {
            AndroidPackageArchive.createDisplayDirectory(cacheFile, "session")
        }

        val emptyArchive = temporaryFolder.newFile("empty.apks")
        ZipOutputStream(FileOutputStream(emptyArchive)).use { Unit }
        ZipFile(emptyArchive).use { zip ->
            expect(AndroidPackageArchiveRejection.DISPLAY_BASE_ENTRY_MISSING) {
                AndroidPackageArchive.requireDisplayBaseEntry(zip, "base.apk")
            }
        }

        expect(AndroidPackageArchiveRejection.DISPLAY_APK_SIZE) {
            AndroidPackageArchive.copyDisplayApkBounded(
                input = ByteArrayInputStream(byteArrayOf(1, 2)),
                output = ByteArrayOutputStream(),
                maxBytes = 1L,
            )
        }
        expect(AndroidPackageArchiveRejection.MANIFEST_ROOT_MISSING) {
            ManifestSummaryParser.parse("<application />")
        }

        assertEquals(AndroidPackageArchiveRejection.entries.toSet(), covered)
    }

    @Test
    fun exactArchiveBoundariesRemainAccepted() {
        ArchiveEntryValidationState(
            ArchiveEntryValidationLimits(
                maxEntries = 2,
                maxNameCharacters = 8,
                maxEntryBytes = 10L,
                maxTotalBytes = 10L,
            ),
        ).apply {
            accept("12345678", 10L)
            accept("unknown", -1L)
        }
        assertTrue(ArchiveEntryValidationState.isSafeEntryName("dir/base", 8))
        assertEquals(
            AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFEST_BYTES.toLong(),
            AndroidPackageArchiveValidator.requireAabDisplayManifest(
                AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFEST_BYTES.toLong(),
            ) { it },
        )
        AndroidPackageArchiveValidator.requireGenericApkEntryCount(
            isBundletool = false,
            count = AndroidPackageArchiveInspector.MAX_GENERIC_APK_ENTRIES,
        )
        AndroidPackageArchiveValidator.requireGenericApkEntryCount(
            isBundletool = true,
            count = Int.MAX_VALUE,
        )
        assertNull(
            AndroidPackageArchiveValidator.rejectDisplayApk(
                DisplayApkFacts(
                    format = AndroidPackageFormat.APKS,
                    baseApkSize = 10L,
                    availableCacheBytes = 10L,
                    maxDisplayApkBytes = 10L,
                ),
            ),
        )
        assertNull(
            AndroidPackageArchiveValidator.rejectDisplayApk(
                DisplayApkFacts(
                    format = AndroidPackageFormat.APK,
                    baseApkSize = Long.MAX_VALUE,
                    availableCacheBytes = 0L,
                    maxDisplayApkBytes = 0L,
                ),
            ),
        )
        val copied = ByteArrayOutputStream()
        AndroidPackageArchive.copyDisplayApkBounded(
            input = ByteArrayInputStream(byteArrayOf(1, 2)),
            output = copied,
            maxBytes = 2L,
        )
        assertTrue(byteArrayOf(1, 2).contentEquals(copied.toByteArray()))
    }

    @Test
    fun malformedZipIsRejectedWithoutInspectionSideEffects() {
        val malformed = temporaryFolder.newFile("malformed.apk").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val before = malformed.readBytes()

        try {
            AndroidPackageArchiveInspector.inspect(malformed, device)
        } catch (_: IOException) {
            assertTrue(before.contentEquals(malformed.readBytes()))
            return
        }
        throw AssertionError("Expected malformed ZIP to be rejected")
    }

    private fun captureArchiveException(block: () -> Unit): AndroidPackageArchiveException {
        try {
            block()
        } catch (error: AndroidPackageArchiveException) {
            return error
        }
        throw AssertionError("Expected AndroidPackageArchiveException")
    }
}
