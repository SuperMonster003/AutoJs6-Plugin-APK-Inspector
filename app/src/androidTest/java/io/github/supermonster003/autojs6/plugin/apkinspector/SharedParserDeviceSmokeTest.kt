package io.github.supermonster003.autojs6.plugin.apkinspector

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.packagearchive.PackageDeviceSpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Only inspects generated no-code archives in a new private cache directory. Never installs. */
@RunWith(AndroidJUnit4::class)
class SharedParserDeviceSmokeTest {
    @Test fun sharedParserAndInspectorDetailsReadSixSyntheticFormatsOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "shared-parser-${UUID.randomUUID()}")
        check(directory.mkdir())
        val device = PackageDeviceSpec(35, listOf("arm64-v8a", "armeabi-v7a"), 480, listOf("en-US"))
        try {
            for (extension in listOf("apk", "apks", "xapk", "apkm", "apkz", "aab")) {
                val file = File(directory, "sample.$extension")
                instrumentation.context.assets.open("$extension-normal.$extension").use { input ->
                    file.outputStream().use(input::copyTo)
                }
                val before = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                val shared = org.autojs.plugin.packagearchive.AndroidPackageArchiveInspector.inspect(file, device)
                val report = AndroidPackageArchiveInspector.inspect(file, device)
                assertEquals(shared.format, report.format)
                assertEquals(shared.selectedApks.map { it.archivePath }, report.selectedApks.map { it.archivePath })
                assertEquals(shared.baseManifest?.packageName, report.baseManifest?.packageName)
                assertNotNull(report.baseManifest)
                assertTrue(report.problems.none { it.blocking })
                assertArrayEquals(before, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
                if (extension == "aab") {
                    assertTrue(report.aabModuleMetadata.isNotEmpty())
                    assertTrue(report.aabModuleMetadata.all { it.issue == null })
                }
                assertTrue(file.delete())
            }
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally {
            check(directory.deleteRecursively() || !directory.exists())
        }
    }
}
