package io.github.supermonster003.autojs6.plugin.apkinspector

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class PrivacyNeutralFixtureMatrixTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun matrixIsCompleteSmallAndChecksumVerified() {
        val matrix = loadMatrix()

        assertEquals(1, matrix.schemaVersion)
        assertEquals(EXPECTED_FORMATS, matrix.formats)
        assertEquals(EXPECTED_CATEGORIES, matrix.categories)
        assertEquals(EXPECTED_FORMATS.size * EXPECTED_CATEGORIES.size, matrix.cases.size)
        assertEquals("DETERMINISTIC_SYNTHETIC", matrix.provenance.kind)
        assertEquals(SYNTHETIC_NAMESPACE, matrix.provenance.namespace)
        assertFalse(matrix.provenance.containsPersonalData)
        assertFalse(matrix.provenance.containsRealApplications)
        assertFalse(matrix.provenance.containsSigningMaterial)
        assertFalse(matrix.provenance.containsExecutableCode)
        assertFalse(matrix.provenance.networkRequired)
        assertEquals(GENERATOR_PATH, matrix.generation.script)
        assertEquals("python $GENERATOR_PATH", matrix.generation.generateCommand)
        assertEquals("python $GENERATOR_PATH --verify", matrix.generation.verifyCommand)
        assertEquals("1980-01-01T00:00:00", matrix.generation.zipTimestamp)
        assertEquals("STORED", matrix.generation.zipCompression)
        assertEquals(MAX_ENTRY_NAME_CHARS, matrix.limits.archiveEntryNameCharacters)
        assertEquals(MAX_ENTRY_NAME_CHARS + 1, matrix.limits.overLimitEntryNameCharacters)

        val expectedPairs = EXPECTED_FORMATS.flatMap { format ->
            EXPECTED_CATEGORIES.map { category -> format to category }
        }.toSet()
        assertEquals(expectedPairs, matrix.cases.map { it.format to it.category }.toSet())
        assertEquals(matrix.cases.size, matrix.cases.map(FixtureCase::id).toSet().size)
        assertEquals(matrix.cases.size, matrix.cases.map(FixtureCase::file).toSet().size)

        val checksums = loadChecksums()
        assertEquals(matrix.cases.map(FixtureCase::file).toSet(), checksums.keys)
        var fixtureBytes = 0L
        matrix.cases.forEach { case ->
            val expectedExtension = case.format.lowercase()
            assertEquals("${case.id}.$expectedExtension", case.file)
            assertFalse(case.file.contains('/'))
            assertFalse(case.file.contains('\\'))
            assertTrue(SHA_256_REGEX.matches(case.sha256))
            assertEquals(EXPECTED_CONSTRUCTIONS.getValue(case.category), case.construction)

            val bytes = resourceBytes("/$RESOURCE_DIRECTORY/${case.file}")
            fixtureBytes += bytes.size
            assertEquals("byte count changed for ${case.id}", case.bytes, bytes.size)
            assertEquals("matrix checksum changed for ${case.id}", case.sha256, bytes.sha256())
            assertEquals("SHA256SUMS changed for ${case.id}", case.sha256, checksums[case.file])
            assertTrue(
                "fixture ${case.id} should remain repository-sized",
                bytes.size <= MAX_FIXTURE_BYTES,
            )
        }
        assertTrue("fixture matrix should remain compact", fixtureBytes <= MAX_MATRIX_FIXTURE_BYTES)
    }

    @Test
    fun fixturesExerciseTheirDeclaredInspectionOutcomes() {
        val matrix = loadMatrix()
        val device = matrix.deviceSpec.toPackageDeviceSpec()

        matrix.cases.forEach { case ->
            val file = temporaryFolder.newFile(case.file).apply {
                writeBytes(resourceBytes("/$RESOURCE_DIRECTORY/${case.file}"))
            }
            when (case.expected.result) {
                "REJECTED" -> {
                    val exception = captureIOException {
                        AndroidPackageArchiveInspector.inspect(file, device)
                    }
                    assertFalse("rejection should explain ${case.id}", exception.message.isNullOrBlank())
                    when (case.expected.rejection) {
                        "MALFORMED_ZIP" -> assertTrue(
                            "${case.id} should fail at ZIP structure",
                            exception is java.util.zip.ZipException,
                        )
                        "ARCHIVE_ENTRY_NAME_LIMIT" -> assertTrue(
                            "${case.id} should hit the entry-name guard",
                            exception.message.orEmpty().contains("unsafe entry name"),
                        )
                        else -> fail("Unknown rejection contract for ${case.id}: ${case.expected.rejection}")
                    }
                }
                "ACCEPTED" -> {
                    val archive = AndroidPackageArchiveInspector.inspect(file, device)
                    assertNotNull("base manifest missing for ${case.id}", archive.baseManifest)
                    val manifest = archive.baseManifest!!
                    assertEquals(
                        "format mismatch for ${case.id}",
                        AndroidPackageFormat.valueOf(case.expected.detectedFormat),
                        archive.format,
                    )
                    assertEquals(
                        "state mismatch for ${case.id}",
                        ArchiveInspectionState.valueOf(case.expected.inspectionState),
                        archive.inspectionState,
                    )
                    assertEquals(case.expected.packageName, manifest.packageName)
                    assertTrue(manifest.packageName.orEmpty().startsWith("$SYNTHETIC_NAMESPACE."))
                    assertEquals(case.expected.minimumSdk, manifest.minSdk)
                    val supportsBaseline =
                        manifest.minSdk?.let { it <= device.sdk } != false &&
                            manifest.maxSdk?.let { it >= device.sdk } != false
                    assertEquals(
                        "manifest compatibility mismatch for ${case.id}",
                        case.expected.deviceCompatible,
                        supportsBaseline,
                    )
                    if (archive.format != AndroidPackageFormat.AAB) {
                        assertEquals(
                            case.expected.deviceCompatible,
                            archive.inspectionState != ArchiveInspectionState.INCOMPATIBLE,
                        )
                    }
                }
                else -> fail("Unknown result contract for ${case.id}: ${case.expected.result}")
            }
        }
    }

    @Test
    fun fixturesContainNoExecutableOrSigningPayloads() {
        val matrix = loadMatrix()
        matrix.cases.forEach { case ->
            val bytes = resourceBytes("/$RESOURCE_DIRECTORY/${case.file}")
            assertFalse("APK signing block leaked into ${case.id}", bytes.containsAscii(APK_SIG_BLOCK_MAGIC))
            assertFalse("DEX filename leaked into ${case.id}", bytes.containsAscii("classes.dex"))
            assertFalse("native-library filename leaked into ${case.id}", bytes.containsAscii(".so"))
            SIGNER_EXTENSIONS.forEach { extension ->
                assertFalse(
                    "certificate filename leaked into ${case.id}",
                    bytes.containsAscii(extension),
                )
            }
            if (case.category == "STRUCTURALLY_DAMAGED") return@forEach
            val file = temporaryFolder.newFile("audit-${case.file}").apply { writeBytes(bytes) }
            ZipFile(file).use { archive ->
                val entries = archive.entries().asSequence().filterNot { it.isDirectory }.toList()
                if (case.category == "OVER_LIMIT") {
                    assertEquals(
                        MAX_ENTRY_NAME_CHARS + 1,
                        entries.maxOf { it.name.length },
                    )
                } else {
                    assertTrue(entries.all { it.name.length <= MAX_ENTRY_NAME_CHARS })
                }
                entries.forEach { entry ->
                    assertFalse("DEX payload leaked into ${case.id}", entry.name.endsWith(".dex", true))
                    assertFalse("native payload leaked into ${case.id}", entry.name.endsWith(".so", true))
                    assertFalse("certificate payload leaked into ${case.id}", SIGNER_EXTENSIONS.any {
                        entry.name.endsWith(it, true)
                    })
                    if (entry.name.endsWith(".apk", true)) {
                        val nestedBytes = archive.getInputStream(entry).use { it.readBytes() }
                        assertEquals(listOf("AndroidManifest.xml"), nestedEntryNames(nestedBytes))
                        assertFalse(nestedBytes.containsAscii(APK_SIG_BLOCK_MAGIC))
                    }
                }
            }
        }
    }

    private fun loadMatrix(): FixtureMatrix =
        Gson().fromJson(resourceText(MATRIX_RESOURCE), FixtureMatrix::class.java)

    private fun loadChecksums(): Map<String, String> =
        resourceText("/$RESOURCE_DIRECTORY/$CHECKSUM_RESOURCE")
            .lineSequence()
            .filter(String::isNotBlank)
            .associate { line ->
                val separator = line.indexOf("  ")
                assertEquals("invalid SHA256SUMS digest width", 64, separator)
                val digest = line.substring(0, separator)
                assertTrue(SHA_256_REGEX.matches(digest))
                line.substring(separator + 2) to digest
            }

    private fun nestedEntryNames(bytes: ByteArray): List<String> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
            buildList {
                while (true) {
                    val entry = archive.nextEntry ?: break
                    if (!entry.isDirectory) add(entry.name)
                    archive.closeEntry()
                }
            }
        }

    private fun captureIOException(block: () -> Unit): IOException = try {
        block()
        throw AssertionError("Expected IOException")
    } catch (exception: IOException) {
        exception
    }

    private fun resourceText(path: String): String =
        resourceBytes(path).toString(Charsets.UTF_8)

    private fun resourceBytes(path: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource: $path" }
            .use { it.readBytes() }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun ByteArray.containsAscii(value: String): Boolean {
        val needle = value.toByteArray(Charsets.US_ASCII)
        if (needle.isEmpty() || needle.size > size) return false
        return indices.any { offset ->
            offset <= size - needle.size && needle.indices.all { index -> this[offset + index] == needle[index] }
        }
    }

    private fun DeviceSpecFixture.toPackageDeviceSpec(): PackageDeviceSpec = PackageDeviceSpec(
        sdk = sdk,
        abis = abis,
        densityDpi = densityDpi,
        locales = locales,
    )

    private data class FixtureMatrix(
        val schemaVersion: Int = 0,
        val provenance: ProvenanceFixture = ProvenanceFixture(),
        val generation: GenerationFixture = GenerationFixture(),
        val limits: LimitsFixture = LimitsFixture(),
        val deviceSpec: DeviceSpecFixture = DeviceSpecFixture(),
        val formats: List<String> = emptyList(),
        val categories: List<String> = emptyList(),
        val cases: List<FixtureCase> = emptyList(),
    )

    private data class ProvenanceFixture(
        val kind: String = "",
        val namespace: String = "",
        val containsPersonalData: Boolean = true,
        val containsRealApplications: Boolean = true,
        val containsSigningMaterial: Boolean = true,
        val containsExecutableCode: Boolean = true,
        val networkRequired: Boolean = true,
    )

    private data class GenerationFixture(
        val script: String = "",
        val generateCommand: String = "",
        val verifyCommand: String = "",
        val zipTimestamp: String = "",
        val zipCompression: String = "",
    )

    private data class LimitsFixture(
        val archiveEntryNameCharacters: Int = 0,
        val overLimitEntryNameCharacters: Int = 0,
    )

    private data class DeviceSpecFixture(
        val sdk: Int = 0,
        val abis: List<String> = emptyList(),
        val densityDpi: Int = 0,
        val locales: List<String> = emptyList(),
    )

    private data class FixtureCase(
        val id: String = "",
        val format: String = "",
        val category: String = "",
        val file: String = "",
        val bytes: Int = 0,
        val sha256: String = "",
        val construction: String = "",
        val expected: ExpectedFixture = ExpectedFixture(),
    )

    private data class ExpectedFixture(
        val result: String = "",
        val rejection: String = "",
        val detectedFormat: String = "",
        val inspectionState: String = "",
        val deviceCompatible: Boolean = false,
        val minimumSdk: Int = 0,
        val packageName: String = "",
    )

    private companion object {
        const val RESOURCE_DIRECTORY = "privacy-neutral-fixtures"
        const val MATRIX_RESOURCE = "/$RESOURCE_DIRECTORY/matrix.json"
        const val CHECKSUM_RESOURCE = "SHA256SUMS"
        const val GENERATOR_PATH = ".python/generate_privacy_neutral_fixtures.py"
        const val SYNTHETIC_NAMESPACE = "org.example.apkinspector.fixture"
        const val MAX_ENTRY_NAME_CHARS = 1_024
        const val MAX_FIXTURE_BYTES = 4 * 1024
        const val MAX_MATRIX_FIXTURE_BYTES = 64 * 1024
        const val APK_SIG_BLOCK_MAGIC = "APK Sig Block 42"
        val EXPECTED_FORMATS = listOf("APK", "APKS", "XAPK", "APKM", "APKZ", "AAB")
        val EXPECTED_CATEGORIES = listOf(
            "NORMAL",
            "STRUCTURALLY_DAMAGED",
            "OVER_LIMIT",
            "DEVICE_INCOMPATIBLE",
        )
        val EXPECTED_CONSTRUCTIONS = mapOf(
            "NORMAL" to "SYNTHETIC_MINIMAL_PACKAGE",
            "STRUCTURALLY_DAMAGED" to
                "ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURES_CORRUPTED",
            "OVER_LIMIT" to "ARCHIVE_ENTRY_NAME_1025_CHARS",
            "DEVICE_INCOMPATIBLE" to "MIN_SDK_99",
        )
        val SHA_256_REGEX = Regex("[0-9a-f]{64}")
        val SIGNER_EXTENSIONS = listOf(".RSA", ".DSA", ".EC")
    }
}
