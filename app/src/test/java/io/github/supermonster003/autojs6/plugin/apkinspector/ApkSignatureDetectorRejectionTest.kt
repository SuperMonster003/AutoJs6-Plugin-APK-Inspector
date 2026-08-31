package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkSignatureDetectorRejectionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun everySigningBlockParseRejectionHasAnExplicitFailClosedCase() {
        val covered = linkedSetOf<ApkSignatureParseRejection>()
        fun expect(
            expected: ApkSignatureParseRejection,
            file: File,
        ) {
            val failure = captureParseException {
                ApkSignatureDetector.readSigningBlockIds(file)
            }
            assertEquals(expected, failure.rejection)
            assertTrue(failure.message.orEmpty().isNotBlank())
            assertNull(ApkSignatureDetector.detectSchemes(file))
            covered += expected
        }

        expect(
            ApkSignatureParseRejection.ZIP_TOO_SMALL,
            writeRaw("too-small.apk", ByteArray(ZIP_EOCD_MIN_BYTES - 1)),
        )
        expect(
            ApkSignatureParseRejection.ZIP_EOCD_MISSING,
            writeRaw("missing-eocd.apk", ByteArray(ZIP_EOCD_MIN_BYTES)),
        )
        expect(
            ApkSignatureParseRejection.CENTRAL_DIRECTORY_OFFSET,
            writeArchive("bad-central-offset.apk", prefix = byteArrayOf(), centralDirectoryOffset = 1),
        )

        val unsignedSizeBlock = signingBlock(byteArrayOf()).apply {
            littleEndianLong(Long.MIN_VALUE).copyInto(this, size - SIGNING_BLOCK_FOOTER_BYTES)
        }
        expect(
            ApkSignatureParseRejection.UNSIGNED_64_BIT,
            writeArchive("unsigned-size.apk", unsignedSizeBlock),
        )
        expect(
            ApkSignatureParseRejection.SIGNING_BLOCK_SIZE,
            writeArchive(
                "small-block-size.apk",
                signingBlock(byteArrayOf(), footerSize = 23L),
            ),
        )
        expect(
            ApkSignatureParseRejection.SIGNING_BLOCK_SIZE,
            writeArchive(
                "large-block-size.apk",
                signingBlock(byteArrayOf(), footerSize = 25L),
            ),
        )
        expect(
            ApkSignatureParseRejection.SIGNING_BLOCK_SIZE_MISMATCH,
            writeArchive(
                "mismatched-block-size.apk",
                signingBlock(byteArrayOf(), headerSize = 25L, footerSize = 24L),
            ),
        )
        expect(
            ApkSignatureParseRejection.SIGNING_BLOCK_PAIR_ALIGNMENT,
            writeArchive(
                "truncated-pair-header.apk",
                signingBlock(byteArrayOf(1)),
            ),
        )
        expect(
            ApkSignatureParseRejection.SIGNING_BLOCK_PAIR_SIZE,
            writeArchive(
                "small-pair.apk",
                signingBlock(littleEndianLong(3L)),
            ),
        )
        expect(
            ApkSignatureParseRejection.SIGNING_BLOCK_PAIR_SIZE,
            writeArchive(
                "large-pair.apk",
                signingBlock(littleEndianLong(5L) + littleEndianInt(0x7109871A)),
            ),
        )

        assertEquals(ApkSignatureParseRejection.entries.toSet(), covered)
    }

    @Test
    fun unsignedShapesReturnEmptyWithoutBeingMisreportedAsMalformedSignatures() {
        val ordinaryZip = writeArchive("ordinary-empty.apk", prefix = byteArrayOf())
        assertTrue(ApkSignatureDetector.readSigningBlockIds(ordinaryZip).isEmpty())

        val comment = "fixture".toByteArray()
        val commentedZip = writeRaw(
            "commented-empty.apk",
            emptyZipEocd(
                centralDirectoryOffset = 0,
                declaredCommentLength = comment.size,
            ) + comment,
        )
        assertTrue(ApkSignatureDetector.readSigningBlockIds(commentedZip).isEmpty())

        val wrongMagic = writeArchive(
            "wrong-magic.apk",
            signingBlock(
                entries = byteArrayOf(),
                magic = ByteArray(SIGNING_BLOCK_MAGIC_BYTES),
            ),
        )
        assertTrue(ApkSignatureDetector.readSigningBlockIds(wrongMagic).isEmpty())
        assertNull(ApkSignatureDetector.detectSchemes(wrongMagic))

        val invalidComment = writeRaw(
            "invalid-comment.apk",
            emptyZipEocd(centralDirectoryOffset = 0, declaredCommentLength = 1),
        )
        val failure = captureParseException {
            ApkSignatureDetector.readSigningBlockIds(invalidComment)
        }
        assertEquals(ApkSignatureParseRejection.ZIP_EOCD_MISSING, failure.rejection)
    }

    @Test
    fun v31IsReportedAsV3AndDuplicateIdsAreCollapsed() {
        val entries = signingPair(BLOCK_ID_V3_1) +
            signingPair(BLOCK_ID_V3_1) +
            signingPair(0x12345678)
        val apk = writeArchive("v31.apk", signingBlock(entries))

        assertEquals(
            linkedSetOf(BLOCK_ID_V3_1, 0x12345678),
            ApkSignatureDetector.readSigningBlockIds(apk),
        )
        assertEquals("V3", ApkSignatureDetector.detectSchemes(apk))
    }

    @Test
    fun v1RequiresManifestSignatureFileAndSupportedBlock() {
        listOf(
            listOf("META-INF/CERT.SF", "META-INF/CERT.RSA"),
            listOf("META-INF/MANIFEST.MF", "META-INF/CERT.RSA"),
            listOf("META-INF/MANIFEST.MF", "META-INF/CERT.SF"),
            listOf("META-INF/MANIFEST.MF", "META-INF/CERT.SF", "META-INF/CERT.P7S"),
            listOf("META-INF/MANIFEST.MF", "CERT.SF", "CERT.RSA"),
        ).forEachIndexed { index, entries ->
            val apk = writeZip("incomplete-v1-$index.apk", entries)
            assertFalse(entries.toString(), ApkSignatureDetector.hasV1Signature(apk))
            assertNull(ApkSignatureDetector.detectSchemes(apk))
        }

        listOf("RSA", "DSA", "EC").forEach { extension ->
            val apk = writeZip(
                "v1-${extension.lowercase()}.apk",
                listOf(
                    "meta-inf/manifest.mf",
                    "meta-inf/cert.sf",
                    "meta-inf/cert.$extension",
                ),
            )
            assertTrue(ApkSignatureDetector.hasV1Signature(apk))
            assertEquals("V1", ApkSignatureDetector.detectSchemes(apk))
        }
    }

    @Test
    fun missingInputIsRejectedBeforeEitherDetectorRuns() {
        val missing = temporaryFolder.root.resolve("missing.apk")
        assertNull(ApkSignatureDetector.detectSchemes(missing))
    }

    private fun writeArchive(
        name: String,
        prefix: ByteArray,
        centralDirectoryOffset: Int = prefix.size,
    ): File = writeRaw(
        name,
        prefix + emptyZipEocd(centralDirectoryOffset),
    )

    private fun writeRaw(name: String, bytes: ByteArray): File =
        temporaryFolder.newFile(name).apply { writeBytes(bytes) }

    private fun writeZip(name: String, entries: List<String>): File =
        temporaryFolder.newFile(name).also { file ->
            ZipOutputStream(FileOutputStream(file)).use { zip ->
                entries.forEach { entryName ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(1)
                    zip.closeEntry()
                }
            }
        }

    private fun signingBlock(
        entries: ByteArray,
        headerSize: Long = entries.size.toLong() + SIGNING_BLOCK_FOOTER_BYTES,
        footerSize: Long = entries.size.toLong() + SIGNING_BLOCK_FOOTER_BYTES,
        magic: ByteArray = SIGNING_BLOCK_MAGIC,
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeLittleEndianLong(headerSize)
            output.write(entries)
            output.writeLittleEndianLong(footerSize)
            output.write(magic)
        }
    }.toByteArray()

    private fun signingPair(id: Int): ByteArray =
        littleEndianLong(Int.SIZE_BYTES.toLong()) + littleEndianInt(id)

    private fun emptyZipEocd(
        centralDirectoryOffset: Int,
        declaredCommentLength: Int = 0,
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06))
            repeat(4) { output.writeLittleEndianShort(0) }
            output.writeLittleEndianInt(0)
            output.writeLittleEndianInt(centralDirectoryOffset)
            output.writeLittleEndianShort(declaredCommentLength)
        }
    }.toByteArray()

    private fun littleEndianInt(value: Int): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { it.writeLittleEndianInt(value) }
        }.toByteArray()

    private fun littleEndianLong(value: Long): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { it.writeLittleEndianLong(value) }
        }.toByteArray()

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        repeat(Int.SIZE_BYTES) { shift ->
            writeByte((value ushr (shift * Byte.SIZE_BITS)) and 0xFF)
        }
    }

    private fun DataOutputStream.writeLittleEndianLong(value: Long) {
        repeat(Long.SIZE_BYTES) { shift ->
            writeByte(((value ushr (shift * Byte.SIZE_BITS)) and 0xFF).toInt())
        }
    }

    private fun captureParseException(block: () -> Unit): ApkSignatureParseException {
        try {
            block()
        } catch (error: ApkSignatureParseException) {
            return error
        }
        throw AssertionError("Expected ApkSignatureParseException")
    }

    private companion object {
        const val BLOCK_ID_V3_1 = 0x1B93AD61
        const val ZIP_EOCD_MIN_BYTES = 22
        const val SIGNING_BLOCK_FOOTER_BYTES = 24
        const val SIGNING_BLOCK_MAGIC_BYTES = 16
        val SIGNING_BLOCK_MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
    }
}
