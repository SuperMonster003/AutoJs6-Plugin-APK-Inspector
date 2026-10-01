package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.AabManifestDisplayDecoder
import org.autojs.plugin.packagearchive.PackageDeviceSpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest

class SharedParserIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val device = PackageDeviceSpec(35, listOf("arm64-v8a", "armeabi-v7a"), 480, listOf("en-US"))

    @Test fun allSixFormatsUseSharedSelectionWithoutPreparingInstallationOrChangingSources() {
        for (extension in listOf("apk", "apks", "xapk", "apkm", "apkz", "aab")) {
            val file = temporary.newFile("sample.$extension")
            javaClass.classLoader!!.getResourceAsStream("privacy-neutral-fixtures/$extension-normal.$extension")!!.use {
                file.outputStream().use(it::copyTo)
            }
            val before = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            val shared = org.autojs.plugin.packagearchive.AndroidPackageArchiveInspector.inspect(file, device)
            val report = AndroidPackageArchiveInspector.inspect(file, device)
            assertEquals(shared.format, report.format)
            assertEquals(shared.subtype, report.subtype)
            assertEquals(shared.selectedApks.map { it.archivePath }, report.selectedApks.map { it.archivePath })
            assertEquals(shared.baseManifest?.packageName, report.baseManifest?.packageName)
            assertEquals(shared.problems, report.problems)
            assertTrue(shared.selectedApks.all { it.sha256 == null })
            assertArrayEquals(before, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
            if (extension == "aab") assertTrue(report.aabModuleMetadata.all { it.issue == null })
        }
        assertEquals(6, temporary.root.listFiles()!!.size)
    }

    @Test fun deliveryMetadataPreservesNamespaceAndEscapedValuesFromSharedXml() {
        val root = AabModuleManifest.parse("""<manifest xmlns:d="http://schemas.android.com/apk/distribution">
            <d:module><d:delivery><d:install-time><d:conditions>
            <d:device-feature d:name="camera&amp;depth" d:version="2" />
            </d:conditions></d:install-time></d:delivery></d:module></manifest>""")
        val metadata = AabModuleMetadataParser.parse(root, "camera")
        assertEquals("camera&depth", metadata.conditions.single().value)
        assertEquals(2, metadata.conditions.single().version)
    }

    @Test fun deliveryXmlRejectsDoctypeMalformedInputAndEachBoundedTreeDimension() {
        val limits = AabManifestDisplayDecoder.Limits()
        assertThrows(IOException::class.java) { AabModuleManifest.parse("<!DOCTYPE manifest SYSTEM 'file:///ignored'><manifest/>") }
        assertThrows(IOException::class.java) { AabModuleManifest.parse("<manifest>") }
        assertThrows(IOException::class.java) { AabModuleManifest.parse("<manifest/>", limits.copy(maxOutputChars = 1)) }
        assertThrows(IOException::class.java) { AabModuleManifest.parse("<manifest><child/></manifest>", limits.copy(maxDepth = 1)) }
        assertThrows(IOException::class.java) { AabModuleManifest.parse("<manifest><child/></manifest>", limits.copy(maxNodes = 1)) }
        assertThrows(IOException::class.java) { AabModuleManifest.parse("<manifest a='1'/>", limits.copy(maxAttributes = 0)) }
        assertThrows(IOException::class.java) { AabModuleManifest.parse("<manifest a='1'/>", limits.copy(maxAttributesPerElement = 0)) }
    }

    @Test fun sharedDependencyDoesNotAuthorizeImplicitV4Sidecars() {
        val apk = temporary.newFile("owned.apk")
        javaClass.classLoader!!.getResourceAsStream("privacy-neutral-fixtures/apk-normal.apk")!!.use {
            apk.outputStream().use(it::copyTo)
        }
        temporary.newFile("owned.apk.idsig").writeText("Not an authorized sidecar")
        assertNull(ApkSignatureDetector.detectSchemes(apk))
    }
}
