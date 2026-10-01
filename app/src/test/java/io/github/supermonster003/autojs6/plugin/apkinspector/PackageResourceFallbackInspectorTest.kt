package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.PackageDeviceSpec

import com.reandroid.arsc.chunk.TableBlock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

class PackageResourceFallbackInspectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val chineseDevice = PackageDeviceSpec(
        sdk = 35,
        abis = listOf("arm64-v8a"),
        densityDpi = 440,
        locales = listOf("zh-CN", "en-US"),
    )

    @Test
    fun aabResourcesPbResolvesLocalizedLabelAndDensityIcon() {
        val aab = temporaryFolder.newFile("localized.aab")
        writeZip(
            aab,
            "BundleConfig.pb" to byteArrayOf(),
            "base/manifest/AndroidManifest.xml" to aabManifest(
                label = "string/app_name",
                icon = "mipmap/ic_launcher",
            ),
            "base/resources.pb" to aabResources(),
            "base/res/mipmap-xhdpi-v4/ic_launcher.png" to ONE_PIXEL_PNG,
            "base/res/mipmap-xxhdpi-v4/ic_launcher.png" to ONE_PIXEL_PNG,
        )

        val archive = AndroidPackageArchiveInspector.inspect(aab, chineseDevice)
        val fallback = PackageResourceFallbackInspector.inspect(archive, chineseDevice)

        assertEquals(ArchiveInspectionState.AAB_SOURCE, archive.inspectionState)
        assertEquals("示例应用", fallback.label)
        assertEquals(
            "base/res/mipmap-xxhdpi-v4/ic_launcher.png",
            fallback.icon?.archivePath,
        )
        assertArrayEquals(ONE_PIXEL_PNG, fallback.icon?.bytes)
        assertTrue(fallback.issues.isEmpty())
    }

    @Test
    fun nestedArscResolvesLabelAndIconWithoutExtractingDisplayApk() {
        val table = TableBlock()
        val packageBlock = table.newPackage(0x7F, "com.example.resources")
        packageBlock.getOrCreate("", "string", "app_name").setValueAsString("Resource Demo")
        packageBlock.getOrCreate("xhdpi", "mipmap", "ic_launcher")
            .setValueAsString("res/mipmap-xhdpi-v4/ic_launcher.png")
        packageBlock.getOrCreate("xxhdpi", "mipmap", "ic_launcher")
            .setValueAsString("res/mipmap-xxhdpi-v4/ic_launcher.png")
        table.refresh()

        val nestedApk = nestedApk(
            manifest(label = "@string/app_name", icon = "@mipmap/ic_launcher"),
            "resources.arsc" to table.bytes,
            "res/mipmap-xhdpi-v4/ic_launcher.png" to ONE_PIXEL_PNG,
            "res/mipmap-xxhdpi-v4/ic_launcher.png" to ONE_PIXEL_PNG,
        )
        val apkm = temporaryFolder.newFile("resource-fallback.apkm")
        writeZip(apkm, "base.apk" to nestedApk)
        val sourceBefore = apkm.readBytes()

        val archive = AndroidPackageArchiveInspector.inspect(apkm, chineseDevice)
        val fallback = PackageResourceFallbackInspector.inspect(archive, chineseDevice)

        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
        assertEquals("Resource Demo", fallback.label)
        assertEquals("res/mipmap-xxhdpi-v4/ic_launcher.png", fallback.icon?.archivePath)
        assertTrue(fallback.issues.isEmpty())
        assertArrayEquals(sourceBefore, apkm.readBytes())
        assertFalse(temporaryFolder.root.walkTopDown().any { it.name == "base.apk" })
    }

    @Test
    fun oversizedAabResourceTableOnlyDisablesFallback() {
        val aab = temporaryFolder.newFile("resource-limit.aab")
        writeZip(
            aab,
            "BundleConfig.pb" to byteArrayOf(),
            "base/manifest/AndroidManifest.xml" to aabManifest(
                label = "string/app_name",
                icon = "mipmap/ic_launcher",
            ),
            "base/resources.pb" to aabResources(),
        )
        val archive = AndroidPackageArchiveInspector.inspect(aab, chineseDevice)

        val fallback = PackageResourceFallbackInspector.inspect(
            archive = archive,
            device = chineseDevice,
            limits = PackageResourceFallbackInspector.Limits(maxResourceTableBytes = 8),
        )

        assertEquals(ArchiveInspectionState.AAB_SOURCE, archive.inspectionState)
        assertNotNull(archive.baseManifest)
        assertEquals(
            listOf(PackageResourceFallbackIssue.RESOURCE_TABLE_LIMIT),
            fallback.issues,
        )
    }

    @Test
    fun malformedAabResourceTableOnlyDisablesFallback() {
        val aab = temporaryFolder.newFile("damaged-resources.aab")
        writeZip(
            aab,
            "BundleConfig.pb" to byteArrayOf(),
            "base/manifest/AndroidManifest.xml" to aabManifest(
                label = "string/app_name",
                icon = "mipmap/ic_launcher",
            ),
            "base/resources.pb" to byteArrayOf(0x12, 0x7F),
        )
        val archive = AndroidPackageArchiveInspector.inspect(aab, chineseDevice)

        val fallback = PackageResourceFallbackInspector.inspect(archive, chineseDevice)

        assertEquals(ArchiveInspectionState.AAB_SOURCE, archive.inspectionState)
        assertNotNull(archive.baseManifest)
        assertEquals(
            listOf(PackageResourceFallbackIssue.RESOURCE_TABLE_INVALID),
            fallback.issues,
        )
    }

    @Test
    fun malformedArscOnlyDisablesFallback() {
        val apkm = temporaryFolder.newFile("damaged-resources.apkm")
        writeZip(
            apkm,
            "base.apk" to nestedApk(
                manifest(label = "@string/app_name", icon = "@mipmap/ic_launcher"),
                "resources.arsc" to ByteArray(12),
                "classes.dex" to byteArrayOf(1, 2, 3),
            ),
        )
        val archive = AndroidPackageArchiveInspector.inspect(apkm, chineseDevice)

        val fallback = PackageResourceFallbackInspector.inspect(archive, chineseDevice)

        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
        assertEquals(1, archive.dexFiles.totalFileCount)
        assertEquals(
            listOf(PackageResourceFallbackIssue.RESOURCE_TABLE_INVALID),
            fallback.issues,
        )
    }

    @Test
    fun nestedInflatedScanLimitOnlyDisablesFallback() {
        val table = TableBlock()
        val packageBlock = table.newPackage(0x7F, "com.example.resources")
        packageBlock.getOrCreate("", "string", "app_name").setValueAsString("Late table")
        table.refresh()
        val baseApk = nestedApk(
            manifest(label = "@string/app_name"),
            "assets/compressed-padding.bin" to ByteArray(64 * 1024),
            "resources.arsc" to table.bytes,
        )
        assertTrue(baseApk.size < 16 * 1024)
        val apkm = temporaryFolder.newFile("scan-limit.apkm")
        writeZip(apkm, "base.apk" to baseApk)
        val archive = AndroidPackageArchiveInspector.inspect(apkm, chineseDevice)

        val fallback = PackageResourceFallbackInspector.inspect(
            archive = archive,
            device = chineseDevice,
            needIcon = false,
            limits = PackageResourceFallbackInspector.Limits(
                maxNestedScanBytes = 16L * 1024L,
            ),
        )

        assertEquals(ArchiveInspectionState.COMPATIBLE, archive.inspectionState)
        assertEquals(
            listOf(PackageResourceFallbackIssue.NESTED_SCAN_LIMIT),
            fallback.issues,
        )
    }

    @Test
    fun iconLimitDoesNotDiscardResolvedLabel() {
        val aab = temporaryFolder.newFile("icon-limit.aab")
        writeZip(
            aab,
            "BundleConfig.pb" to byteArrayOf(),
            "base/manifest/AndroidManifest.xml" to aabManifest(
                label = "string/app_name",
                icon = "mipmap/ic_launcher",
            ),
            "base/resources.pb" to aabResources(),
            "base/res/mipmap-xhdpi-v4/ic_launcher.png" to ONE_PIXEL_PNG,
            "base/res/mipmap-xxhdpi-v4/ic_launcher.png" to ONE_PIXEL_PNG,
        )
        val archive = AndroidPackageArchiveInspector.inspect(aab, chineseDevice)

        val fallback = PackageResourceFallbackInspector.inspect(
            archive = archive,
            device = chineseDevice,
            limits = PackageResourceFallbackInspector.Limits(maxIconBytes = 8),
        )

        assertEquals("示例应用", fallback.label)
        assertEquals(listOf(PackageResourceFallbackIssue.ICON_LIMIT), fallback.issues)
    }

    private fun aabResources(): ByteArray = ProtoWriter().apply {
        message(RESOURCE_TABLE_PACKAGE_FIELD) {
            message(PACKAGE_ID_FIELD) { varint(WRAPPED_ID_VALUE_FIELD, 0x7F) }
            string(PACKAGE_NAME_FIELD, "com.example.resources")
            message(PACKAGE_TYPE_FIELD) {
                message(TYPE_ID_FIELD) { varint(WRAPPED_ID_VALUE_FIELD, 1) }
                string(TYPE_NAME_FIELD, "string")
                message(TYPE_ENTRY_FIELD) {
                    message(ENTRY_ID_FIELD) { varint(WRAPPED_ID_VALUE_FIELD, 0) }
                    string(ENTRY_NAME_FIELD, "app_name")
                    message(ENTRY_CONFIG_VALUE_FIELD) {
                        message(CONFIG_VALUE_CONFIGURATION_FIELD) {}
                        message(CONFIG_VALUE_VALUE_FIELD) {
                            message(VALUE_ITEM_FIELD) {
                                message(ITEM_STRING_FIELD) {
                                    string(WRAPPED_STRING_VALUE_FIELD, "Demo")
                                }
                            }
                        }
                    }
                    message(ENTRY_CONFIG_VALUE_FIELD) {
                        message(CONFIG_VALUE_CONFIGURATION_FIELD) {
                            string(CONFIGURATION_LOCALE_FIELD, "zh-CN")
                        }
                        message(CONFIG_VALUE_VALUE_FIELD) {
                            message(VALUE_ITEM_FIELD) {
                                message(ITEM_STRING_FIELD) {
                                    string(WRAPPED_STRING_VALUE_FIELD, "示例应用")
                                }
                            }
                        }
                    }
                }
            }
            message(PACKAGE_TYPE_FIELD) {
                message(TYPE_ID_FIELD) { varint(WRAPPED_ID_VALUE_FIELD, 2) }
                string(TYPE_NAME_FIELD, "mipmap")
                message(TYPE_ENTRY_FIELD) {
                    message(ENTRY_ID_FIELD) { varint(WRAPPED_ID_VALUE_FIELD, 0) }
                    string(ENTRY_NAME_FIELD, "ic_launcher")
                    fileConfigValue(
                        density = 320,
                        path = "res/mipmap-xhdpi-v4/ic_launcher.png",
                    )
                    fileConfigValue(
                        density = 480,
                        path = "res/mipmap-xxhdpi-v4/ic_launcher.png",
                    )
                }
            }
        }
    }.toByteArray()

    private fun ProtoWriter.fileConfigValue(density: Int, path: String) {
        message(ENTRY_CONFIG_VALUE_FIELD) {
            message(CONFIG_VALUE_CONFIGURATION_FIELD) {
                varint(CONFIGURATION_DENSITY_FIELD, density)
            }
            message(CONFIG_VALUE_VALUE_FIELD) {
                message(VALUE_ITEM_FIELD) {
                    message(ITEM_FILE_FIELD) {
                        string(FILE_REFERENCE_PATH_FIELD, path)
                        varint(FILE_REFERENCE_TYPE_FIELD, 1)
                    }
                }
            }
        }
    }

    private fun aabManifest(label: String, icon: String): ByteArray = ProtoWriter().apply {
        message(XML_NODE_ELEMENT_FIELD) {
            message(XML_ELEMENT_NAMESPACE_FIELD) {
                string(XML_NAMESPACE_PREFIX_FIELD, "android")
                string(XML_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
            }
            string(XML_ELEMENT_NAME_FIELD, "manifest")
            message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                string(XML_ATTRIBUTE_NAME_FIELD, "package")
                string(XML_ATTRIBUTE_VALUE_FIELD, "com.example.resources")
            }
            message(XML_ELEMENT_CHILD_FIELD) {
                message(XML_NODE_ELEMENT_FIELD) {
                    string(XML_ELEMENT_NAME_FIELD, "application")
                    resourceAttribute("label", label)
                    resourceAttribute("icon", icon)
                }
            }
        }
    }.toByteArray()

    private fun ProtoWriter.resourceAttribute(name: String, reference: String) {
        message(XML_ELEMENT_ATTRIBUTE_FIELD) {
            string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
            string(XML_ATTRIBUTE_NAME_FIELD, name)
            message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                message(ITEM_REFERENCE_FIELD) {
                    string(REFERENCE_NAME_FIELD, reference)
                }
            }
        }
    }

    private fun manifest(
        label: String = "Demo",
        icon: String? = null,
    ): ByteArray = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="$ANDROID_NAMESPACE"
            package="com.example.resources"
            android:versionCode="1">
            <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35" />
            <application android:label="$label"${icon?.let { " android:icon=\"$it\"" }.orEmpty()} />
        </manifest>
    """.trimIndent().toByteArray()

    private fun nestedApk(
        manifest: ByteArray,
        vararg entries: Pair<String, ByteArray>,
    ): ByteArray = ByteArrayOutputStream().use { output ->
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

    private fun writeZip(file: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private class ProtoWriter {

        private val output = ByteArrayOutputStream()

        fun message(fieldNumber: Int, block: ProtoWriter.() -> Unit) {
            val bytes = ProtoWriter().apply(block).toByteArray()
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun string(fieldNumber: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun varint(fieldNumber: Int, value: Int) {
            tag(fieldNumber, WIRE_VARINT)
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

    private companion object {
        val ONE_PIXEL_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )

        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val WIRE_VARINT = 0
        const val WIRE_LENGTH_DELIMITED = 2

        const val RESOURCE_TABLE_PACKAGE_FIELD = 2
        const val PACKAGE_ID_FIELD = 1
        const val PACKAGE_NAME_FIELD = 2
        const val PACKAGE_TYPE_FIELD = 3
        const val TYPE_ID_FIELD = 1
        const val TYPE_NAME_FIELD = 2
        const val TYPE_ENTRY_FIELD = 3
        const val ENTRY_ID_FIELD = 1
        const val ENTRY_NAME_FIELD = 2
        const val ENTRY_CONFIG_VALUE_FIELD = 6
        const val CONFIG_VALUE_CONFIGURATION_FIELD = 1
        const val CONFIG_VALUE_VALUE_FIELD = 2
        const val VALUE_ITEM_FIELD = 4
        const val ITEM_REFERENCE_FIELD = 1
        const val ITEM_STRING_FIELD = 2
        const val ITEM_FILE_FIELD = 5
        const val WRAPPED_ID_VALUE_FIELD = 1
        const val WRAPPED_STRING_VALUE_FIELD = 1
        const val FILE_REFERENCE_PATH_FIELD = 1
        const val FILE_REFERENCE_TYPE_FIELD = 2
        const val CONFIGURATION_LOCALE_FIELD = 3
        const val CONFIGURATION_DENSITY_FIELD = 18

        const val XML_NODE_ELEMENT_FIELD = 1
        const val XML_ELEMENT_NAMESPACE_FIELD = 1
        const val XML_ELEMENT_NAME_FIELD = 3
        const val XML_ELEMENT_ATTRIBUTE_FIELD = 4
        const val XML_ELEMENT_CHILD_FIELD = 5
        const val XML_NAMESPACE_PREFIX_FIELD = 1
        const val XML_NAMESPACE_URI_FIELD = 2
        const val XML_ATTRIBUTE_NAMESPACE_URI_FIELD = 1
        const val XML_ATTRIBUTE_NAME_FIELD = 2
        const val XML_ATTRIBUTE_VALUE_FIELD = 3
        const val XML_ATTRIBUTE_COMPILED_ITEM_FIELD = 6
        const val REFERENCE_NAME_FIELD = 3
    }
}
