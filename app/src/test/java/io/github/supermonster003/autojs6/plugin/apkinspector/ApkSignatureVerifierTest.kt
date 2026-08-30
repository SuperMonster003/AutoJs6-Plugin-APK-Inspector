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
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkSignatureVerifierTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun officialApksignerFixtureVerifiesV2AndV3() {
        val apk = writeOfficialFixture("valid-v2-v3.apk")

        val verification = ApkSignatureVerifier.verify(apk)

        assertFalse(verification.hasV1)
        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v2?.state)
        assertEquals(1, verification.v2?.signerCount)
        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v3?.state)
        assertEquals(1, verification.v3?.signerCount)
        assertEquals(24, verification.v3?.minimumSdkVersion)
        assertFalse(verification.hasV31)
        assertNull(verification.v4)
    }

    @Test
    fun officialApksignerV4FixtureVerifiesSignedDataDigestAndMerkleTree() {
        val apk = writeOfficialFixture("valid-v4.apk")
        val idsig = writeFixture("valid-v4.apk.idsig", V4_IDSIG_FIXTURE_RESOURCE)

        val verification = ApkSignatureVerifier.verify(apk, idsig)

        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v2?.state)
        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v3?.state)
        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v4?.state)
        assertEquals(1, verification.v4?.signerCount)
        assertNull(verification.v4?.reason)
    }

    @Test
    fun officialApksignerV41FixtureMatchesBothRotationSigners() {
        val apk = writeFixture("valid-v41.apk", V41_APK_FIXTURE_RESOURCE)
        val idsig = writeFixture("valid-v41.apk.idsig", V41_IDSIG_FIXTURE_RESOURCE)

        val verification = ApkSignatureVerifier.verify(apk, idsig)

        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v3?.state)
        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v31?.state)
        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v4?.state)
        assertEquals(2, verification.v4?.signerCount)
        assertEquals(2, verification.lineage?.certificates?.size)
    }

    @Test
    fun officialApksignerV2FixtureVerifiesTwoIndependentSigners() {
        val apk = writeFixture("valid-v2-multisigner.apk", V2_MULTISIGNER_FIXTURE_RESOURCE)

        val verification = ApkSignatureVerifier.verify(apk)

        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v2?.state)
        assertEquals(2, verification.v2?.signerCount)
        assertEquals(2, verification.v2?.verifiedSigners?.size)
        assertNull(verification.v3)
        assertNull(verification.v31)
        assertNull(verification.v4)
    }

    @Test
    fun v4RejectsAnApkChangeInsideTheUnsignedSigningBlockPadding() {
        val bytes = decodeOfficialFixture()
        val paddingOffset = findSigningBlockPairValueOffset(bytes, VERITY_PADDING_BLOCK_ID)
        bytes[paddingOffset] = (bytes[paddingOffset].toInt() xor 1).toByte()
        val apk = temporaryFolder.newFile("tampered-v4-root.apk").apply { writeBytes(bytes) }
        val idsig = writeFixture("tampered-v4-root.apk.idsig", V4_IDSIG_FIXTURE_RESOURCE)

        val verification = ApkSignatureVerifier.verify(apk, idsig)

        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v2?.state)
        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v3?.state)
        assertEquals(ApkSignatureVerificationState.FAILED, verification.v4?.state)
        assertEquals(ApkSignatureFailureReason.V4_ROOT_HASH_MISMATCH, verification.v4?.reason)
    }

    @Test
    fun v4RejectsAChangedEmbeddedMerkleTree() {
        val apk = writeOfficialFixture("tree-tamper.apk")
        val bytes = decodeFixture(V4_IDSIG_FIXTURE_RESOURCE)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val idsig = temporaryFolder.newFile("tree-tamper.apk.idsig").apply { writeBytes(bytes) }

        val verification = ApkSignatureVerifier.verify(apk, idsig)

        assertEquals(ApkSignatureVerificationState.FAILED, verification.v4?.state)
        assertEquals(ApkSignatureFailureReason.V4_MERKLE_TREE_MISMATCH, verification.v4?.reason)
    }

    @Test
    fun v4RejectsAChangedSignedRootHash() {
        val apk = writeOfficialFixture("signed-data-tamper.apk")
        val bytes = decodeFixture(V4_IDSIG_FIXTURE_RESOURCE)
        val rootOffset = findV4RootHashOffset(bytes)
        bytes[rootOffset] = (bytes[rootOffset].toInt() xor 1).toByte()
        val idsig = temporaryFolder.newFile("signed-data-tamper.apk.idsig").apply {
            writeBytes(bytes)
        }

        val verification = ApkSignatureVerifier.verify(apk, idsig)

        assertEquals(ApkSignatureVerificationState.FAILED, verification.v4?.state)
        assertEquals(ApkSignatureFailureReason.SIGNATURE_DID_NOT_VERIFY, verification.v4?.reason)
    }

    @Test
    fun missingIdsigDoesNotClaimV4PresenceOrVerification() {
        val verification = ApkSignatureVerifier.verify(writeOfficialFixture("missing-idsig.apk"))

        assertNull(verification.v4)
    }

    @Test
    fun truncatedIdsigIsReportedAsMalformedV4() {
        val apk = writeOfficialFixture("truncated-idsig.apk")
        val bytes = decodeFixture(V4_IDSIG_FIXTURE_RESOURCE).copyOf(19)
        val idsig = temporaryFolder.newFile("truncated-idsig.apk.idsig").apply { writeBytes(bytes) }

        val verification = ApkSignatureVerifier.verify(apk, idsig)

        assertEquals(ApkSignatureVerificationState.FAILED, verification.v4?.state)
        assertEquals(ApkSignatureFailureReason.MALFORMED_V4_SIGNATURE, verification.v4?.reason)
    }

    @Test
    fun officialApksignerFixtureVerifiesV31AndCertificateLineage() {
        val apk = writeFixture("valid-v31-lineage.apk", V31_LINEAGE_FIXTURE_RESOURCE)

        val verification = ApkSignatureVerifier.verify(apk)

        val v3 = checkNotNull(verification.v3)
        assertEquals(ApkSignatureVerificationState.VERIFIED, v3.state)
        assertEquals(1, v3.signerCount)
        assertEquals(24, v3.minimumSdkVersion)
        assertEquals(33, v3.rotationMinSdkVersion)

        val v31 = checkNotNull(verification.v31)
        assertEquals(ApkSignatureVerificationState.VERIFIED, v31.state)
        assertEquals(1, v31.signerCount)
        assertEquals(33, v31.minimumSdkVersion)
        assertNull(v31.rotationMinSdkVersion)

        val lineage = checkNotNull(verification.lineage)
        assertNull(v3.lineage)
        assertEquals(lineage, v31.lineage)
        assertEquals(2, lineage.certificates.size)
        assertEquals(
            "CN=Roadmap Signer One,OU=APK Inspector,O=AutoJs6,L=Shanghai,ST=Shanghai,C=CN",
            lineage.certificates[0].certificate.subject,
        )
        assertEquals(
            "42e1c2d34209501bbc7950cb6103b2ed9806f4a19281e6ddb3e4096679c0e837",
            lineage.certificates[0].certificate.sha256Fingerprint,
        )
        assertEquals(
            "CN=Roadmap Signer Two,OU=APK Inspector,O=AutoJs6,L=Shanghai,ST=Shanghai,C=CN",
            lineage.certificates[1].certificate.subject,
        )
        assertEquals(
            "cdd8c336431268290e7bc722840de73fa62cc1f310763aa569e36a3101a005ce",
            lineage.certificates[1].certificate.sha256Fingerprint,
        )
        assertTrue(lineage.certificates.all { it.capabilityFlags == DEFAULT_LINEAGE_CAPABILITIES })
    }

    @Test
    fun changedContentFailsV3AndV31ContentDigests() {
        val bytes = decodeFixture(V31_LINEAGE_FIXTURE_RESOURCE)
        check(findSigningBlockOffset(bytes) > 1024) { "Fixture has no signed padding byte" }
        bytes[1024] = (bytes[1024].toInt() xor 1).toByte()
        val apk = temporaryFolder.newFile("tampered-v31-content.apk").apply { writeBytes(bytes) }

        val verification = ApkSignatureVerifier.verify(apk)

        assertEquals(ApkSignatureVerificationState.FAILED, verification.v3?.state)
        assertEquals(ApkSignatureFailureReason.CONTENT_DIGEST_MISMATCH, verification.v3?.reason)
        assertEquals(ApkSignatureVerificationState.FAILED, verification.v31?.state)
        assertEquals(ApkSignatureFailureReason.CONTENT_DIGEST_MISMATCH, verification.v31?.reason)
        assertNull(verification.lineage)
    }

    @Test
    fun v3RotationAttributeRejectsStrippedV31Block() {
        val bytes = removeSigningBlockPair(
            decodeFixture(V31_LINEAGE_FIXTURE_RESOURCE),
            V31_BLOCK_ID,
        )
        val apk = temporaryFolder.newFile("stripped-v31.apk").apply { writeBytes(bytes) }

        val verification = ApkSignatureVerifier.verify(apk)

        assertNull(verification.v31)
        assertEquals(ApkSignatureVerificationState.FAILED, verification.v3?.state)
        assertEquals(ApkSignatureFailureReason.V31_BLOCK_MISSING, verification.v3?.reason)
        assertNull(verification.lineage)
    }

    @Test
    fun v31BlockRejectsStrippedV3BaseBlock() {
        val bytes = removeSigningBlockPair(
            decodeFixture(V31_LINEAGE_FIXTURE_RESOURCE),
            V3_BLOCK_ID,
        )
        val apk = temporaryFolder.newFile("stripped-v3.apk").apply { writeBytes(bytes) }

        val verification = ApkSignatureVerifier.verify(apk)

        assertNull(verification.v3)
        assertEquals(ApkSignatureVerificationState.FAILED, verification.v31?.state)
        assertEquals(ApkSignatureFailureReason.V3_BLOCK_MISSING, verification.v31?.reason)
        assertNull(verification.lineage)
    }

    @Test
    fun changedContentFailsBothContentDigests() {
        val bytes = decodeOfficialFixture()
        // apksigner aligns the signing block to 4096 bytes; this byte is content padding covered
        // by both signed digests, but is outside the ZIP structures and the signing block itself.
        bytes[1024] = (bytes[1024].toInt() xor 1).toByte()
        val apk = temporaryFolder.newFile("tampered-v2-v3.apk").apply { writeBytes(bytes) }

        val verification = ApkSignatureVerifier.verify(apk)

        assertEquals(ApkSignatureVerificationState.FAILED, verification.v2?.state)
        assertEquals(ApkSignatureFailureReason.CONTENT_DIGEST_MISMATCH, verification.v2?.reason)
        assertEquals(ApkSignatureVerificationState.FAILED, verification.v3?.state)
        assertEquals(ApkSignatureFailureReason.CONTENT_DIGEST_MISMATCH, verification.v3?.reason)
    }

    @Test
    fun changedV2SignatureFailsCryptographicVerificationOnlyForV2() {
        val bytes = decodeOfficialFixture()
        val signatureOffset = findFirstV2SignatureOffset(bytes)
        bytes[signatureOffset] = (bytes[signatureOffset].toInt() xor 1).toByte()
        val apk = temporaryFolder.newFile("tampered-v2-signature.apk").apply { writeBytes(bytes) }

        val verification = ApkSignatureVerifier.verify(apk)

        assertEquals(ApkSignatureVerificationState.FAILED, verification.v2?.state)
        assertEquals(ApkSignatureFailureReason.SIGNATURE_DID_NOT_VERIFY, verification.v2?.reason)
        assertEquals(ApkSignatureVerificationState.VERIFIED, verification.v3?.state)
    }

    @Test
    fun unsignedZipHasNoV2OrV3Result() {
        val apk = temporaryFolder.newFile("unsigned.apk")
        ZipOutputStream(FileOutputStream(apk)).use { zip ->
            zip.putNextEntry(ZipEntry("payload.bin"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }

        val verification = ApkSignatureVerifier.verify(apk)

        assertFalse(verification.hasV1)
        assertNull(verification.v2)
        assertNull(verification.v3)
        assertFalse(verification.hasV31)
    }

    @Test
    fun unsupportedAlgorithmKeepsPresenceWithoutClaimingVerification() {
        val unknownSignatureRecord = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeLittleEndianInt(0x7FFF)
                output.writeLengthPrefixed(byteArrayOf())
            }
        }.toByteArray()
        val signer = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeLengthPrefixed(byteArrayOf())
                output.writeLengthPrefixed(lengthPrefixed(unknownSignatureRecord))
            }
        }.toByteArray()
        val apk = writeSigningBlockApk(
            name = "unsupported-v2.apk",
            id = V2_BLOCK_ID,
            value = lengthPrefixed(lengthPrefixed(signer)),
        )

        val verification = ApkSignatureVerifier.verify(apk)

        assertEquals(ApkSignatureVerificationState.PRESENT, verification.v2?.state)
        assertEquals(
            ApkSignatureFailureReason.UNSUPPORTED_SIGNATURE_ALGORITHM,
            verification.v2?.reason,
        )
    }

    @Test
    fun malformedSignerIsReportedAsVerificationFailure() {
        val apk = writeSigningBlockApk(
            name = "malformed-v2.apk",
            id = V2_BLOCK_ID,
            value = byteArrayOf(),
        )

        val verification = ApkSignatureVerifier.verify(apk)

        assertEquals(ApkSignatureVerificationState.FAILED, verification.v2?.state)
        assertEquals(ApkSignatureFailureReason.MALFORMED_SIGNER, verification.v2?.reason)
    }

    @Test
    fun v31PresenceIsNotMislabelledAsV3Verification() {
        val apk = writeSigningBlockApk(
            name = "v31-only.apk",
            id = V31_BLOCK_ID,
            value = byteArrayOf(),
        )

        val verification = ApkSignatureVerifier.verify(apk)

        assertNull(verification.v3)
        assertTrue(verification.hasV31)
        assertEquals(ApkSignatureVerificationState.FAILED, verification.v31?.state)
        assertEquals(ApkSignatureFailureReason.MALFORMED_SIGNER, verification.v31?.reason)
    }

    private fun writeOfficialFixture(name: String) =
        temporaryFolder.newFile(name).apply { writeBytes(decodeOfficialFixture()) }

    private fun writeFixture(name: String, resource: String) =
        temporaryFolder.newFile(name).apply { writeBytes(decodeFixture(resource)) }

    private fun decodeOfficialFixture(): ByteArray = decodeFixture(OFFICIAL_FIXTURE_RESOURCE)

    private fun decodeFixture(resource: String): ByteArray {
        val encoded = checkNotNull(javaClass.getResourceAsStream(resource)) {
            "Missing $resource"
        }.bufferedReader(Charsets.US_ASCII).use { reader -> reader.readText() }
        return Base64.getMimeDecoder().decode(encoded)
    }

    private fun writeSigningBlockApk(name: String, id: Int, value: ByteArray) =
        temporaryFolder.newFile(name).apply {
            val pair = ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeLittleEndianLong(Int.SIZE_BYTES.toLong() + value.size)
                    output.writeLittleEndianInt(id)
                    output.write(value)
                }
            }.toByteArray()
            val blockSize = pair.size.toLong() + 24
            val signingBlock = ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeLittleEndianLong(blockSize)
                    output.write(pair)
                    output.writeLittleEndianLong(blockSize)
                    output.write("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
                }
            }.toByteArray()
            FileOutputStream(this).use { output ->
                output.write(signingBlock)
                output.write(emptyZipEocd(signingBlock.size))
            }
        }

    private fun lengthPrefixed(value: ByteArray): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output -> output.writeLengthPrefixed(value) }
        }.toByteArray()

    private fun findFirstV2SignatureOffset(apk: ByteArray): Int {
        val eocdOffset = apk.size - ZIP_EOCD_BYTES
        val centralDirectoryOffset = apk.readLittleEndianInt(eocdOffset + 16)
        val signingBlockSize = apk.readLittleEndianLong(centralDirectoryOffset - 24)
        var pairOffset = (centralDirectoryOffset - signingBlockSize - 8).toInt() + 8
        val pairsEnd = centralDirectoryOffset - 24
        while (pairOffset < pairsEnd) {
            val pairSize = apk.readLittleEndianLong(pairOffset)
            val pairId = apk.readLittleEndianInt(pairOffset + 8)
            if (pairId == V2_BLOCK_ID) {
                val schemeOffset = pairOffset + 12
                val signerOffset = schemeOffset + 8
                val signedDataBytes = apk.readLittleEndianInt(signerOffset)
                val signaturesOffset = signerOffset + Int.SIZE_BYTES + signedDataBytes
                val firstRecordOffset = signaturesOffset + Int.SIZE_BYTES
                val signatureBytes = apk.readLittleEndianInt(
                    firstRecordOffset + Int.SIZE_BYTES + Int.SIZE_BYTES,
                )
                check(signatureBytes > 0) { "V2 fixture has no signature bytes" }
                return firstRecordOffset + Int.SIZE_BYTES * 3
            }
            pairOffset += Long.SIZE_BYTES + pairSize.toInt()
        }
        error("V2 block missing from fixture")
    }

    private fun findSigningBlockOffset(apk: ByteArray): Int {
        val eocdOffset = apk.size - ZIP_EOCD_BYTES
        val centralDirectoryOffset = apk.readLittleEndianInt(eocdOffset + 16)
        val signingBlockSize = apk.readLittleEndianLong(centralDirectoryOffset - 24)
        return (centralDirectoryOffset - signingBlockSize - Long.SIZE_BYTES).toInt()
    }

    private fun findSigningBlockPairValueOffset(apk: ByteArray, id: Int): Int {
        val eocdOffset = apk.size - ZIP_EOCD_BYTES
        val centralDirectoryOffset = apk.readLittleEndianInt(eocdOffset + 16)
        val signingBlockSize = apk.readLittleEndianLong(centralDirectoryOffset - 24)
        var pairOffset = (centralDirectoryOffset - signingBlockSize - Long.SIZE_BYTES).toInt() +
            Long.SIZE_BYTES
        val pairsEnd = centralDirectoryOffset - 24
        while (pairOffset < pairsEnd) {
            val pairSize = apk.readLittleEndianLong(pairOffset)
            if (apk.readLittleEndianInt(pairOffset + Long.SIZE_BYTES) == id) {
                check(pairSize > Int.SIZE_BYTES) { "Signing-block pair has no value" }
                return pairOffset + Long.SIZE_BYTES + Int.SIZE_BYTES
            }
            pairOffset += Long.SIZE_BYTES + pairSize.toInt()
        }
        error("Signing-block pair ${Integer.toUnsignedString(id, 16)} is missing")
    }

    private fun findV4RootHashOffset(idsig: ByteArray): Int {
        val hashingInfoLength = idsig.readLittleEndianInt(Int.SIZE_BYTES)
        check(hashingInfoLength > 0)
        val hashingInfoOffset = Int.SIZE_BYTES * 2
        val saltLengthOffset = hashingInfoOffset + Int.SIZE_BYTES + 1
        val saltLength = idsig.readLittleEndianInt(saltLengthOffset)
        val rootLengthOffset = saltLengthOffset + Int.SIZE_BYTES + saltLength
        check(idsig.readLittleEndianInt(rootLengthOffset) == 32)
        return rootLengthOffset + Int.SIZE_BYTES
    }

    private fun removeSigningBlockPair(apk: ByteArray, id: Int): ByteArray {
        val eocdOffset = apk.size - ZIP_EOCD_BYTES
        val centralDirectoryOffset = apk.readLittleEndianInt(eocdOffset + 16)
        val signingBlockSize = apk.readLittleEndianLong(centralDirectoryOffset - 24)
        val signingBlockOffset =
            (centralDirectoryOffset - signingBlockSize - Long.SIZE_BYTES).toInt()
        var pairOffset = signingBlockOffset + Long.SIZE_BYTES
        val pairsEnd = centralDirectoryOffset - 24
        while (pairOffset < pairsEnd) {
            val pairSize = apk.readLittleEndianLong(pairOffset)
            check(pairSize in Int.SIZE_BYTES.toLong()..Int.MAX_VALUE.toLong()) {
                "Invalid signing block pair size $pairSize"
            }
            val pairBytes = Long.SIZE_BYTES + pairSize.toInt()
            check(pairOffset + pairBytes <= pairsEnd) { "Signing block pair exceeds block" }
            if (apk.readLittleEndianInt(pairOffset + Long.SIZE_BYTES) == id) {
                val result = ByteArray(apk.size - pairBytes)
                apk.copyInto(result, endIndex = pairOffset)
                apk.copyInto(
                    destination = result,
                    destinationOffset = pairOffset,
                    startIndex = pairOffset + pairBytes,
                )
                val newSigningBlockSize = signingBlockSize - pairBytes
                val newCentralDirectoryOffset = centralDirectoryOffset - pairBytes
                val newEocdOffset = eocdOffset - pairBytes
                result.writeLittleEndianLong(signingBlockOffset, newSigningBlockSize)
                result.writeLittleEndianLong(
                    newCentralDirectoryOffset - 24,
                    newSigningBlockSize,
                )
                result.writeLittleEndianInt(newEocdOffset + 16, newCentralDirectoryOffset)
                return result
            }
            pairOffset += pairBytes
        }
        error("Signing block pair ${Integer.toUnsignedString(id, 16)} is missing")
    }

    private fun ByteArray.readLittleEndianInt(offset: Int): Int =
        ByteBuffer.wrap(this, offset, Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).int

    private fun ByteArray.readLittleEndianLong(offset: Int): Long =
        ByteBuffer.wrap(this, offset, Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).long

    private fun ByteArray.writeLittleEndianInt(offset: Int, value: Int) {
        repeat(Int.SIZE_BYTES) { shift ->
            this[offset + shift] = (value ushr (shift * 8)).toByte()
        }
    }

    private fun ByteArray.writeLittleEndianLong(offset: Int, value: Long) {
        repeat(Long.SIZE_BYTES) { shift ->
            this[offset + shift] = (value ushr (shift * 8)).toByte()
        }
    }

    private fun emptyZipEocd(centralDirectoryOffset: Int): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06))
                repeat(4) { output.writeLittleEndianShort(0) }
                output.writeLittleEndianInt(0)
                output.writeLittleEndianInt(centralDirectoryOffset)
                output.writeLittleEndianShort(0)
            }
        }.toByteArray()

    private fun DataOutputStream.writeLengthPrefixed(value: ByteArray) {
        writeLittleEndianInt(value.size)
        write(value)
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        repeat(Int.SIZE_BYTES) { shift -> writeByte((value ushr (shift * 8)) and 0xFF) }
    }

    private fun DataOutputStream.writeLittleEndianLong(value: Long) {
        repeat(Long.SIZE_BYTES) { shift -> writeByte(((value ushr (shift * 8)) and 0xFF).toInt()) }
    }

    companion object {
        // Generated with Android SDK Build Tools apksigner using V1=false, V2=true, and V3=true.
        private const val OFFICIAL_FIXTURE_RESOURCE = "/valid-minimal-v2-v3.apk.b64"
        // Generated with apksigner rotate/sign using a two-certificate lineage and rotation SDK 33.
        private const val V31_LINEAGE_FIXTURE_RESOURCE = "/valid-v31-lineage.apk.b64"
        // Generated with Build Tools 37 apksigner and independently accepted by `apksigner verify`.
        private const val V4_IDSIG_FIXTURE_RESOURCE = "/valid-v4.idsig.b64"
        private const val V41_APK_FIXTURE_RESOURCE = "/valid-v41-lineage.apk.b64"
        private const val V41_IDSIG_FIXTURE_RESOURCE = "/valid-v41-lineage.idsig.b64"
        private const val V2_MULTISIGNER_FIXTURE_RESOURCE = "/valid-v2-multisigner.apk.b64"
        private const val DEFAULT_LINEAGE_CAPABILITIES = 0x17
        private const val V2_BLOCK_ID = 0x7109871A
        private const val V3_BLOCK_ID = 0xF05368C0.toInt()
        private const val V31_BLOCK_ID = 0x1B93AD61
        private const val VERITY_PADDING_BLOCK_ID = 0x42726577
        private const val ZIP_EOCD_BYTES = 22
    }
}
