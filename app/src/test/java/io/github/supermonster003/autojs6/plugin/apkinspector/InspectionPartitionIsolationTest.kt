package io.github.supermonster003.autojs6.plugin.apkinspector

import com.reandroid.arsc.chunk.TableBlock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InspectionPartitionIsolationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val device = PackageDeviceSpec(
        sdk = 35,
        abis = listOf("arm64-v8a", "armeabi-v7a"),
        densityDpi = 480,
        locales = listOf("en-US"),
    )

    @Test
    fun permissionScanAndDisplayLimitsLeaveEveryOtherPartitionComplete() {
        val snapshot = inspect(
            writeApk(
                name = "permission-limit.apk",
                permissionCount = MAX_SCANNED_PERMISSION_FIXTURE_COUNT + 1,
            ),
        )

        assertEquals(
            MAX_SCANNED_PERMISSION_FIXTURE_COUNT + 1,
            snapshot.archive.baseManifest?.requestedPermissions?.size,
        )
        assertEquals(
            PermissionProtectionAnalyzer.MAX_DISPLAYED_PERMISSIONS,
            snapshot.permissions.permissions.size,
        )
        assertEquals(
            MAX_SCANNED_PERMISSION_FIXTURE_COUNT + 1 -
                PermissionProtectionAnalyzer.MAX_DISPLAYED_PERMISSIONS,
            snapshot.permissions.omittedCount,
        )
        assertPackageUsable(snapshot)
        assertComponentsComplete(snapshot)
        assertNativeLibrariesComplete(snapshot)
        assertDexComplete(snapshot)
        assertResourcesComplete(snapshot)
    }

    @Test
    fun componentScanLimitLeavesEveryOtherPartitionComplete() {
        val snapshot = inspect(
            writeApk(
                name = "component-limit.apk",
                componentCount = ManifestComponentSummaryParser.MAX_COMPONENTS_PER_MANIFEST + 1,
            ),
        )

        assertEquals(
            ManifestComponentSummaryParser.MAX_COMPONENTS_PER_MANIFEST,
            snapshot.archive.manifestComponents.total,
        )
        assertTrue(snapshot.archive.manifestComponents.scanLimitReached)
        assertEquals(0, snapshot.archive.manifestComponents.failedManifestCount)
        assertPackageUsable(snapshot)
        assertPermissionsComplete(snapshot)
        assertNativeLibrariesComplete(snapshot)
        assertDexComplete(snapshot)
        assertResourcesComplete(snapshot)
    }

    @Test
    fun nativeLibraryEntryLimitLeavesEveryOtherPartitionComplete() {
        val snapshot = inspect(
            writeApk(
                name = "native-limit.apk",
                nativeLibraryCount = NativeLibraryInspector.MAX_NATIVE_LIBRARY_ENTRIES + 1,
            ),
        )

        val nativeLibraries = snapshot.archive.nativeLibraries
        assertEquals(
            NativeLibraryInspector.MAX_NATIVE_LIBRARY_ENTRIES,
            nativeLibraries.totalLibraryCount,
        )
        assertEquals(1, nativeLibraries.omittedLibraryCount)
        assertTrue(nativeLibraries.hasPartialResults)
        assertPackageUsable(snapshot)
        assertPermissionsComplete(snapshot)
        assertComponentsComplete(snapshot)
        assertDexComplete(snapshot)
        assertResourcesComplete(snapshot)
    }

    @Test
    fun dexDisplayLimitLeavesEveryOtherPartitionComplete() {
        val snapshot = inspect(
            writeApk(
                name = "dex-limit.apk",
                dexFileCount = DexFileSummary.MAX_DISPLAYED_FILES + 1,
            ),
        )

        val dexFiles = snapshot.archive.dexFiles
        assertEquals(DexFileSummary.MAX_DISPLAYED_FILES + 1, dexFiles.totalFileCount)
        assertEquals(DexFileSummary.MAX_DISPLAYED_FILES, dexFiles.files.size)
        assertEquals(1, dexFiles.omittedFileCount)
        assertTrue(dexFiles.hasPartialResults)
        assertPackageUsable(snapshot)
        assertPermissionsComplete(snapshot)
        assertComponentsComplete(snapshot)
        assertNativeLibrariesComplete(snapshot)
        assertResourcesComplete(snapshot)
    }

    @Test
    fun resourceTableLimitLeavesEveryArchivePartitionComplete() {
        val snapshot = inspect(
            apk = writeApk("resource-limit.apk"),
            fallbackLimits = PackageResourceFallbackInspector.Limits(
                maxResourceTableBytes = 8,
            ),
        )

        assertNull(snapshot.resources.label)
        assertNull(snapshot.resources.icon)
        assertEquals(
            listOf(PackageResourceFallbackIssue.RESOURCE_TABLE_LIMIT),
            snapshot.resources.issues,
        )
        assertPackageUsable(snapshot)
        assertPermissionsComplete(snapshot)
        assertComponentsComplete(snapshot)
        assertNativeLibrariesComplete(snapshot)
        assertDexComplete(snapshot)
    }

    @Test
    fun malformedNestedCodeDirectoryLeavesManifestPermissionsAndResourcesComplete() {
        val nestedApk = fixtureZipBytes().also { bytes ->
            bytes[bytes.size - ZIP_EOCD_MIN_BYTES] = 0
        }
        val container = temporaryFolder.newFile("damaged-code.apkm")
        writeZip(container, "base.apk" to nestedApk)

        val snapshot = inspect(container)

        assertEquals(1, snapshot.archive.nativeLibraries.failedApkCount)
        assertEquals(0, snapshot.archive.nativeLibraries.totalLibraryCount)
        assertTrue(snapshot.archive.nativeLibraries.hasPartialResults)
        assertEquals(1, snapshot.archive.dexFiles.failedApkCount)
        assertEquals(0, snapshot.archive.dexFiles.totalFileCount)
        assertTrue(snapshot.archive.dexFiles.hasPartialResults)
        assertPackageUsable(snapshot)
        assertPermissionsComplete(snapshot)
        assertComponentsComplete(snapshot)
        assertResourcesComplete(snapshot)
    }

    private fun inspect(
        apk: File,
        fallbackLimits: PackageResourceFallbackInspector.Limits =
            PackageResourceFallbackInspector.Limits(),
    ): PartitionSnapshot {
        val archive = AndroidPackageArchiveInspector.inspect(apk, device)
        val summary = requireNotNull(archive.baseManifest)
        val permissions = PermissionProtectionAnalyzer.analyze(
            requestedPermissions = summary.requestedPermissions,
            declaredProtectionLevels = summary.declaredPermissionProtectionLevels,
        ) { name ->
            PermissionDefinition(
                protectionLevel = if (name.endsWith(".P0000")) 1 else 0,
                description = if (name.endsWith(".P0000")) "Runtime fixture permission" else null,
            )
        }
        val resources = PackageResourceFallbackInspector.inspect(
            archive = archive,
            device = device,
            limits = fallbackLimits,
        )
        return PartitionSnapshot(archive, permissions, resources)
    }

    private fun assertPackageUsable(snapshot: PartitionSnapshot) {
        assertEquals(ArchiveInspectionState.COMPATIBLE, snapshot.archive.inspectionState)
        assertEquals("com.example.isolation", snapshot.archive.baseManifest?.packageName)
        assertEquals(42L, snapshot.archive.baseManifest?.versionCode)
    }

    private fun assertPermissionsComplete(snapshot: PartitionSnapshot) {
        assertEquals(1, snapshot.permissions.permissions.size)
        assertEquals(0, snapshot.permissions.omittedCount)
        assertTrue(snapshot.permissions.permissions.single().protectionLevelResolved)
    }

    private fun assertComponentsComplete(snapshot: PartitionSnapshot) {
        val components = snapshot.archive.manifestComponents
        assertEquals(1, components.total)
        assertEquals(1, components.services.notExported)
        assertFalse(components.scanLimitReached)
        assertEquals(0, components.failedManifestCount)
        assertEquals(0, components.omittedManifestCount)
    }

    private fun assertNativeLibrariesComplete(snapshot: PartitionSnapshot) {
        val nativeLibraries = snapshot.archive.nativeLibraries
        assertEquals(1, nativeLibraries.totalLibraryCount)
        assertEquals(3L, nativeLibraries.totalUncompressedBytes)
        assertEquals("arm64-v8a", nativeLibraries.abiGroups.single().abi)
        assertFalse(nativeLibraries.hasPartialResults)
    }

    private fun assertDexComplete(snapshot: PartitionSnapshot) {
        val dexFiles = snapshot.archive.dexFiles
        assertEquals(1, dexFiles.totalFileCount)
        assertEquals(5L, dexFiles.totalUncompressedBytes)
        assertEquals("classes.dex", dexFiles.files.single().path.substringAfterLast("!/"))
        assertFalse(dexFiles.hasPartialResults)
    }

    private fun assertResourcesComplete(snapshot: PartitionSnapshot) {
        assertEquals("Isolation Demo", snapshot.resources.label)
        assertNotNull(snapshot.resources.icon)
        assertArrayEquals(ONE_PIXEL_PNG, snapshot.resources.icon?.bytes)
        assertTrue(snapshot.resources.issues.isEmpty())
    }

    private fun writeApk(
        name: String,
        permissionCount: Int = 1,
        componentCount: Int = 1,
        nativeLibraryCount: Int = 1,
        dexFileCount: Int = 1,
    ): File = temporaryFolder.newFile(name).also { file ->
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            writeFixtureEntries(
                zip = zip,
                permissionCount = permissionCount,
                componentCount = componentCount,
                nativeLibraryCount = nativeLibraryCount,
                dexFileCount = dexFileCount,
            )
        }
    }

    private fun fixtureZipBytes(
        permissionCount: Int = 1,
        componentCount: Int = 1,
        nativeLibraryCount: Int = 1,
        dexFileCount: Int = 1,
    ): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            writeFixtureEntries(
                zip = zip,
                permissionCount = permissionCount,
                componentCount = componentCount,
                nativeLibraryCount = nativeLibraryCount,
                dexFileCount = dexFileCount,
            )
        }
        output.toByteArray()
    }

    private fun writeFixtureEntries(
        zip: ZipOutputStream,
        permissionCount: Int,
        componentCount: Int,
        nativeLibraryCount: Int,
        dexFileCount: Int,
    ) {
        zip.writeEntry(
            "AndroidManifest.xml",
            manifest(permissionCount, componentCount),
        )
        zip.writeEntry("resources.arsc", RESOURCE_TABLE_BYTES)
        zip.writeEntry("res/mipmap-xxhdpi-v4/ic_launcher.png", ONE_PIXEL_PNG)
        repeat(nativeLibraryCount) { index ->
            zip.writeEntry(
                "lib/arm64-v8a/libfixture${index.toString().padStart(4, '0')}.so",
                byteArrayOf(1, 2, 3),
            )
        }
        repeat(dexFileCount) { index ->
            val path = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
            zip.writeEntry(path, byteArrayOf(1, 2, 3, 4, 5))
        }
    }

    private fun manifest(permissionCount: Int, componentCount: Int): ByteArray {
        val permissions = buildString {
            repeat(permissionCount) { index ->
                append("<uses-permission android:name=\"com.example.permission.P")
                append(index.toString().padStart(4, '0'))
                append("\" />\n")
            }
        }
        val components = buildString {
            repeat(componentCount) { index ->
                append("<service android:name=\".Service")
                append(index)
                append("\" android:exported=\"false\" />\n")
            }
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.isolation"
                android:versionCode="42"
                android:versionName="1.0">
                <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35" />
                $permissions
                <application
                    android:label="@string/app_name"
                    android:icon="@mipmap/ic_launcher">
                    $components
                </application>
            </manifest>
        """.trimIndent().toByteArray()
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun writeZip(file: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) -> zip.writeEntry(name, bytes) }
        }
    }

    private data class PartitionSnapshot(
        val archive: AndroidPackageArchive,
        val permissions: RequestedPermissionAnalysis,
        val resources: PackageResourceFallback,
    )

    private companion object {
        const val MAX_SCANNED_PERMISSION_FIXTURE_COUNT = 2_048
        const val ZIP_EOCD_MIN_BYTES = 22

        val ONE_PIXEL_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )

        val RESOURCE_TABLE_BYTES: ByteArray by lazy {
            val table = TableBlock()
            val packageBlock = table.newPackage(0x7F, "com.example.isolation")
            packageBlock.getOrCreate("", "string", "app_name")
                .setValueAsString("Isolation Demo")
            packageBlock.getOrCreate("xxhdpi", "mipmap", "ic_launcher")
                .setValueAsString("res/mipmap-xxhdpi-v4/ic_launcher.png")
            table.refresh()
            table.bytes
        }
    }
}
