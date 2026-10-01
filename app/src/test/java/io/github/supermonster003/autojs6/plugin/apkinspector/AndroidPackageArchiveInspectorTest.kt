package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.PackageDeviceSpec
import org.autojs.plugin.packagearchive.AndroidPackageFormat
import org.autojs.plugin.packagearchive.AndroidPackageSubtype
import org.autojs.plugin.packagearchive.ArchiveProblemCode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidPackageArchiveInspectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val arm64Device = PackageDeviceSpec(
        sdk = 35,
        abis = listOf("arm64-v8a", "armeabi-v7a"),
        densityDpi = 440,
        locales = listOf("en-US"),
    )

    @Test
    fun singleApkIsDetectedFromContentsAndParsed() {
        val apk = temporaryFolder.newFile("renamed.bin")
        writeZip(
            apk,
            "AndroidManifest.xml" to manifest(),
            "lib/arm64-v8a/libdemo.so" to byteArrayOf(1, 2, 3, 4),
            "classes.dex" to byteArrayOf(5, 6, 7, 8, 9),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apk, arm64Device)

        assertEquals(AndroidPackageFormat.APK, archive.format)
        assertEquals("com.example.demo", archive.baseManifest?.packageName)
        assertEquals(42L, archive.baseManifest?.versionCode)
        assertEquals(24, archive.baseManifest?.minSdk)
        assertEquals(1, archive.nativeLibraries.totalLibraryCount)
        assertEquals(4L, archive.nativeLibraries.totalUncompressedBytes)
        assertEquals(1, archive.dexFiles.totalFileCount)
        assertEquals(5L, archive.dexFiles.totalUncompressedBytes)
        assertEquals("classes.dex", archive.dexFiles.files.single().path)
        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
    }

    @Test
    fun inspectionDoesNotModifySource() {
        val apk = temporaryFolder.newFile("readonly.apk")
        writeZip(apk, "AndroidManifest.xml" to manifest())
        val bytesBefore = apk.readBytes()
        val lengthBefore = apk.length()
        val modifiedBefore = apk.lastModified()

        AndroidPackageArchiveInspector.inspect(apk, arm64Device)

        assertTrue(bytesBefore.contentEquals(apk.readBytes()))
        assertEquals(lengthBefore, apk.length())
        assertEquals(modifiedBefore, apk.lastModified())
    }

    @Test
    fun apkmSelectsDeviceAbiDensityAndLocaleSplits() {
        val apkm = temporaryFolder.newFile("demo.apkm")
        writeZip(
            apkm,
            "base.apk" to nestedApk(manifest()),
            "split_config.arm64_v8a.apk" to nestedApk(manifest(split = "config.arm64_v8a")),
            "split_config.x86.apk" to nestedApk(manifest(split = "config.x86")),
            "split_config.hdpi.apk" to nestedApk(manifest(split = "config.hdpi")),
            "split_config.xxhdpi.apk" to nestedApk(manifest(split = "config.xxhdpi")),
            "split_config.en.apk" to nestedApk(manifest(split = "config.en")),
            "split_config.fr.apk" to nestedApk(manifest(split = "config.fr")),
            "Android/obb/com.example.demo/main.42.com.example.demo.obb" to byteArrayOf(1, 2, 3),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apkm, arm64Device)
        val selected = archive.selectedApks.map { it.manifest.splitName }

        assertEquals(AndroidPackageFormat.APKM, archive.format)
        assertEquals(4, archive.selectedApks.size)
        assertTrue(null in selected)
        assertTrue("config.arm64_v8a" in selected)
        assertTrue("config.xxhdpi" in selected)
        assertTrue("config.en" in selected)
        assertFalse("config.x86" in selected)
        assertFalse("config.fr" in selected)
        assertEquals(1, archive.obbEntries.size)
        assertEquals(ArchiveInspectionState.COMPATIBLE_WITH_OBB, archive.inspectionState)
    }

    @Test
    fun componentSummariesAreAggregatedAcrossSelectedApks() {
        val apkm = temporaryFolder.newFile("components.apkm")
        writeZip(
            apkm,
            "base.apk" to nestedApk(
                manifest(
                    components = """
                        <activity android:name=".MainActivity" android:exported="true" />
                        <receiver android:name=".ImplicitReceiver" />
                    """.trimIndent(),
                ),
            ),
            "feature_stats.apk" to nestedApk(
                manifest(
                    split = "feature.stats",
                    featureSplit = true,
                    components = """
                        <service android:name=".StatsService" android:exported="false" />
                        <provider android:name=".StatsProvider" android:exported="true" />
                    """.trimIndent(),
                ),
            ),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apkm, arm64Device)

        assertEquals(2, archive.selectedApks.size)
        assertEquals(4, archive.manifestComponents.total)
        assertEquals(1, archive.manifestComponents.activities.exported)
        assertEquals(1, archive.manifestComponents.services.notExported)
        assertEquals(1, archive.manifestComponents.receivers.exportedUnspecified)
        assertEquals(1, archive.manifestComponents.providers.exported)
    }

    @Test
    fun codeMetadataIsAggregatedOnlyAcrossSelectedApks() {
        val apkm = temporaryFolder.newFile("native-libraries.apkm")
        writeZip(
            apkm,
            "base.apk" to nestedApk(
                manifest(),
                "lib/arm64-v8a/libbase.so" to byteArrayOf(1, 2, 3),
                "classes.dex" to byteArrayOf(1, 2),
            ),
            "split_config.arm64_v8a.apk" to nestedApk(
                manifest(split = "config.arm64_v8a"),
                "lib/arm64-v8a/libfeature.so" to byteArrayOf(4, 5, 6, 7),
                "classes2.dex" to byteArrayOf(3, 4, 5),
            ),
            "split_config.x86.apk" to nestedApk(
                manifest(split = "config.x86"),
                "lib/x86/libunused.so" to ByteArray(100),
                "classes.dex" to ByteArray(100),
            ),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apkm, arm64Device)

        assertEquals(2, archive.selectedApks.size)
        assertEquals(2, archive.nativeLibraries.totalLibraryCount)
        assertEquals(7L, archive.nativeLibraries.totalUncompressedBytes)
        assertEquals(listOf("arm64-v8a"), archive.nativeLibraries.abiGroups.map { it.abi })
        assertEquals(
            NativeAbiCompatibility.PREFERRED,
            archive.nativeLibraries.abiGroups.single().compatibility,
        )
        assertEquals(2, archive.dexFiles.totalFileCount)
        assertEquals(5L, archive.dexFiles.totalUncompressedBytes)
        assertEquals(
            listOf(
                "base.apk!/classes.dex",
                "split_config.arm64_v8a.apk!/classes2.dex",
            ),
            archive.dexFiles.files.map(DexFileEntrySummary::path),
        )
    }

    @Test
    fun malformedNestedCentralDirectoryDoesNotInvalidateOtherReportSections() {
        val apkm = temporaryFolder.newFile("native-directory-damaged.apkm")
        val damagedApk = nestedApk(manifest()).also { bytes ->
            bytes[bytes.size - ZIP_EOCD_MIN_BYTES] = 0
        }
        writeZip(apkm, "base.apk" to damagedApk)

        val archive = AndroidPackageArchiveInspector.inspect(apkm, arm64Device)

        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
        assertEquals("com.example.demo", archive.baseManifest?.packageName)
        assertEquals(1, archive.nativeLibraries.failedApkCount)
        assertEquals(0, archive.nativeLibraries.totalLibraryCount)
        assertEquals(1, archive.dexFiles.failedApkCount)
        assertEquals(0, archive.dexFiles.totalFileCount)
    }

    @Test
    fun bundletoolApksInspectsOnlyTocSelectedComponentsBeyondGenericLimit() {
        val apks = temporaryFolder.newFile("large.apks")
        ZipOutputStream(FileOutputStream(apks)).use { zip ->
            zip.putNextEntry(ZipEntry("toc.pb"))
            zip.write(singleBaseToc("splits/base-master.apk"))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("splits/base-master.apk"))
            zip.write(nestedApk(manifest()))
            zip.closeEntry()

            repeat(513) { index ->
                zip.putNextEntry(ZipEntry("unused/variant-$index.apk"))
                zip.write(byteArrayOf(index.toByte()))
                zip.closeEntry()
            }
        }

        val archive = AndroidPackageArchiveInspector.inspect(apks, arm64Device)

        assertEquals(AndroidPackageSubtype.BUNDLETOOL_APKS, archive.subtype)
        assertEquals(514, archive.apkEntryCount)
        assertEquals(listOf("splits/base-master.apk"), archive.selectedApks.map { it.archivePath })
        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
    }

    @Test
    fun incompatibleAbiIsReported() {
        val apks = temporaryFolder.newFile("demo.apks")
        writeZip(
            apks,
            "base.apk" to nestedApk(manifest()),
            "split_config.x86.apk" to nestedApk(manifest(split = "config.x86")),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apks, arm64Device)

        assertEquals(ArchiveInspectionState.INCOMPATIBLE, archive.inspectionState)
        assertTrue(archive.problems.any { it.code == ArchiveProblemCode.INCOMPATIBLE_DEVICE })
    }

    @Test
    fun unsafeArchiveEntryIsRejected() {
        val xapk = temporaryFolder.newFile("unsafe.xapk")
        writeZip(xapk, "../base.apk" to nestedApk(manifest()))

        assertFailsWithIOException {
            AndroidPackageArchiveInspector.inspect(xapk, arm64Device)
        }
    }

    @Test
    fun caseDistinctApkEntriesAreAccepted() {
        val apk = temporaryFolder.newFile("case-distinct.apk")
        writeZip(
            apk,
            "AndroidManifest.xml" to manifest(),
            "assets/Example.txt" to byteArrayOf(1),
            "assets/example.txt" to byteArrayOf(2),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apk, arm64Device)

        assertEquals(AndroidPackageFormat.APK, archive.format)
        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
    }

    @Test
    fun manifestSummaryParsesSplitMetadataAndPermissions() {
        val summary = ManifestSummaryParser.parse(
            manifest(
                split = "feature.camera",
                configForSplit = "base",
                featureSplit = true,
                usesSplit = "feature.core",
                declaredPermissions = """
                    <permission android:name="com.example.permission.RUNTIME" android:protectionLevel="dangerous|instant" />
                    <permission android:name="com.example.permission.SIGNED" android:protectionLevel="signature|privileged" />
                    <permission android:name="com.example.permission.NUMERIC" android:protectionLevel="0x00000001" />
                    <permission android:name="com.example.permission.NORMAL" />
                    <permission android:name="com.example.permission.INVALID" android:protectionLevel="not-a-level" />
                    <permission android:name="com.example.permission.AMBIGUOUS" android:protectionLevel="normal|dangerous" />
                    <permission-group android:name="com.example.permission.GROUP" />
                """.trimIndent(),
            ).toString(Charsets.UTF_8),
        )

        assertEquals("feature.camera", summary.splitName)
        assertEquals("base", summary.configForSplit)
        assertTrue(summary.featureSplit)
        assertEquals(listOf("feature.core"), summary.usesSplits)
        assertEquals(listOf("android.permission.CAMERA"), summary.requestedPermissions)
        assertEquals(
            mapOf(
                "com.example.permission.NORMAL" to 0,
                "com.example.permission.NUMERIC" to 1,
                "com.example.permission.RUNTIME" to 1,
                "com.example.permission.SIGNED" to 2,
            ),
            summary.declaredPermissionProtectionLevels,
        )
    }

    private fun manifest(
        split: String? = null,
        configForSplit: String? = null,
        featureSplit: Boolean = false,
        usesSplit: String? = null,
        declaredPermissions: String = "",
        components: String = "",
    ): ByteArray {
        val splitAttributes = buildString {
            split?.let { append(""" split="$it"""") }
            configForSplit?.let { append(""" android:configForSplit="$it"""") }
            if (featureSplit) append(""" android:isFeatureSplit="true"""")
        }
        val usesSplitElement = usesSplit?.let { """<uses-split android:name="$it" />""" }.orEmpty()
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.demo"
                android:versionCode="42"
                android:versionName="1.2"$splitAttributes>
                <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35" />
                <uses-permission android:name="android.permission.CAMERA" />
                $declaredPermissions
                $usesSplitElement
                <application android:label="Demo">
                    $components
                </application>
            </manifest>
        """.trimIndent().toByteArray()
    }

    private fun nestedApk(
        manifest: ByteArray,
        vararg entries: Pair<String, ByteArray>,
    ): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write(manifest)
                zip.closeEntry()
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

    private fun singleBaseToc(basePath: String): ByteArray = ProtoWriter().apply {
        string(4, "com.example.demo")
        message(1) {
            message(2) {
                message(1) {
                    string(1, "base")
                    varint(6, 1)
                }
                message(2) {
                    string(2, basePath)
                    message(3) {
                        string(1, "base")
                        varint(2, 1)
                    }
                }
            }
        }
    }.toByteArray()

    private fun writeZip(file: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun assertFailsWithIOException(block: () -> Unit) {
        try {
            block()
        } catch (_: IOException) {
            return
        }
        throw AssertionError("Expected IOException")
    }

    private companion object {
        const val ZIP_EOCD_MIN_BYTES = 22
    }

    private class ProtoWriter {

        private val output = ByteArrayOutputStream()

        fun message(fieldNumber: Int, block: ProtoWriter.() -> Unit) {
            val bytes = ProtoWriter().apply(block).toByteArray()
            tag(fieldNumber, 2)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun string(fieldNumber: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            tag(fieldNumber, 2)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun varint(fieldNumber: Int, value: Int) {
            tag(fieldNumber, 0)
            rawVarint(value.toLong() and 0xFFFF_FFFFL)
        }

        private fun tag(fieldNumber: Int, wireType: Int) {
            rawVarint(((fieldNumber shl 3) or wireType).toLong())
        }

        private fun rawVarint(value: Long) {
            var remaining = value
            while (remaining and -0x80L != 0L) {
                output.write(((remaining and 0x7F) or 0x80).toInt())
                remaining = remaining ushr 7
            }
            output.write(remaining.toInt())
        }

        fun toByteArray(): ByteArray = output.toByteArray()
    }
}
