package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DexFileSummaryTest {

    @Test
    fun apkDexFilesUseStandardRootPathsAndNaturalOrder() {
        val summary = NativeLibraryInspector.inspectApkCodeEntries(
            entries = listOf(
                entry("classes10.dex", 10),
                entry("classes2.dex", 2),
                entry("classes.dex", 1),
                entry("classes1.dex", 100),
                entry("assets/classes.dex", 200),
                entry("other.dex", 300),
            ),
            deviceAbis = emptyList(),
        ).dexFiles

        assertEquals(3, summary.totalFileCount)
        assertEquals(13L, summary.totalUncompressedBytes)
        assertEquals(
            listOf("classes.dex", "classes2.dex", "classes10.dex"),
            summary.files.map(DexFileEntrySummary::path),
        )
        assertEquals(2, summary.invalidEntryCount)
        assertTrue(summary.hasPartialResults)
    }

    @Test
    fun aabDexFilesIncludeModulePaths() {
        val summary = NativeLibraryInspector.inspectAabCodeEntries(
            entries = listOf(
                entry("feature/dex/classes2.dex", 5),
                entry("base/dex/classes.dex", 4),
                entry("feature/root/classes.dex", 100),
                entry("base/dex/metadata.dex", 200),
            ),
            deviceAbis = emptyList(),
        ).dexFiles

        assertEquals(2, summary.totalFileCount)
        assertEquals(9L, summary.totalUncompressedBytes)
        assertEquals(
            listOf("base/dex/classes.dex", "feature/dex/classes2.dex"),
            summary.files.map(DexFileEntrySummary::path),
        )
        assertEquals(1, summary.invalidEntryCount)
    }

    @Test
    fun displayLimitKeepsNaturalFirstFilesButTotalsIncludeAll() {
        val summary = NativeLibraryInspector.inspectApkCodeEntries(
            entries = listOf(
                entry("classes3.dex", 3),
                entry("classes2.dex", 2),
                entry("classes.dex", 1),
            ),
            deviceAbis = emptyList(),
            limits = NativeLibraryInspector.Limits(maxDisplayedDexFiles = 2),
        ).dexFiles

        assertEquals(3, summary.totalFileCount)
        assertEquals(6L, summary.totalUncompressedBytes)
        assertEquals(1, summary.omittedFileCount)
        assertEquals(
            listOf("classes.dex", "classes2.dex"),
            summary.files.map(DexFileEntrySummary::path),
        )
        assertTrue(summary.hasPartialResults)
    }

    @Test
    fun nestedApkCollectsDexAndNativeMetadataInOneCodeSummary() {
        val nestedApk = zipBytes(
            "AndroidManifest.xml" to "<manifest />".toByteArray(),
            "classes10.dex" to ByteArray(10),
            "classes.dex" to byteArrayOf(1),
            "lib/arm64-v8a/libdemo.so" to byteArrayOf(1, 2, 3),
        )

        val codeSummary = NativeLibraryInspector.inspectNestedPackageCode(
            input = ByteArrayInputStream(nestedApk),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertEquals(1, codeSummary.nativeLibraries.totalLibraryCount)
        assertEquals(2, codeSummary.dexFiles.totalFileCount)
        assertEquals(11L, codeSummary.dexFiles.totalUncompressedBytes)
        assertEquals(
            listOf("classes.dex", "classes10.dex"),
            codeSummary.dexFiles.files.map(DexFileEntrySummary::path),
        )
        assertFalse(codeSummary.dexFiles.hasPartialResults)
    }

    @Test
    fun malformedNestedApkMarksBothCodeSectionsPartial() {
        val codeSummary = NativeLibraryInspector.inspectNestedPackageCode(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertEquals(1, codeSummary.nativeLibraries.failedApkCount)
        assertEquals(1, codeSummary.dexFiles.failedApkCount)
        assertTrue(codeSummary.nativeLibraries.hasPartialResults)
        assertTrue(codeSummary.dexFiles.hasPartialResults)
    }

    private fun entry(name: String, size: Long): ZipEntry =
        ZipEntry(name).apply { this.size = size }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
