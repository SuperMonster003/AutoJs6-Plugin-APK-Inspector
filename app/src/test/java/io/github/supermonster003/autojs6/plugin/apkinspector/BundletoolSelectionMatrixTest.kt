package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.BundletoolTocDecoder
import org.autojs.plugin.packagearchive.PackageDeviceSpec

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class BundletoolSelectionMatrixTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun simulatedSelectionsMatchBundletoolInstallOracleAndRemainReproducible() {
        val matrix = resourceBytes(MATRIX_RESOURCE).let { bytes ->
            Gson().fromJson(bytes.toString(Charsets.UTF_8), SelectionMatrix::class.java)
        }
        assertEquals(1, matrix.schemaVersion)
        assertEquals("1.18.2", matrix.bundletool.version)
        assertEquals(BUNDLETOOL_SHA256, matrix.bundletool.sha256)
        assertEquals("test-fixtures/bundletool-selection", matrix.fixture.sourceProject)
        assertTrue(matrix.oracle.contains("InstallApksCommand"))
        assertEquals(3, matrix.cases.size)

        val fixtureBytes = resourceBytes("/$RESOURCE_DIRECTORY/${matrix.fixture.file}")
        assertEquals(matrix.fixture.sha256, fixtureBytes.sha256())
        val fixture = temporaryFolder.newFile(matrix.fixture.file).apply {
            writeBytes(fixtureBytes)
        }

        val actualCase = matrix.cases.first()
        val actualDevice = actualCase.deviceSpec.toPackageDeviceSpec()
        val actualArchive = AndroidPackageArchiveInspector.inspect(fixture, actualDevice)
        assertSameSelection(
            "bundletool oracle mismatch for ${actualCase.id}",
            actualCase.expectedApks,
            actualArchive.selectedApks.map(ArchiveApkEntry::archivePath),
        )

        val selectedPathSets = matrix.cases.map { fixtureCase ->
            val simulatedDevice = fixtureCase.deviceSpec.toPackageDeviceSpec()
            val first = PackageSelectionSimulator.simulate(
                actualArchive = actualArchive,
                actualDevice = actualDevice,
                simulatedDevice = simulatedDevice,
            )
            val second = PackageSelectionSimulator.simulate(
                actualArchive = actualArchive,
                actualDevice = actualDevice,
                simulatedDevice = simulatedDevice,
            )

            assertSameSelection(
                "bundletool install selection mismatch for ${fixtureCase.id}",
                fixtureCase.expectedApks,
                first.simulatedSelectedApkPaths,
            )
            assertEquals(
                "simulation was not reproducible for ${fixtureCase.id}",
                first.simulatedSelectedApkPaths,
                second.simulatedSelectedApkPaths,
            )
            assertSameSelection(
                "bounded toc selection mismatch for ${fixtureCase.id}",
                fixtureCase.expectedApks,
                BundletoolTocDecoder.select(fixture, simulatedDevice).apkPaths,
            )
            assertTrue(
                "fixture inspection became incompatible for ${fixtureCase.id}",
                first.simulatedInspectionState != ArchiveInspectionState.INCOMPATIBLE,
            )
            first.simulatedSelectedApkPaths.toSet()
        }

        assertEquals(3, selectedPathSets.distinct().size)
        assertNotEquals(selectedPathSets[0], selectedPathSets[1])
        assertNotEquals(selectedPathSets[1], selectedPathSets[2])
    }

    private fun assertSameSelection(message: String, expected: List<String>, actual: List<String>) {
        assertEquals("$message (oracle duplicate path)", expected.size, expected.toSet().size)
        assertEquals("$message (duplicate path)", actual.size, actual.toSet().size)
        assertEquals(message, expected.toSet(), actual.toSet())
    }

    private fun resourceBytes(path: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource: $path" }
            .use { it.readBytes() }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun DeviceSpecFixture.toPackageDeviceSpec(): PackageDeviceSpec = PackageDeviceSpec(
        sdk = sdkVersion,
        abis = supportedAbis,
        densityDpi = screenDensity,
        locales = supportedLocales,
    )

    private data class SelectionMatrix(
        val schemaVersion: Int = 0,
        val bundletool: BundletoolFixture = BundletoolFixture(),
        val fixture: FixtureMetadata = FixtureMetadata(),
        val oracle: String = "",
        val cases: List<SelectionCase> = emptyList(),
    )

    private data class BundletoolFixture(
        val version: String = "",
        val sha256: String = "",
    )

    private data class FixtureMetadata(
        val file: String = "",
        val sha256: String = "",
        val sourceProject: String = "",
    )

    private data class SelectionCase(
        val id: String = "",
        val deviceSpec: DeviceSpecFixture = DeviceSpecFixture(),
        val expectedApks: List<String> = emptyList(),
    )

    private data class DeviceSpecFixture(
        val supportedAbis: List<String> = emptyList(),
        val supportedLocales: List<String> = emptyList(),
        val screenDensity: Int = 0,
        val sdkVersion: Int = 0,
    )

    companion object {
        private const val RESOURCE_DIRECTORY = "bundletool-selection"
        private const val MATRIX_RESOURCE = "/$RESOURCE_DIRECTORY/matrix.json"
        private const val BUNDLETOOL_SHA256 =
            "378b5434cd1378bef6b2bc527b8c7f0ff2584b273830335bce54d6d0813c8584"
    }
}
