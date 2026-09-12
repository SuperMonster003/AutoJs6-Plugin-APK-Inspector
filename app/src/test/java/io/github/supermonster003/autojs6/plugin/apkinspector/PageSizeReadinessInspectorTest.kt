package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PageSizeReadinessInspectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun alignedAndUnalignedLibrariesAreCountedPerAbi() {
        val apk = zipFile(
            "AndroidManifest.xml" to byteArrayOf(1),
            "lib/arm64-v8a/libaligned.so" to elf64(0x4000, 0x4000),
            "lib/arm64-v8a/libunaligned.so" to elf64(0x4000, 0x1000),
            "lib/x86_64/libaligned.so" to elf64(0x10000),
            "lib/armeabi-v7a/libignored.so" to elf32(0x1000),
            "lib/x86/libignored.so" to elf32(0x1000),
            "assets/copied.so" to elf64(0x1000),
        )
        val summary = inspect(apk, extractNativeLibs = true)

        assertEquals(PageSizeReadinessState.NOT_READY, summary.state)
        assertEquals(true, summary.extractNativeLibs)
        assertFalse(summary.zipOffsetsChecked)
        assertEquals(listOf("arm64-v8a", "x86_64"), summary.abis.map(PageSizeAbiReadiness::abi))
        val arm64 = summary.abis[0]
        assertEquals(2, arm64.libraryCount)
        assertEquals(1, arm64.alignedCount)
        assertEquals(1, arm64.unalignedCount)
        assertEquals(0, arm64.unreadableCount)
        assertEquals(listOf("libunaligned.so"), arm64.examples)
        val x8664 = summary.abis[1]
        assertEquals(1, x8664.alignedCount)
        assertEquals(0, x8664.unalignedCount)
        assertEquals(3, summary.libraryCount)
        assertEquals(1, summary.unalignedCount)
    }

    @Test
    fun fullyAlignedPackageIsReady() {
        val apk = zipFile(
            "lib/arm64-v8a/liba.so" to elf64(0x4000, 0x4000, 0x4000),
            "lib/arm64-v8a/libb.so" to elf64(0x10000),
        )
        val summary = inspect(apk, extractNativeLibs = true)

        assertEquals(PageSizeReadinessState.READY, summary.state)
        assertEquals(2, summary.libraryCount)
        assertEquals(0, summary.unalignedCount)
        assertTrue(summary.abis.single().examples.isEmpty())
    }

    @Test
    fun unreadableLibraryLeavesPackageUnverified() {
        val apk = zipFile(
            "lib/arm64-v8a/libgood.so" to elf64(0x4000),
            "lib/arm64-v8a/libnotelf.so" to byteArrayOf(1, 2, 3, 4),
            "lib/arm64-v8a/libnoload.so" to elf64(),
        )
        val summary = inspect(apk, extractNativeLibs = true)

        assertEquals(PageSizeReadinessState.UNVERIFIED, summary.state)
        assertEquals(2, summary.unreadableCount)
        assertEquals(0, summary.unalignedCount)
        assertEquals(listOf("libnotelf.so", "libnoload.so"), summary.abis.single().examples)
    }

    @Test
    fun packageWithoutSixtyFourBitLibrariesIsNeutral() {
        val apk = zipFile(
            "lib/armeabi-v7a/libonly32.so" to elf32(0x1000),
            "classes.dex" to byteArrayOf(9),
        )
        val summary = inspect(apk, extractNativeLibs = null)

        assertEquals(PageSizeReadinessState.NO_64BIT_LIBRARIES, summary.state)
        assertNull(summary.extractNativeLibs)
        assertTrue(summary.abis.isEmpty())
    }

    @Test
    fun storedEntriesAreCheckedForZipOffsetsOnlyWhenLibrariesStayInsideTheApk() {
        val aligned = elf64(0x4000)
        val stored = zipFile(
            "lib/arm64-v8a/libstored.so" to aligned,
            stored = setOf("lib/arm64-v8a/libstored.so"),
        )

        // The first local header starts at offset 0, so the data begins at 30 + name length: never 16 KB aligned.
        val kept = inspect(stored, extractNativeLibs = false)
        assertEquals(PageSizeReadinessState.NOT_READY, kept.state)
        assertTrue(kept.zipOffsetsChecked)
        assertEquals(1, kept.zipMisalignedCount)
        assertEquals(0, kept.unalignedCount)
        assertEquals(listOf("libstored.so"), kept.abis.single().examples)

        // With extractNativeLibs="true" the installer copies libraries out, so the ZIP layout is irrelevant.
        val extracted = inspect(stored, extractNativeLibs = true)
        assertEquals(PageSizeReadinessState.READY, extracted.state)
        assertFalse(extracted.zipOffsetsChecked)
        assertEquals(0, extracted.zipMisalignedCount)
    }

    @Test
    fun storedEntryAtAlignedOffsetPassesTheZipOffsetCheck() {
        val aligned = elf64(0x4000)
        val padding = "padding.bin"
        // Local header (30) + name length pushes the next local header so that its data offset lands on 16 KB.
        val nameLength = "lib/arm64-v8a/libstored.so".length
        val firstDataOffset = 30 + padding.length
        val paddingSize = 16_384 - firstDataOffset - 30 - nameLength
        val stored = zipFile(
            padding to ByteArray(paddingSize),
            "lib/arm64-v8a/libstored.so" to aligned,
            stored = setOf(padding, "lib/arm64-v8a/libstored.so"),
        )
        val summary = inspect(stored, extractNativeLibs = false)

        assertEquals(PageSizeReadinessState.READY, summary.state)
        assertTrue(summary.zipOffsetsChecked)
        assertEquals(0, summary.zipMisalignedCount)
    }

    @Test
    fun aabModulesUseTheBundleLibraryLayout() {
        val bundle = zipFile(
            "base/lib/arm64-v8a/libbase.so" to elf64(0x4000),
            "feature/lib/x86_64/libfeature.so" to elf64(0x1000),
            "base/lib/armeabi-v7a/libignored.so" to elf32(0x1000),
        )
        val summary = ZipFile(bundle).use { zip ->
            PageSizeReadinessInspector.inspectAab(zip, zip.entries().toList())
        }

        assertEquals(PageSizeReadinessState.NOT_READY, summary.state)
        assertEquals(listOf("arm64-v8a", "x86_64"), summary.abis.map(PageSizeAbiReadiness::abi))
        assertEquals(1, summary.abis[1].unalignedCount)
        assertNull(summary.extractNativeLibs)
        assertFalse(summary.zipOffsetsChecked)
    }

    @Test
    fun libraryLimitMarksOmittedEntries() {
        val apk = zipFile(
            "lib/arm64-v8a/liba.so" to elf64(0x4000),
            "lib/arm64-v8a/libb.so" to elf64(0x4000),
            "lib/arm64-v8a/libc.so" to elf64(0x4000),
        )
        val summary = ZipFile(apk).use { zip ->
            PageSizeReadinessInspector.inspectApk(apk, zip, zip.entries().toList(), true, maxLibraries = 2)
        }

        assertEquals(PageSizeReadinessState.UNVERIFIED, summary.state)
        assertEquals(1, summary.omittedLibraryCount)
        assertEquals(2, summary.libraryCount)
    }

    @Test
    fun elfParserReadsThirtyTwoBitAndBigEndianTables() {
        assertEquals(0x1000L, PageSizeReadinessInspector.readMinimumLoadAlignment(ByteArrayInputStream(elf32(0x1000, 0x4000))))
        assertEquals(0x4000L, PageSizeReadinessInspector.readMinimumLoadAlignment(ByteArrayInputStream(elf64(0x4000, littleEndian = false))))
        assertNull(PageSizeReadinessInspector.readMinimumLoadAlignment(ByteArrayInputStream(byteArrayOf(0x7F, 'E'.code.toByte()))))
        assertNull(PageSizeReadinessInspector.readMinimumLoadAlignment(ByteArrayInputStream(elf64(0x4000).copyOf(70))))
    }

    @Test
    fun manifestParserExposesExtractNativeLibs() {
        val declaredFalse = ManifestSummaryParser.parse(
            """<manifest package="com.example"><application android:extractNativeLibs="false" /></manifest>""",
        )
        val declaredTrue = ManifestSummaryParser.parse(
            """<manifest package="com.example"><application android:extractNativeLibs="true" /></manifest>""",
        )
        val undeclared = ManifestSummaryParser.parse("""<manifest package="com.example"><application /></manifest>""")

        assertEquals(false, declaredFalse.extractNativeLibs)
        assertEquals(true, declaredTrue.extractNativeLibs)
        assertNull(undeclared.extractNativeLibs)
    }

    @Test
    fun nestedContainersAreNotEvaluated() {
        assertEquals(PageSizeReadinessState.NOT_EVALUATED, PageSizeReadinessSummary.NOT_EVALUATED.state)
        assertEquals(0, PageSizeReadinessSummary.NOT_EVALUATED.libraryCount)
    }

    private fun inspect(apk: File, extractNativeLibs: Boolean?): PageSizeReadinessSummary =
        ZipFile(apk).use { zip ->
            PageSizeReadinessInspector.inspectApk(apk, zip, zip.entries().toList(), extractNativeLibs)
        }

    private fun zipFile(vararg entries: Pair<String, ByteArray>, stored: Set<String> = emptySet()): File {
        val file = temporaryFolder.newFile("${System.nanoTime()}.zip")
        FileOutputStream(file).use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    val entry = ZipEntry(name)
                    if (name in stored) {
                        entry.method = ZipEntry.STORED
                        entry.size = bytes.size.toLong()
                        entry.compressedSize = bytes.size.toLong()
                        entry.crc = CRC32().apply { update(bytes) }.value
                    }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
        return file
    }

    /** Minimal ELF64 image: header plus one PT_LOAD program header per alignment value. */
    private fun elf64(vararg loadAlignments: Long, littleEndian: Boolean = true): ByteArray {
        val phnum = loadAlignments.size + 1
        val buffer = ByteBuffer.allocate(64 + 56 * phnum)
            .order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        buffer.put(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, if (littleEndian) 1 else 2, 1))
        buffer.position(0x20)
        buffer.putLong(64) // e_phoff
        buffer.position(0x36)
        buffer.putShort(56) // e_phentsize
        buffer.putShort(phnum.toShort()) // e_phnum
        buffer.position(64)
        // A PT_NOTE first, to prove that only PT_LOAD is considered.
        buffer.putInt(4).putInt(4).putLong(0).putLong(0).putLong(0).putLong(0).putLong(0).putLong(0x1)
        loadAlignments.forEach { align ->
            buffer.putInt(1).putInt(5).putLong(0).putLong(0).putLong(0).putLong(0x100).putLong(0x100).putLong(align)
        }
        return buffer.array()
    }

    /** Minimal ELF32 image with one PT_LOAD per alignment value. */
    private fun elf32(vararg loadAlignments: Long): ByteArray {
        val phnum = loadAlignments.size
        val buffer = ByteBuffer.allocate(52 + 32 * phnum).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 1, 1, 1))
        buffer.position(0x1C)
        buffer.putInt(52) // e_phoff
        buffer.position(0x2A)
        buffer.putShort(32) // e_phentsize
        buffer.putShort(phnum.toShort()) // e_phnum
        buffer.position(52)
        loadAlignments.forEach { align ->
            buffer.putInt(1).putInt(0).putInt(0).putInt(0).putInt(0x100).putInt(0x100).putInt(5).putInt(align.toInt())
        }
        return buffer.array()
    }
}
