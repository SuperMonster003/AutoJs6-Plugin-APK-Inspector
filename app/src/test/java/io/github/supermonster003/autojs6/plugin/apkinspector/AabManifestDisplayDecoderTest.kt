package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

class AabManifestDisplayDecoderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun protobufManifestIsDecodedWithRawAndCompiledValues() {
        val decoded = AabManifestDisplayDecoder.decodeManifest(
            manifest(packageName = "com.example.app", split = "config.en"),
        )

        assertTrue(decoded.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"))
        assertTrue(decoded.contains("xmlns:android=\"$ANDROID_NAMESPACE\""))
        assertTrue(decoded.contains("package=\"com.example.app\""))
        assertTrue(decoded.contains("split=\"config.en\""))
        assertTrue(decoded.contains("android:versionCode=\"42\""))
        assertTrue(decoded.contains("android:versionName=\"2.1\""))
        assertTrue(decoded.contains("<uses-sdk android:minSdkVersion=\"24\" />"))
        assertTrue(decoded.contains("android:debuggable=\"true\""))
        assertTrue(decoded.contains("android:label=\"Demo &amp; Co\""))
        assertTrue(decoded.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(decoded.endsWith("</manifest>"))
    }

    @Test
    fun aabPrefersBaseManifestWithoutExtractingIt() {
        val aab = createAab(
            "feature/manifest/AndroidManifest.xml" to manifest("com.example.feature"),
            "base/manifest/AndroidManifest.xml" to manifest("com.example.base"),
        )

        val decoded = AabManifestDisplayDecoder.decode(aab)

        assertTrue(decoded.contains("package=\"com.example.base\""))
        assertFalse(decoded.contains("package=\"com.example.feature\""))
    }

    @Test
    fun aabComponentSummaryIncludesBaseAndFeatureModules() {
        val aab = createAab(
            "base/manifest/AndroidManifest.xml" to manifest(
                packageName = "com.example.app",
                componentName = ".MainActivity",
                componentTag = "activity",
                componentExported = true,
            ),
            "feature/manifest/AndroidManifest.xml" to manifest(
                packageName = "com.example.app",
                split = "feature",
                componentName = ".FeatureService",
                componentTag = "service",
                componentExported = false,
            ),
        )

        val archive = AndroidPackageArchiveInspector.inspect(aab, testDevice)

        assertEquals(AndroidPackageFormat.AAB, archive.format)
        assertEquals(2, archive.aabModules.size)
        assertEquals(2, archive.manifestComponents.total)
        assertEquals(1, archive.manifestComponents.activities.exported)
        assertEquals(1, archive.manifestComponents.services.notExported)
        assertEquals(0, archive.manifestComponents.failedManifestCount)
    }

    @Test
    fun aabMetadataIncludesBundleConfigAndConditionalFeatureDelivery() {
        val aab = createAab(
            "BundleConfig.pb" to bundleConfig(),
            "base/manifest/AndroidManifest.xml" to manifest("com.example.app"),
            "camera/manifest/AndroidManifest.xml" to conditionalFeatureManifest(),
        )

        val archive = AndroidPackageArchiveInspector.inspect(aab, testDevice)

        assertEquals("1.18.2", archive.aabBundleConfig?.bundletoolVersion)
        assertEquals(AabBundleType.REGULAR, archive.aabBundleConfig?.bundleType)
        assertEquals(
            listOf(AabSplitDimension.ABI, AabSplitDimension.LANGUAGE),
            archive.aabBundleConfig?.splitDimensions?.map(AabSplitDimensionConfig::dimension),
        )
        val base = archive.aabModuleMetadata.single { metadata -> metadata.name == "base" }
        assertEquals(AabModuleType.BASE, base.type)
        assertEquals(listOf(AabModuleDeliveryMode.INSTALL_TIME), base.deliveryModes)
        val camera = archive.aabModuleMetadata.single { metadata -> metadata.name == "camera" }
        assertEquals(AabModuleType.FEATURE, camera.type)
        assertEquals(
            listOf(AabModuleDeliveryMode.INSTALL_TIME, AabModuleDeliveryMode.ON_DEMAND),
            camera.deliveryModes,
        )
        assertEquals(
            listOf(
                AabModuleConditionKind.MIN_SDK,
                AabModuleConditionKind.DEVICE_FEATURE,
                AabModuleConditionKind.INCLUDED_COUNTRY,
            ),
            camera.conditions.map(AabModuleCondition::kind),
        )
        assertEquals("US", camera.conditions.last().value)
        assertEquals(false, camera.fusingIncluded)
        assertEquals(true, camera.installTimeRemovable)
        assertEquals(0, archive.aabModuleMetadataOmittedCount)
    }

    @Test
    fun oversizedBundleConfigOnlyDegradesItsOwnSummary() {
        val aab = createAab(
            "BundleConfig.pb" to ByteArray(
                AndroidPackageArchiveInspector.MAX_AAB_BUNDLE_CONFIG_BYTES + 1,
            ),
            "base/manifest/AndroidManifest.xml" to manifest(
                packageName = "com.example.app",
                componentName = ".MainActivity",
                componentExported = true,
            ),
        )

        val archive = AndroidPackageArchiveInspector.inspect(aab, testDevice)

        assertEquals(AabMetadataIssueCode.LIMIT, archive.aabBundleConfig?.issue?.code)
        assertEquals("com.example.app", archive.baseManifest?.packageName)
        assertEquals(1, archive.manifestComponents.total)
        assertEquals(AabModuleType.BASE, archive.aabModuleMetadata.single().type)
    }

    @Test
    fun malformedBundleConfigOnlyDegradesItsOwnSummary() {
        val aab = createAab(
            "BundleConfig.pb" to byteArrayOf(0x0A, 0x7F),
            "base/manifest/AndroidManifest.xml" to manifest(
                packageName = "com.example.app",
                componentName = ".MainActivity",
                componentExported = true,
            ),
        )

        val archive = AndroidPackageArchiveInspector.inspect(aab, testDevice)

        assertEquals(AabMetadataIssueCode.INVALID, archive.aabBundleConfig?.issue?.code)
        assertEquals("com.example.app", archive.baseManifest?.packageName)
        assertEquals(1, archive.manifestComponents.total)
        assertNull(archive.aabModuleMetadata.single().issue)
    }

    @Test
    fun aabCodeMetadataIncludesAllModulesAndMarksDeviceMatch() {
        val aab = createAab(
            "base/manifest/AndroidManifest.xml" to manifest("com.example.app"),
            "base/lib/arm64-v8a/libbase.so" to byteArrayOf(1, 2, 3),
            "feature/lib/arm64-v8a/libfeature.so" to byteArrayOf(4, 5, 6, 7),
            "base/lib/x86/libbase.so" to byteArrayOf(8, 9, 10, 11, 12),
            "base/dex/classes.dex" to byteArrayOf(1, 2),
            "feature/dex/classes2.dex" to byteArrayOf(3, 4, 5),
        )

        val archive = AndroidPackageArchiveInspector.inspect(aab, testDevice)

        assertEquals(3, archive.nativeLibraries.totalLibraryCount)
        assertEquals(12L, archive.nativeLibraries.totalUncompressedBytes)
        assertEquals(listOf("arm64-v8a", "x86"), archive.nativeLibraries.abiGroups.map { it.abi })
        assertEquals(2, archive.nativeLibraries.abiGroups[0].libraryCount)
        assertEquals(
            NativeAbiCompatibility.PREFERRED,
            archive.nativeLibraries.abiGroups[0].compatibility,
        )
        assertEquals(
            NativeAbiCompatibility.UNSUPPORTED,
            archive.nativeLibraries.abiGroups[1].compatibility,
        )
        assertEquals(2, archive.dexFiles.totalFileCount)
        assertEquals(5L, archive.dexFiles.totalUncompressedBytes)
        assertEquals(
            listOf("base/dex/classes.dex", "feature/dex/classes2.dex"),
            archive.dexFiles.files.map(DexFileEntrySummary::path),
        )
    }

    @Test
    fun malformedFeatureManifestOnlyMarksComponentSummaryPartial() {
        val aab = createAab(
            "base/manifest/AndroidManifest.xml" to manifest(
                packageName = "com.example.app",
                componentName = ".MainActivity",
                componentExported = true,
            ),
            "feature/manifest/AndroidManifest.xml" to byteArrayOf(0x0A, 0x7F),
        )

        val archive = AndroidPackageArchiveInspector.inspect(aab, testDevice)

        assertEquals(1, archive.manifestComponents.total)
        assertEquals(1, archive.manifestComponents.failedManifestCount)
        assertEquals("com.example.app", archive.baseManifest?.packageName)
        assertEquals(
            AabMetadataIssueCode.INVALID,
            archive.aabModuleMetadata.single { metadata -> metadata.name == "feature" }.issue?.code,
        )
    }

    @Test
    fun aabModuleManifestScanIsBounded() {
        val entries = buildList {
            add("base/manifest/AndroidManifest.xml" to manifest("com.example.app"))
            repeat(AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFESTS) { index ->
                add(
                    "feature${index.toString().padStart(3, '0')}/manifest/AndroidManifest.xml" to
                        manifest("com.example.app", split = "feature$index"),
                )
            }
        }
        val aab = createAab(*entries.toTypedArray())

        val archive = AndroidPackageArchiveInspector.inspect(aab, testDevice)

        assertEquals(AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFESTS + 1, archive.aabModules.size)
        assertEquals(1, archive.manifestComponents.omittedManifestCount)
        assertEquals(0, archive.manifestComponents.failedManifestCount)
        assertEquals(1, archive.aabModuleMetadataOmittedCount)
        assertEquals(AndroidPackageArchiveInspector.MAX_AAB_COMPONENT_MANIFESTS, archive.aabModuleMetadata.size)
    }

    @Test
    fun aabFallsBackToAnotherModuleManifest() {
        val aab = createAab(
            "feature/manifest/AndroidManifest.xml" to manifest("com.example.feature"),
        )

        val decoded = AabManifestDisplayDecoder.decode(aab)

        assertTrue(decoded.contains("package=\"com.example.feature\""))
    }

    @Test
    fun aabEntryBudgetIsEnforcedBeforeManifestReading() {
        val aab = createAab(
            "assets/ignored.txt" to byteArrayOf(1),
            "base/manifest/AndroidManifest.xml" to manifest("com.example.base"),
        )

        captureIOException {
            AabManifestDisplayDecoder.decode(
                aab,
                AabManifestDisplayDecoder.Limits(maxZipEntries = 1),
            )
        }
    }

    @Test
    fun malformedProtobufReportsAnIOException() {
        val failure = captureIOException {
            AabManifestDisplayDecoder.decodeManifest(
                byteArrayOf(
                    fieldTag(fieldNumber = 1, wireType = 2).toByte(),
                    0x80.toByte(),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("AAB manifest protobuf"))
    }

    @Test
    fun nodeAndStringBudgetsAreEnforced() {
        val bytes = manifest("com.example.app")
        val defaults = AabManifestDisplayDecoder.Limits()

        listOf(
            defaults.copy(maxInputBytes = bytes.size - 1),
            defaults.copy(maxFields = 3),
            defaults.copy(maxNodes = 1),
            defaults.copy(maxAttributes = 1),
            defaults.copy(maxAttributesPerElement = 1),
            defaults.copy(maxNamespaces = 0),
            defaults.copy(maxDepth = 1),
            defaults.copy(maxDecodedStringChars = 8),
            defaults.copy(maxSingleStringChars = 4),
            defaults.copy(maxXmlNameChars = 4),
            defaults.copy(maxOutputChars = 64),
        ).forEach { limits ->
            captureIOException {
                AabManifestDisplayDecoder.decodeManifest(bytes, limits)
            }
        }
    }

    @Test
    fun truncatedNestedMessageIsRejected() {
        val malformed = manifest("com.example.app").copyOf().also { bytes ->
            bytes[1] = 0x7F
        }

        captureIOException {
            AabManifestDisplayDecoder.decodeManifest(malformed)
        }
    }

    private fun manifest(
        packageName: String,
        split: String = "",
        componentName: String? = null,
        componentTag: String = "activity",
        componentExported: Boolean? = null,
    ): ByteArray = ProtoWriter().apply {
        message(XML_NODE_ELEMENT_FIELD) {
            message(XML_ELEMENT_NAMESPACE_FIELD) {
                string(XML_NAMESPACE_PREFIX_FIELD, "android")
                string(XML_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
            }
            string(XML_ELEMENT_NAME_FIELD, "manifest")
            message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                string(XML_ATTRIBUTE_NAME_FIELD, "package")
                string(XML_ATTRIBUTE_VALUE_FIELD, packageName)
            }
            message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                string(XML_ATTRIBUTE_NAME_FIELD, "versionCode")
                varint(XML_ATTRIBUTE_RESOURCE_ID_FIELD, 0x0101021B)
                message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                    message(ITEM_PRIMITIVE_FIELD) {
                        varint(PRIMITIVE_INT_DECIMAL_FIELD, 42)
                    }
                }
            }
            message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                string(XML_ATTRIBUTE_NAME_FIELD, "versionName")
                message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                    message(ITEM_STRING_FIELD) {
                        string(WRAPPED_STRING_VALUE_FIELD, "2.1")
                    }
                }
            }
            if (split.isNotEmpty()) {
                message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                    string(XML_ATTRIBUTE_NAME_FIELD, "split")
                    string(XML_ATTRIBUTE_VALUE_FIELD, split)
                }
            }
            message(XML_ELEMENT_CHILD_FIELD) {
                message(XML_NODE_ELEMENT_FIELD) {
                    string(XML_ELEMENT_NAME_FIELD, "uses-sdk")
                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                        string(XML_ATTRIBUTE_NAME_FIELD, "minSdkVersion")
                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                            message(ITEM_PRIMITIVE_FIELD) {
                                varint(PRIMITIVE_INT_DECIMAL_FIELD, 24)
                            }
                        }
                    }
                }
            }
            message(XML_ELEMENT_CHILD_FIELD) {
                message(XML_NODE_ELEMENT_FIELD) {
                    string(XML_ELEMENT_NAME_FIELD, "application")
                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                        string(XML_ATTRIBUTE_NAME_FIELD, "debuggable")
                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                            message(ITEM_PRIMITIVE_FIELD) {
                                varint(PRIMITIVE_BOOLEAN_FIELD, 1)
                            }
                        }
                    }
                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                        string(XML_ATTRIBUTE_NAME_FIELD, "label")
                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                            message(ITEM_RAW_STRING_FIELD) {
                                string(WRAPPED_STRING_VALUE_FIELD, "Demo & Co")
                            }
                        }
                    }
                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                        string(XML_ATTRIBUTE_NAME_FIELD, "icon")
                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                            message(ITEM_REFERENCE_FIELD) {
                                string(REFERENCE_NAME_FIELD, "mipmap/ic_launcher")
                            }
                        }
                    }
                    componentName?.let { name ->
                        message(XML_ELEMENT_CHILD_FIELD) {
                            message(XML_NODE_ELEMENT_FIELD) {
                                string(XML_ELEMENT_NAME_FIELD, componentTag)
                                message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                                    string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                                    string(XML_ATTRIBUTE_NAME_FIELD, "name")
                                    string(XML_ATTRIBUTE_VALUE_FIELD, name)
                                }
                                componentExported?.let { exported ->
                                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                                        string(XML_ATTRIBUTE_NAME_FIELD, "exported")
                                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                                            message(ITEM_PRIMITIVE_FIELD) {
                                                varint(PRIMITIVE_BOOLEAN_FIELD, if (exported) 1 else 0)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }.toByteArray()

    private fun conditionalFeatureManifest(): ByteArray = ProtoWriter().apply {
        rootElement("manifest") {
            namespace("dist", DISTRIBUTION_NAMESPACE)
            attribute("", "package", "com.example.app")
            attribute("", "split", "camera")
            childElement(DISTRIBUTION_NAMESPACE, "module") {
                attribute(DISTRIBUTION_NAMESPACE, "type", "feature")
                childElement(DISTRIBUTION_NAMESPACE, "delivery") {
                    childElement(DISTRIBUTION_NAMESPACE, "install-time") {
                        childElement(DISTRIBUTION_NAMESPACE, "conditions") {
                            childElement(DISTRIBUTION_NAMESPACE, "min-sdk") {
                                attribute(DISTRIBUTION_NAMESPACE, "value", "26")
                            }
                            childElement(DISTRIBUTION_NAMESPACE, "device-feature") {
                                attribute(
                                    DISTRIBUTION_NAMESPACE,
                                    "name",
                                    "android.hardware.camera.ar",
                                )
                                attribute(DISTRIBUTION_NAMESPACE, "version", "2")
                            }
                            childElement(DISTRIBUTION_NAMESPACE, "user-countries") {
                                childElement(DISTRIBUTION_NAMESPACE, "country") {
                                    attribute(DISTRIBUTION_NAMESPACE, "code", "us")
                                }
                            }
                        }
                        childElement(DISTRIBUTION_NAMESPACE, "removable") {
                            attribute(DISTRIBUTION_NAMESPACE, "value", "true")
                        }
                    }
                    childElement(DISTRIBUTION_NAMESPACE, "on-demand") {}
                }
                childElement(DISTRIBUTION_NAMESPACE, "fusing") {
                    attribute(DISTRIBUTION_NAMESPACE, "include", "false")
                }
            }
        }
    }.toByteArray()

    private fun bundleConfig(): ByteArray = ProtoWriter().apply {
        message(1) {
            string(2, "1.18.2")
        }
        message(2) {
            message(1) {
                message(1) {
                    varint(1, 1)
                }
                message(1) {
                    varint(1, 3)
                }
            }
        }
    }.toByteArray()

    private fun createAab(vararg entries: Pair<String, ByteArray>): File {
        val aab = temporaryFolder.newFile("sample-${System.nanoTime()}.aab")
        ZipOutputStream(FileOutputStream(aab)).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents)
                zip.closeEntry()
            }
        }
        return aab
    }

    private fun captureIOException(block: () -> Unit): IOException {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (exception: IOException) {
            return exception
        }
    }

    private class ProtoWriter {

        private val output = ByteArrayOutputStream()

        fun message(fieldNumber: Int, block: ProtoWriter.() -> Unit) {
            val nested = ProtoWriter().apply(block).toByteArray()
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(nested.size.toLong())
            output.write(nested)
        }

        fun string(fieldNumber: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun varint(fieldNumber: Int, value: Int) {
            tag(fieldNumber, WIRE_VARINT)
            rawVarint(value.toLong() and 0xFFFFFFFFL)
        }

        fun rootElement(name: String, block: ProtoWriter.() -> Unit) {
            message(XML_NODE_ELEMENT_FIELD) {
                string(XML_ELEMENT_NAME_FIELD, name)
                block()
            }
        }

        fun childElement(
            namespaceUri: String,
            name: String,
            block: ProtoWriter.() -> Unit = {},
        ) {
            message(XML_ELEMENT_CHILD_FIELD) {
                message(XML_NODE_ELEMENT_FIELD) {
                    if (namespaceUri.isNotEmpty()) {
                        string(XML_ELEMENT_NAMESPACE_URI_FIELD, namespaceUri)
                    }
                    string(XML_ELEMENT_NAME_FIELD, name)
                    block()
                }
            }
        }

        fun namespace(prefix: String, uri: String) {
            message(XML_ELEMENT_NAMESPACE_FIELD) {
                string(XML_NAMESPACE_PREFIX_FIELD, prefix)
                string(XML_NAMESPACE_URI_FIELD, uri)
            }
        }

        fun attribute(namespaceUri: String, name: String, value: String) {
            message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                if (namespaceUri.isNotEmpty()) {
                    string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, namespaceUri)
                }
                string(XML_ATTRIBUTE_NAME_FIELD, name)
                string(XML_ATTRIBUTE_VALUE_FIELD, value)
            }
        }

        private fun tag(fieldNumber: Int, wireType: Int) {
            rawVarint(fieldTag(fieldNumber, wireType).toLong())
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
        val testDevice = PackageDeviceSpec(
            sdk = 35,
            abis = listOf("arm64-v8a"),
            densityDpi = 440,
            locales = listOf("en-US"),
        )

        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val DISTRIBUTION_NAMESPACE = "http://schemas.android.com/apk/distribution"
        const val WIRE_VARINT = 0
        const val WIRE_LENGTH_DELIMITED = 2

        const val XML_NODE_ELEMENT_FIELD = 1
        const val XML_ELEMENT_NAMESPACE_FIELD = 1
        const val XML_ELEMENT_NAMESPACE_URI_FIELD = 2
        const val XML_ELEMENT_NAME_FIELD = 3
        const val XML_ELEMENT_ATTRIBUTE_FIELD = 4
        const val XML_ELEMENT_CHILD_FIELD = 5
        const val XML_NAMESPACE_PREFIX_FIELD = 1
        const val XML_NAMESPACE_URI_FIELD = 2
        const val XML_ATTRIBUTE_NAMESPACE_URI_FIELD = 1
        const val XML_ATTRIBUTE_NAME_FIELD = 2
        const val XML_ATTRIBUTE_VALUE_FIELD = 3
        const val XML_ATTRIBUTE_RESOURCE_ID_FIELD = 5
        const val XML_ATTRIBUTE_COMPILED_ITEM_FIELD = 6
        const val ITEM_REFERENCE_FIELD = 1
        const val ITEM_STRING_FIELD = 2
        const val ITEM_RAW_STRING_FIELD = 3
        const val ITEM_PRIMITIVE_FIELD = 7
        const val WRAPPED_STRING_VALUE_FIELD = 1
        const val REFERENCE_NAME_FIELD = 3
        const val PRIMITIVE_INT_DECIMAL_FIELD = 6
        const val PRIMITIVE_BOOLEAN_FIELD = 8

        fun fieldTag(fieldNumber: Int, wireType: Int): Int =
            (fieldNumber shl 3) or wireType
    }
}
