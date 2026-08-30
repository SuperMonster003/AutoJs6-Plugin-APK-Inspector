package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativeLibrarySummaryTest {

    @Test
    fun apkLibrariesAreGroupedByAbiAndDevicePreference() {
        val summary = NativeLibraryInspector.inspectApkEntries(
            entries = listOf(
                entry("lib/arm64-v8a/libalpha.so", 10),
                entry("lib/arm64-v8a/libbeta.so", 20),
                entry("lib/armeabi-v7a/libalpha.so", 7),
                entry("lib/x86/libalpha.so", 5),
                entry("assets/copied.so", 99),
                entry("lib/bad abi/libinvalid.so", 1),
            ),
            deviceAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        assertEquals(4, summary.totalLibraryCount)
        assertEquals(42L, summary.totalUncompressedBytes)
        assertEquals(1, summary.invalidEntryCount)
        assertEquals(
            listOf("arm64-v8a", "armeabi-v7a", "x86"),
            summary.abiGroups.map(NativeLibraryAbiSummary::abi),
        )
        assertEquals(NativeAbiCompatibility.PREFERRED, summary.abiGroups[0].compatibility)
        assertEquals(NativeAbiCompatibility.COMPATIBLE, summary.abiGroups[1].compatibility)
        assertEquals(NativeAbiCompatibility.UNSUPPORTED, summary.abiGroups[2].compatibility)
        assertEquals(2, summary.abiGroups[0].libraryCount)
        assertEquals(30L, summary.abiGroups[0].uncompressedBytes)
    }

    @Test
    fun aabModuleLibrariesAreAggregatedByAbi() {
        val summary = NativeLibraryInspector.inspectAabEntries(
            entries = listOf(
                entry("base/lib/arm64-v8a/libbase.so", 11),
                entry("feature/lib/arm64-v8a/libfeature.so", 13),
                entry("base/lib/x86_64/libbase.so", 17),
                entry("base/root/lib/arm64-v8a/libignored.so", 19),
            ),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertEquals(3, summary.totalLibraryCount)
        assertEquals(41L, summary.totalUncompressedBytes)
        assertEquals(2, summary.abiGroups.size)
        assertEquals("arm64-v8a", summary.abiGroups[0].abi)
        assertEquals(2, summary.abiGroups[0].libraryCount)
        assertEquals(NativeAbiCompatibility.PREFERRED, summary.abiGroups[0].compatibility)
        assertEquals(NativeAbiCompatibility.UNSUPPORTED, summary.abiGroups[1].compatibility)
        assertFalse(summary.hasPartialResults)
    }

    @Test
    fun nestedApkUsesCentralDirectoryWithoutInflatingLibraries() {
        val nestedApk = zipBytes(
            "AndroidManifest.xml" to "<manifest />".toByteArray(),
            "lib/arm64-v8a/libalpha.so" to ByteArray(1_024) { 0x5A },
            "lib/x86/libalpha.so" to ByteArray(2_048) { 0x33 },
        )

        val summary = NativeLibraryInspector.inspectNestedApk(
            input = ByteArrayInputStream(nestedApk),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertEquals(2, summary.totalLibraryCount)
        assertEquals(3_072L, summary.totalUncompressedBytes)
        assertEquals(0, summary.failedApkCount)
        assertEquals(0, summary.omittedApkCount)
        assertEquals("arm64-v8a", summary.abiGroups[0].abi)
    }

    @Test
    fun malformedNestedApkOnlyMarksNativeSummaryPartial() {
        val summary = NativeLibraryInspector.inspectNestedApk(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertEquals(0, summary.totalLibraryCount)
        assertEquals(1, summary.failedApkCount)
        assertTrue(summary.hasPartialResults)
    }

    @Test
    fun nestedApkInputBudgetProducesAnOmittedPartialResult() {
        val nestedApk = zipBytes(
            "AndroidManifest.xml" to "<manifest />".toByteArray(),
            "lib/arm64-v8a/libalpha.so" to ByteArray(512) { 0x41 },
        )
        val limits = NativeLibraryInspector.Limits(
            maxNestedApkScanBytes = nestedApk.size.toLong() - 1L,
        )

        val summary = NativeLibraryInspector.inspectNestedApk(
            input = ByteArrayInputStream(nestedApk),
            deviceAbis = listOf("arm64-v8a"),
            limits = limits,
        )

        assertEquals(0, summary.totalLibraryCount)
        assertEquals(0, summary.failedApkCount)
        assertEquals(1, summary.omittedApkCount)
        assertTrue(summary.nestedScanLimitReached)
    }

    @Test
    fun libraryAndDisplayedAbiCountsAreBounded() {
        val entries = listOf(
            entry("lib/abi-a/liba.so", 1),
            entry("lib/abi-b/libb.so", 2),
            entry("lib/abi-c/libc.so", 3),
            entry("lib/abi-d/libd.so", 4),
        )
        val limits = NativeLibraryInspector.Limits(
            maxNativeLibraryEntries = 3,
            maxDisplayedAbis = 1,
        )

        val summary = NativeLibraryInspector.inspectApkEntries(
            entries = entries,
            deviceAbis = listOf("abi-b"),
            limits = limits,
        )

        assertEquals(3, summary.totalLibraryCount)
        assertEquals(6L, summary.totalUncompressedBytes)
        assertEquals(1, summary.omittedLibraryCount)
        assertEquals(2, summary.omittedAbiCount)
        assertEquals(listOf("abi-b"), summary.abiGroups.map(NativeLibraryAbiSummary::abi))
        assertTrue(summary.hasPartialResults)
    }

    @Test
    fun nestedCentralDirectoryEntryCountIsBounded() {
        val nestedApk = zipBytes(
            "AndroidManifest.xml" to "<manifest />".toByteArray(),
            "lib/arm64-v8a/libalpha.so" to byteArrayOf(1),
        )
        val limits = NativeLibraryInspector.Limits(maxNestedApkEntries = 1)

        val summary = NativeLibraryInspector.inspectNestedApk(
            input = ByteArrayInputStream(nestedApk),
            deviceAbis = listOf("arm64-v8a"),
            limits = limits,
        )

        assertEquals(0, summary.totalLibraryCount)
        assertTrue(summary.nestedScanLimitReached)
        assertTrue(summary.hasPartialResults)
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
