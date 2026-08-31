package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageSelectionSimulatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val actualDevice = PackageDeviceSpec(
        sdk = 35,
        abis = listOf("arm64-v8a", "armeabi-v7a"),
        densityDpi = 440,
        locales = listOf("en-US", "en"),
    )

    @Test
    fun genericContainerIsReselectedForEverySimulatedDimension() {
        val container = temporaryFolder.newFile("configuration.apkm")
        writeZip(
            container,
            "base.apk" to nestedApk(manifest()),
            "split_config.arm64_v8a.apk" to nestedApk(manifest("config.arm64_v8a")),
            "split_config.x86.apk" to nestedApk(manifest("config.x86")),
            "split_config.hdpi.apk" to nestedApk(manifest("config.hdpi")),
            "split_config.xxhdpi.apk" to nestedApk(manifest("config.xxhdpi")),
            "split_config.en.apk" to nestedApk(manifest("config.en")),
            "split_config.fr.apk" to nestedApk(manifest("config.fr")),
        )
        val actualArchive = AndroidPackageArchiveInspector.inspect(container, actualDevice)
        val simulatedDevice = PackageSimulationConfiguration(
            languageTag = "fr-FR",
            densityDpi = 240,
            abi = "x86",
        ).applyTo(actualDevice)

        val simulation = PackageSelectionSimulator.simulate(
            actualArchive = actualArchive,
            actualDevice = actualDevice,
            simulatedDevice = simulatedDevice,
        )

        assertEquals(
            listOf(
                PackageDeviceDimension.LANGUAGE,
                PackageDeviceDimension.DENSITY,
                PackageDeviceDimension.ABI,
            ),
            simulation.changedDimensions,
        )
        assertEquals(
            listOf(
                "base.apk",
                "split_config.x86.apk",
                "split_config.hdpi.apk",
                "split_config.fr.apk",
            ),
            simulation.simulatedSelectedApkPaths,
        )
        assertEquals(
            listOf(
                "split_config.x86.apk",
                "split_config.hdpi.apk",
                "split_config.fr.apk",
            ),
            simulation.addedApkPaths,
        )
        assertEquals(
            listOf(
                "split_config.arm64_v8a.apk",
                "split_config.xxhdpi.apk",
                "split_config.en.apk",
            ),
            simulation.removedApkPaths,
        )
        assertEquals(ArchiveInspectionState.COMPATIBLE, simulation.simulatedInspectionState)
        assertFalse(simulation.selectionMatchesActual)
    }

    @Test
    fun actualConfigurationPreservesFallbacksAndReusesTheSameSelection() {
        val container = temporaryFolder.newFile("actual.apks")
        writeZip(
            container,
            "base.apk" to nestedApk(manifest()),
            "split_config.arm64_v8a.apk" to nestedApk(manifest("config.arm64_v8a")),
            "split_config.en.apk" to nestedApk(manifest("config.en")),
        )
        val actualArchive = AndroidPackageArchiveInspector.inspect(container, actualDevice)
        val selectedConfiguration = PackageSimulationConfiguration.from(actualDevice)
        val restoredDevice = selectedConfiguration.applyTo(actualDevice)

        val simulation = PackageSelectionSimulator.simulate(
            actualArchive = actualArchive,
            actualDevice = actualDevice,
            simulatedDevice = restoredDevice,
        )

        assertEquals(actualDevice, restoredDevice)
        assertTrue(simulation.changedDimensions.isEmpty())
        assertTrue(simulation.addedApkPaths.isEmpty())
        assertTrue(simulation.removedApkPaths.isEmpty())
        assertTrue(simulation.selectionMatchesActual)
    }

    private fun manifest(split: String? = null): ByteArray {
        val splitAttribute = split?.let { " split=\"$it\"" }.orEmpty()
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.simulation"
                android:versionCode="1"$splitAttribute>
                <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35" />
                <application android:label="Simulation" />
            </manifest>
        """.trimIndent().toByteArray()
    }

    private fun nestedApk(manifest: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write(manifest)
                zip.closeEntry()
            }
            output.toByteArray()
        }

    private fun writeZip(file: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
