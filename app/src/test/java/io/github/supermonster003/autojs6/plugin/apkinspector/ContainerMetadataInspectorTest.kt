package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.PackageDeviceSpec
import org.autojs.plugin.packagearchive.AndroidPackageFormat
import org.autojs.plugin.packagearchive.AndroidPackageSubtype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContainerMetadataInspectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val device = PackageDeviceSpec(
        sdk = 35,
        abis = listOf("arm64-v8a", "armeabi-v7a"),
        densityDpi = 480,
        locales = listOf("en-US"),
    )

    @Test
    fun saiApksPrefersV2MetadataAndConventionalIcon() {
        val container = writeContainer(
            "sai.apks",
            "meta.sai_v1.json" to jsonBytes(
                """
                    {
                      "package": "com.example.stale",
                      "version_code": 1,
                      "version_name": "old"
                    }
                """,
            ),
            "meta.sai_v2.json" to jsonBytes(
                """
                    {
                      "package": "com.example.demo",
                      "label": "Demo",
                      "version_code": 42,
                      "version_name": "1.2",
                      "meta_version": 2,
                      "split_apk": true
                    }
                """,
            ),
            "icon.png" to byteArrayOf(1, 2, 3),
            "base.apk" to nestedApk(manifest()),
        )

        val archive = AndroidPackageArchiveInspector.inspect(container, device)
        val metadata = archive.containerMetadata

        assertEquals(AndroidPackageFormat.APKS, archive.format)
        assertEquals(AndroidPackageSubtype.SAI_APKS, archive.subtype)
        assertEquals("meta.sai_v2.json", metadata.sourceEntry)
        assertEquals(ContainerMetadataPackager.SAI, metadata.packager)
        assertEquals("2", metadata.packagerVersion)
        assertEquals("com.example.demo", metadata.packageName)
        assertEquals("1.2", metadata.declaredVersionName)
        assertEquals("42", metadata.declaredVersionCode)
        assertEquals("icon.png", metadata.iconEntry)
        assertNull(metadata.issue)
        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
    }

    @Test
    fun xapkMetadataSelectsItsPackageAndDeclaredIconEntry() {
        val container = writeContainer(
            "demo.xapk",
            "manifest.json" to jsonBytes(
                """
                    {
                      "xapk_version": 2,
                      "apkm_version": 999,
                      "package_name": "com.example.demo",
                      "version_name": "9.9-container",
                      "version_code": "900",
                      "icon": "icons/demo.png"
                    }
                """,
            ),
            "icons/demo.png" to byteArrayOf(4, 5, 6),
            "first.apk" to nestedApk(manifest(packageName = "com.example.other")),
            "second.apk" to nestedApk(manifest()),
        )

        val archive = AndroidPackageArchiveInspector.inspect(container, device)
        val metadata = archive.containerMetadata

        assertEquals(AndroidPackageFormat.XAPK, archive.format)
        assertEquals(ContainerMetadataPackager.XAPK, metadata.packager)
        assertEquals("2", metadata.packagerVersion)
        assertEquals("9.9-container", metadata.declaredVersionName)
        assertEquals("900", metadata.declaredVersionCode)
        assertEquals("icons/demo.png", metadata.iconEntry)
        assertEquals(listOf("second.apk"), archive.selectedApks.map(ArchiveApkEntry::archivePath))
        assertEquals("com.example.demo", archive.baseManifest?.packageName)
        assertEquals(42L, archive.baseManifest?.versionCode)
        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
    }

    @Test
    fun apkmInfoMetadataUsesApkMirrorFieldsAndRootIcon() {
        val container = writeContainer(
            "demo.apkm",
            "META-INF/APKMIRROR.SF" to byteArrayOf(1),
            "info.json" to jsonBytes(
                """
                    {
                      "apkm_version": 5,
                      "app_name": "Demo",
                      "release_version": "1.2",
                      "versioncode": "42",
                      "pname": "com.example.demo",
                      "min_api": "24"
                    }
                """,
            ),
            "icon.png" to byteArrayOf(7, 8, 9),
            "base.apk" to nestedApk(manifest()),
        )

        val archive = AndroidPackageArchiveInspector.inspect(container, device)
        val metadata = archive.containerMetadata

        assertEquals(AndroidPackageFormat.APKM, archive.format)
        assertEquals(AndroidPackageSubtype.APKM, archive.subtype)
        assertEquals("info.json", metadata.sourceEntry)
        assertEquals(ContainerMetadataPackager.APK_MIRROR, metadata.packager)
        assertEquals("5", metadata.packagerVersion)
        assertEquals("com.example.demo", metadata.packageName)
        assertEquals("1.2", metadata.declaredVersionName)
        assertEquals("42", metadata.declaredVersionCode)
        assertEquals("icon.png", metadata.iconEntry)
        assertNull(metadata.issue)
    }

    @Test
    fun malformedMetadataDoesNotAffectManifestOrCodePartitions() {
        val container = writeContainer(
            "malformed.apkm",
            "META-INF/APKMIRROR.SF" to byteArrayOf(1),
            "info.json" to "{not-json".toByteArray(),
            "base.apk" to nestedApk(
                manifest(),
                "lib/arm64-v8a/libdemo.so" to byteArrayOf(1, 2, 3),
                "classes.dex" to byteArrayOf(4, 5, 6, 7),
            ),
        )

        val archive = AndroidPackageArchiveInspector.inspect(container, device)
        val metadata = archive.containerMetadata

        assertEquals(ContainerMetadataIssue.METADATA_INVALID, metadata.issue)
        assertEquals("info.json", metadata.sourceEntry)
        assertEquals(ContainerMetadataPackager.APK_MIRROR, metadata.packager)
        assertNull(metadata.packageName)
        assertNull(metadata.declaredVersionName)
        assertNull(metadata.declaredVersionCode)
        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
        assertEquals("com.example.demo", archive.baseManifest?.packageName)
        assertEquals(1, archive.manifestComponents.services.notExported)
        assertEquals(listOf("android.permission.CAMERA"), archive.baseManifest?.requestedPermissions)
        assertEquals(1, archive.nativeLibraries.totalLibraryCount)
        assertEquals(1, archive.dexFiles.totalFileCount)
    }

    @Test
    fun metadataOverOneMiBIsOmittedWithoutAffectingTheArchive() {
        val metadataBytes = ByteArray(ContainerMetadataInspector.MAX_METADATA_BYTES + 1) { ' '.code.toByte() }
        val container = writeContainer(
            "oversized.xapk",
            "manifest.json" to metadataBytes,
            "base.apk" to nestedApk(manifest()),
        )

        val archive = AndroidPackageArchiveInspector.inspect(container, device)
        val metadata = archive.containerMetadata

        assertEquals(ContainerMetadataIssue.METADATA_LIMIT, metadata.issue)
        assertEquals("manifest.json", metadata.sourceEntry)
        assertEquals(ContainerMetadataPackager.XAPK, metadata.packager)
        assertNull(metadata.packageName)
        assertNull(metadata.declaredVersionName)
        assertNull(metadata.declaredVersionCode)
        assertNull(metadata.iconEntry)
        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
        assertEquals("com.example.demo", archive.baseManifest?.packageName)
        assertEquals(1, archive.selectedApks.size)
        assertTrue(archive.problems.isEmpty())
    }

    private fun writeContainer(
        name: String,
        vararg entries: Pair<String, ByteArray>,
    ): File = temporaryFolder.newFile(name).also { file ->
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun nestedApk(
        manifest: ByteArray,
        vararg entries: Pair<String, ByteArray>,
    ): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifest)
            zip.closeEntry()
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }

    private fun manifest(
        packageName: String = "com.example.demo",
    ): ByteArray =
        """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageName"
                android:versionCode="42"
                android:versionName="1.2">
                <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35" />
                <uses-permission android:name="android.permission.CAMERA" />
                <application android:label="Demo">
                    <service android:name=".DemoService" android:exported="false" />
                </application>
            </manifest>
        """.trimIndent().toByteArray()

    private fun jsonBytes(value: String): ByteArray = value.trimIndent().toByteArray(Charsets.UTF_8)
}
