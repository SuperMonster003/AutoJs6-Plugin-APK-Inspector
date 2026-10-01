package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.PackageDeviceSpec
import org.autojs.plugin.packagearchive.ArchiveProblem

/** A user-selected subset of the device properties that influence APK targeting. */
internal data class PackageSimulationConfiguration(
    val languageTag: String,
    val densityDpi: Int,
    val abi: String,
) {

    fun applyTo(actualDevice: PackageDeviceSpec): PackageDeviceSpec = actualDevice.copy(
        abis = if (abi.equals(actualDevice.primaryAbi, ignoreCase = true)) {
            actualDevice.abis
        } else {
            listOf(abi)
        },
        densityDpi = densityDpi,
        locales = if (languageTag.equals(actualDevice.primaryLanguage, ignoreCase = true)) {
            actualDevice.locales
        } else {
            listOf(languageTag)
        },
    )

    companion object {
        fun from(device: PackageDeviceSpec): PackageSimulationConfiguration =
            PackageSimulationConfiguration(
                languageTag = device.primaryLanguage,
                densityDpi = device.densityDpi,
                abi = device.primaryAbi,
            )
    }
}

internal enum class PackageDeviceDimension {
    LANGUAGE,
    DENSITY,
    ABI,
}

/** The selected APK set for a simulated device, compared with the initial real-device result. */
internal data class PackageSelectionSimulation(
    val actualDevice: PackageDeviceSpec,
    val simulatedDevice: PackageDeviceSpec,
    val actualSelectedApkPaths: List<String>,
    val simulatedSelectedApkPaths: List<String>,
    val simulatedInspectionState: ArchiveInspectionState,
    val simulatedProblems: List<ArchiveProblem>,
) {

    val changedDimensions: List<PackageDeviceDimension> = buildList {
        if (!actualDevice.primaryLanguage.equals(simulatedDevice.primaryLanguage, ignoreCase = true)) {
            add(PackageDeviceDimension.LANGUAGE)
        }
        if (actualDevice.densityDpi != simulatedDevice.densityDpi) {
            add(PackageDeviceDimension.DENSITY)
        }
        if (!actualDevice.primaryAbi.equals(simulatedDevice.primaryAbi, ignoreCase = true)) {
            add(PackageDeviceDimension.ABI)
        }
    }

    val addedApkPaths: List<String> = simulatedSelectedApkPaths.filterNot(
        actualSelectedApkPaths.toHashSet()::contains,
    )

    val removedApkPaths: List<String> = actualSelectedApkPaths.filterNot(
        simulatedSelectedApkPaths.toHashSet()::contains,
    )

    val selectionMatchesActual: Boolean =
        actualSelectedApkPaths.toHashSet() == simulatedSelectedApkPaths.toHashSet()
}

/** Re-runs the bounded read-only package inspection against the already staged private snapshot. */
internal object PackageSelectionSimulator {

    fun simulate(
        actualArchive: AndroidPackageArchive,
        actualDevice: PackageDeviceSpec,
        simulatedDevice: PackageDeviceSpec,
    ): PackageSelectionSimulation {
        val simulatedArchive = if (simulatedDevice == actualDevice) {
            actualArchive
        } else {
            AndroidPackageArchiveInspector.inspect(actualArchive.sourceFile, simulatedDevice)
        }
        return PackageSelectionSimulation(
            actualDevice = actualDevice,
            simulatedDevice = simulatedDevice,
            actualSelectedApkPaths = actualArchive.selectedApks.map(ArchiveApkEntry::archivePath),
            simulatedSelectedApkPaths = simulatedArchive.selectedApks.map(ArchiveApkEntry::archivePath),
            simulatedInspectionState = simulatedArchive.inspectionState,
            simulatedProblems = simulatedArchive.problems,
        )
    }
}

private val PackageDeviceSpec.primaryLanguage: String
    get() = locales.firstOrNull()?.takeIf(String::isNotBlank) ?: "und"

private val PackageDeviceSpec.primaryAbi: String
    get() = abis.firstOrNull()?.takeIf(String::isNotBlank) ?: "unknown"
