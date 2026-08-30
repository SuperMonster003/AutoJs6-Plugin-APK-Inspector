package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec

/** Bounded implementation of the AOSP V4 idsig and fs-verity verification rules. */
internal object ApkV4SignatureVerifier {

    fun verify(
        apkFile: File,
        idsigFile: File,
        v2: ApkSchemeVerification?,
        v3: ApkSchemeVerification?,
        v31: ApkSchemeVerification?,
    ): ApkSchemeVerification = try {
        verifyOrThrow(apkFile, idsigFile, v2, v3, v31)
    } catch (error: V4VerificationException) {
        ApkSchemeVerification(
            state = error.state,
            signerCount = error.signerCount,
            reason = error.reason,
            detail = sanitizeDetail(error.message),
        )
    } catch (error: IOException) {
        ApkSchemeVerification(
            state = ApkSignatureVerificationState.FAILED,
            reason = ApkSignatureFailureReason.IO_ERROR,
            detail = sanitizeDetail(error.message),
        )
    } catch (error: Exception) {
        ApkSchemeVerification(
            state = ApkSignatureVerificationState.FAILED,
            reason = ApkSignatureFailureReason.MALFORMED_V4_SIGNATURE,
            detail = sanitizeDetail(error.message),
        )
    }

    private fun verifyOrThrow(
        apkFile: File,
        idsigFile: File,
        v2: ApkSchemeVerification?,
        v3: ApkSchemeVerification?,
        v31: ApkSchemeVerification?,
    ): ApkSchemeVerification {
        if (!apkFile.isFile || !idsigFile.isFile) {
            throw V4VerificationException(
                ApkSignatureFailureReason.IO_ERROR,
                "APK or idsig input is unavailable",
            )
        }
        val idsig = readBoundedIdsig(idsigFile)
        val parsed = try {
            parseIdsig(idsig)
        } catch (error: V4VerificationException) {
            throw error
        } catch (error: BufferUnderflowException) {
            throw V4VerificationException(
                ApkSignatureFailureReason.MALFORMED_V4_SIGNATURE,
                "idsig ended before all declared fields were read",
            )
        } catch (error: EOFException) {
            throw V4VerificationException(
                ApkSignatureFailureReason.MALFORMED_V4_SIGNATURE,
                error.message,
            )
        } catch (error: IllegalArgumentException) {
            throw V4VerificationException(
                ApkSignatureFailureReason.MALFORMED_V4_SIGNATURE,
                error.message,
            )
        }

        val verifiedV4Signers = buildList {
            add(verifySigningInfo(apkFile.length(), parsed.hashingInfo, parsed.primarySigningInfo))
            parsed.additionalSigningInfos.forEach { block ->
                add(verifySigningInfo(apkFile.length(), parsed.hashingInfo, block.signingInfo))
            }
        }
        crossCheckComplementaryScheme(
            v4Signers = verifiedV4Signers,
            additionalBlocks = parsed.additionalSigningInfos,
            v2 = v2,
            v3 = v3,
            v31 = v31,
        )

        val actualTree = generateVerityTree(apkFile, parsed.hashingInfo.salt)
        val actualRootHash = saltedDigest(
            parsed.hashingInfo.salt,
            actualTree,
            offset = 0,
            length = VERITY_BLOCK_BYTES,
        )
        if (!MessageDigest.isEqual(parsed.hashingInfo.rawRootHash, actualRootHash)) {
            throw V4VerificationException(
                ApkSignatureFailureReason.V4_ROOT_HASH_MISMATCH,
                "idsig root does not match the APK fs-verity tree",
                verifiedV4Signers.size,
            )
        }
        parsed.merkleTree?.let { expectedTree ->
            if (!MessageDigest.isEqual(expectedTree, actualTree)) {
                throw V4VerificationException(
                    ApkSignatureFailureReason.V4_MERKLE_TREE_MISMATCH,
                    "embedded tree has ${expectedTree.size} bytes; expected ${actualTree.size}",
                    verifiedV4Signers.size,
                )
            }
        }

        return ApkSchemeVerification(
            state = ApkSignatureVerificationState.VERIFIED,
            signerCount = verifiedV4Signers.size,
        )
    }

    private fun readBoundedIdsig(idsigFile: File): ByteArray {
        val length = idsigFile.length()
        if (length !in 1..PackageCacheStager.MAX_V4_IDSIG_BYTES) {
            throw V4VerificationException(
                ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                "idsig length $length is outside the supported bound",
                state = ApkSignatureVerificationState.PRESENT,
            )
        }
        val result = ByteArray(length.toInt())
        DataInputStream(FileInputStream(idsigFile)).use { input ->
            input.readFully(result)
            if (input.read() != -1) {
                throw V4VerificationException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "idsig changed while it was read",
                )
            }
        }
        return result
    }

    private fun parseIdsig(bytes: ByteArray): ParsedIdsig {
        val reader = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = reader.readInt32("version")
        if (version != V4_VERSION) {
            throw V4VerificationException(
                ApkSignatureFailureReason.UNSUPPORTED_V4_VERSION,
                "version $version; supported version is $V4_VERSION",
                state = ApkSignatureVerificationState.PRESENT,
            )
        }
        val hashingInfoBytes = reader.readSizedBytes("hashing info", MAX_HASHING_INFO_BYTES)
        val signingInfosBytes = reader.readSizedBytes("signing infos", MAX_SIGNING_INFOS_BYTES)
        val merkleTree = if (reader.hasRemaining()) {
            reader.readSizedBytes("Merkle tree", MAX_MERKLE_TREE_BYTES)
        } else {
            null
        }
        reader.requireFullyConsumed("idsig")

        val hashingReader = hashingInfoBytes.littleEndianReader()
        val hashingInfo = HashingInfo(
            hashAlgorithm = hashingReader.readInt32("hash algorithm"),
            log2BlockSize = hashingReader.readByte("log2 block size"),
            salt = hashingReader.readSizedBytes("salt", MAX_SALT_BYTES),
            rawRootHash = hashingReader.readSizedBytes("raw root hash", SHA256_BYTES),
        )
        hashingReader.requireFullyConsumed("hashing info")
        if (
            hashingInfo.hashAlgorithm != HASH_ALGORITHM_SHA256 ||
            hashingInfo.log2BlockSize != LOG2_BLOCK_SIZE_4096 ||
            hashingInfo.rawRootHash.size != SHA256_BYTES
        ) {
            throw V4VerificationException(
                ApkSignatureFailureReason.UNSUPPORTED_V4_HASHING,
                "hash=${hashingInfo.hashAlgorithm}, block=${hashingInfo.log2BlockSize}, " +
                    "root=${hashingInfo.rawRootHash.size} bytes",
                state = ApkSignatureVerificationState.PRESENT,
            )
        }

        val signingReader = signingInfosBytes.littleEndianReader()
        val primarySigningInfo = signingReader.readSigningInfo("primary signing info")
        val additionalSigningInfos = mutableListOf<SigningInfoBlock>()
        val seenBlockIds = mutableSetOf<Int>()
        while (signingReader.hasRemaining()) {
            if (additionalSigningInfos.size >= MAX_ADDITIONAL_SIGNING_INFOS) {
                throw V4VerificationException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "too many V4 signing-info blocks",
                    state = ApkSignatureVerificationState.PRESENT,
                )
            }
            val blockId = signingReader.readInt32("signing-info block ID")
            if (!seenBlockIds.add(blockId)) {
                throw V4VerificationException(
                    ApkSignatureFailureReason.MALFORMED_V4_SIGNATURE,
                    "duplicate signing-info block ${blockId.toHexId()}",
                )
            }
            val blockBytes = signingReader.readSizedBytes(
                "signing-info block",
                MAX_SIGNING_INFOS_BYTES,
            )
            val blockReader = blockBytes.littleEndianReader()
            val signingInfo = blockReader.readSigningInfo("additional signing info")
            blockReader.requireFullyConsumed("additional signing info")
            additionalSigningInfos += SigningInfoBlock(blockId, signingInfo)
        }
        return ParsedIdsig(hashingInfo, primarySigningInfo, additionalSigningInfos, merkleTree)
    }

    private fun ByteBuffer.readSigningInfo(label: String): SigningInfo = SigningInfo(
        apkDigest = readSizedBytes("$label APK digest", MAX_APK_DIGEST_BYTES),
        certificate = readSizedBytes("$label certificate", MAX_CERTIFICATE_BYTES),
        additionalData = readSizedBytes("$label additional data", MAX_ADDITIONAL_DATA_BYTES),
        publicKey = readSizedBytes("$label public key", MAX_PUBLIC_KEY_BYTES),
        signatureAlgorithmId = readInt32("$label signature algorithm"),
        signature = readSizedBytes("$label signature", MAX_SIGNATURE_BYTES),
    )

    private fun verifySigningInfo(
        apkSize: Long,
        hashingInfo: HashingInfo,
        signingInfo: SigningInfo,
    ): VerifiedV4Signer {
        val algorithm = V4SignatureAlgorithm.fromId(signingInfo.signatureAlgorithmId)
            ?: throw V4VerificationException(
                ApkSignatureFailureReason.UNSUPPORTED_SIGNATURE_ALGORITHM,
                signingInfo.signatureAlgorithmId.toHexId(),
                state = ApkSignatureVerificationState.PRESENT,
            )
        if (
            signingInfo.apkDigest.isEmpty() ||
            signingInfo.certificate.isEmpty() ||
            signingInfo.publicKey.isEmpty() ||
            signingInfo.signature.isEmpty()
        ) {
            throw V4VerificationException(
                ApkSignatureFailureReason.MALFORMED_V4_SIGNATURE,
                "a required signing-info field is empty",
            )
        }
        val publicKey = decodePublicKey(algorithm.keyAlgorithm, signingInfo.publicKey)
        val signedData = encodeSignedData(apkSize, hashingInfo, signingInfo)
        verifySignature(algorithm, publicKey, signedData, signingInfo.signature)
        val certificate = parseCertificate(signingInfo.certificate)
        if (!MessageDigest.isEqual(certificate.publicKey.encoded, signingInfo.publicKey)) {
            throw V4VerificationException(ApkSignatureFailureReason.PUBLIC_KEY_MISMATCH)
        }
        return VerifiedV4Signer(signingInfo.certificate, signingInfo.apkDigest)
    }

    private fun encodeSignedData(
        apkSize: Long,
        hashingInfo: HashingInfo,
        signingInfo: SigningInfo,
    ): ByteArray {
        val size = Math.addExact(
            FIXED_SIGNED_DATA_BYTES,
            listOf(
                hashingInfo.salt,
                hashingInfo.rawRootHash,
                signingInfo.apkDigest,
                signingInfo.certificate,
                signingInfo.additionalData,
            ).sumOf { bytes -> Math.addExact(4, bytes.size) },
        )
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(size)
            putLong(apkSize)
            putInt(hashingInfo.hashAlgorithm)
            put(hashingInfo.log2BlockSize)
            putSizedBytes(hashingInfo.salt)
            putSizedBytes(hashingInfo.rawRootHash)
            putSizedBytes(signingInfo.apkDigest)
            putSizedBytes(signingInfo.certificate)
            putSizedBytes(signingInfo.additionalData)
        }.array()
    }

    private fun crossCheckComplementaryScheme(
        v4Signers: List<VerifiedV4Signer>,
        additionalBlocks: List<SigningInfoBlock>,
        v2: ApkSchemeVerification?,
        v3: ApkSchemeVerification?,
        v31: ApkSchemeVerification?,
    ) {
        val verifiedV3 = v3?.takeIf { it.state == ApkSignatureVerificationState.VERIFIED }
        val verifiedV31 = v31?.takeIf { it.state == ApkSignatureVerificationState.VERIFIED }
        val verifiedV2 = v2?.takeIf { it.state == ApkSignatureVerificationState.VERIFIED }
        when {
            verifiedV3 != null -> {
                val expectedSignerCount = if (verifiedV31 != null) 2 else 1
                if (v4Signers.size != expectedSignerCount) {
                    throw V4VerificationException(
                        ApkSignatureFailureReason.V4_SIGNER_COUNT_MISMATCH,
                        "V4 has ${v4Signers.size} signers; expected $expectedSignerCount",
                        v4Signers.size,
                    )
                }
                if (verifiedV31 != null) {
                    if (
                        additionalBlocks.size != 1 ||
                        additionalBlocks.single().blockId != V31_SIGNING_BLOCK_ID
                    ) {
                        throw V4VerificationException(
                            ApkSignatureFailureReason.V4_SIGNER_MISMATCH,
                            "V4.1 signer is not bound to the V3.1 block",
                            v4Signers.size,
                        )
                    }
                } else if (additionalBlocks.isNotEmpty()) {
                    throw V4VerificationException(
                        ApkSignatureFailureReason.V4_SIGNER_COUNT_MISMATCH,
                        "additional V4 signing information requires a verified V3.1 block",
                        v4Signers.size,
                    )
                }
                crossCheckSigner(v4Signers[0], singleComplementarySigner(verifiedV3, "V3"), "V3")
                if (verifiedV31 != null) {
                    crossCheckSigner(
                        v4Signers[1],
                        singleComplementarySigner(verifiedV31, "V3.1"),
                        "V3.1",
                    )
                }
            }

            verifiedV2 != null -> {
                if (v4Signers.size != 1 || additionalBlocks.isNotEmpty()) {
                    throw V4VerificationException(
                        ApkSignatureFailureReason.V4_SIGNER_COUNT_MISMATCH,
                        "V2-backed V4 requires exactly one signer",
                        v4Signers.size,
                    )
                }
                crossCheckSigner(v4Signers.single(), singleComplementarySigner(verifiedV2, "V2"), "V2")
            }

            else -> throw V4VerificationException(
                ApkSignatureFailureReason.V4_COMPLEMENTARY_SCHEME_MISSING,
                "V4 requires a verified V2 or V3 signing block",
                v4Signers.size,
            )
        }
    }

    private fun singleComplementarySigner(
        scheme: ApkSchemeVerification,
        label: String,
    ): ApkVerifiedSigner {
        if (scheme.verifiedSigners.size != 1) {
            throw V4VerificationException(
                ApkSignatureFailureReason.V4_SIGNER_COUNT_MISMATCH,
                "$label has ${scheme.verifiedSigners.size} signers; expected 1",
            )
        }
        return scheme.verifiedSigners.single()
    }

    private fun crossCheckSigner(
        v4: VerifiedV4Signer,
        complementary: ApkVerifiedSigner,
        label: String,
    ) {
        if (!MessageDigest.isEqual(v4.certificate, complementary.certificateEncoding)) {
            throw V4VerificationException(
                ApkSignatureFailureReason.V4_SIGNER_MISMATCH,
                "V4 certificate does not match $label",
            )
        }
        val expectedDigest = pickBestV4Digest(complementary)
            ?: throw V4VerificationException(
                ApkSignatureFailureReason.V4_COMPLEMENTARY_SCHEME_MISSING,
                "$label has no V4-compatible APK digest",
            )
        if (!MessageDigest.isEqual(v4.apkDigest, expectedDigest)) {
            throw V4VerificationException(
                ApkSignatureFailureReason.V4_APK_DIGEST_MISMATCH,
                "V4 APK digest does not match $label",
            )
        }
    }

    private fun pickBestV4Digest(signer: ApkVerifiedSigner): ByteArray? {
        V4_DIGEST_ALGORITHM_PRIORITY.forEach { signatureIds ->
            val candidates = signatureIds.mapNotNull(signer.contentDigestsBySignatureAlgorithm::get)
            if (candidates.isNotEmpty()) {
                val first = candidates.first()
                if (candidates.drop(1).any { !MessageDigest.isEqual(first, it) }) {
                    throw V4VerificationException(
                        ApkSignatureFailureReason.MALFORMED_SIGNER,
                        "complementary signer has inconsistent content digests",
                    )
                }
                return first
            }
        }
        return null
    }

    private fun generateVerityTree(apkFile: File, salt: ByteArray): ByteArray {
        val levelSizesBottomUp = calculateLevelSizes(apkFile.length())
        val levelSizes = levelSizesBottomUp.asReversed()
        val offsets = IntArray(levelSizes.size + 1)
        levelSizes.forEachIndexed { index, size ->
            offsets[index + 1] = Math.addExact(offsets[index], size)
        }
        val tree = ByteArray(offsets.last())
        val bottomLevel = levelSizes.lastIndex
        val block = ByteArray(VERITY_BLOCK_BYTES)
        var outputOffset = offsets[bottomLevel]
        RandomAccessFile(apkFile, "r").use { apk ->
            var remaining = apk.length()
            while (remaining > 0L) {
                block.fill(0)
                val bytesInBlock = minOf(remaining, VERITY_BLOCK_BYTES.toLong()).toInt()
                apk.readFully(block, 0, bytesInBlock)
                val digest = saltedDigest(salt, block, 0, block.size)
                digest.copyInto(tree, outputOffset)
                outputOffset += digest.size
                remaining -= bytesInBlock
            }
        }
        for (level in bottomLevel - 1 downTo 0) {
            val childOffset = offsets[level + 1]
            val childSize = levelSizes[level + 1]
            outputOffset = offsets[level]
            var inputOffset = childOffset
            val inputEnd = childOffset + childSize
            while (inputOffset < inputEnd) {
                val digest = saltedDigest(
                    salt,
                    tree,
                    inputOffset,
                    VERITY_BLOCK_BYTES,
                )
                digest.copyInto(tree, outputOffset)
                outputOffset += digest.size
                inputOffset += VERITY_BLOCK_BYTES
            }
        }
        return tree
    }

    private fun calculateLevelSizes(apkSize: Long): List<Int> {
        if (apkSize <= 0L) {
            throw V4VerificationException(
                ApkSignatureFailureReason.MALFORMED_APK,
                "APK is empty",
            )
        }
        val sizes = mutableListOf<Int>()
        var sourceSize = apkSize
        var totalSize = 0L
        while (true) {
            val chunkCount = ceilDiv(sourceSize, VERITY_BLOCK_BYTES.toLong())
            val digestBytes = Math.multiplyExact(chunkCount, SHA256_BYTES.toLong())
            val levelSize = Math.multiplyExact(
                ceilDiv(digestBytes, VERITY_BLOCK_BYTES.toLong()),
                VERITY_BLOCK_BYTES.toLong(),
            )
            totalSize = Math.addExact(totalSize, levelSize)
            if (totalSize > MAX_MERKLE_TREE_BYTES) {
                throw V4VerificationException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "APK fs-verity tree exceeds $MAX_MERKLE_TREE_BYTES bytes",
                    state = ApkSignatureVerificationState.PRESENT,
                )
            }
            sizes += levelSize.toInt()
            if (digestBytes <= VERITY_BLOCK_BYTES) break
            sourceSize = digestBytes
        }
        return sizes
    }

    private fun saltedDigest(
        salt: ByteArray,
        data: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = MessageDigest.getInstance("SHA-256").run {
        update(salt)
        update(data, offset, length)
        digest()
    }

    private fun decodePublicKey(algorithm: String, encoded: ByteArray): PublicKey = try {
        KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(encoded))
    } catch (error: NoSuchAlgorithmException) {
        throw V4VerificationException(
            ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
            algorithm,
            state = ApkSignatureVerificationState.PRESENT,
        )
    } catch (error: InvalidKeySpecException) {
        throw V4VerificationException(
            ApkSignatureFailureReason.MALFORMED_PUBLIC_KEY,
            error.message,
        )
    }

    private fun parseCertificate(encoded: ByteArray): X509Certificate = try {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(encoded.inputStream()) as X509Certificate
    } catch (error: CertificateException) {
        throw V4VerificationException(
            ApkSignatureFailureReason.MALFORMED_CERTIFICATE,
            error.message,
        )
    }

    private fun verifySignature(
        algorithm: V4SignatureAlgorithm,
        publicKey: PublicKey,
        signedData: ByteArray,
        signatureBytes: ByteArray,
    ) {
        val verifier = createSignatureVerifier(algorithm)
        try {
            verifier.initVerify(publicKey)
            algorithm.pssParameters?.let(verifier::setParameter)
            verifier.update(signedData)
            if (!verifier.verify(signatureBytes)) {
                throw V4VerificationException(
                    ApkSignatureFailureReason.SIGNATURE_DID_NOT_VERIFY,
                    algorithm.displayName,
                )
            }
        } catch (error: V4VerificationException) {
            throw error
        } catch (error: InvalidAlgorithmParameterException) {
            throw V4VerificationException(
                ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
                error.message,
                state = ApkSignatureVerificationState.PRESENT,
            )
        } catch (error: InvalidKeyException) {
            throw V4VerificationException(
                ApkSignatureFailureReason.SIGNATURE_DID_NOT_VERIFY,
                error.message,
            )
        } catch (error: SignatureException) {
            throw V4VerificationException(
                ApkSignatureFailureReason.SIGNATURE_DID_NOT_VERIFY,
                error.message,
            )
        }
    }

    private fun createSignatureVerifier(algorithm: V4SignatureAlgorithm): Signature {
        var lastError: NoSuchAlgorithmException? = null
        algorithm.jcaNames.forEach { name ->
            try {
                return Signature.getInstance(name)
            } catch (error: NoSuchAlgorithmException) {
                lastError = error
            }
        }
        throw V4VerificationException(
            ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
            "${algorithm.displayName}: ${lastError?.message.orEmpty()}",
            state = ApkSignatureVerificationState.PRESENT,
        )
    }

    private fun ByteBuffer.readInt32(label: String): Int {
        if (remaining() < Int.SIZE_BYTES) throw EOFException("$label is truncated")
        return int
    }

    private fun ByteBuffer.readByte(label: String): Byte {
        if (!hasRemaining()) throw EOFException("$label is truncated")
        return get()
    }

    private fun ByteBuffer.readSizedBytes(label: String, maxBytes: Int): ByteArray {
        val length = readInt32("$label length")
        if (length < 0 || length > maxBytes) {
            throw V4VerificationException(
                ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                "$label length $length exceeds $maxBytes",
                state = ApkSignatureVerificationState.PRESENT,
            )
        }
        if (remaining() < length) throw EOFException("$label is truncated")
        return ByteArray(length).also(::get)
    }

    private fun ByteBuffer.requireFullyConsumed(label: String) {
        if (hasRemaining()) {
            throw V4VerificationException(
                ApkSignatureFailureReason.MALFORMED_V4_SIGNATURE,
                "$label has ${remaining()} trailing bytes",
            )
        }
    }

    private fun ByteBuffer.putSizedBytes(bytes: ByteArray) {
        putInt(bytes.size)
        put(bytes)
    }

    private fun ByteArray.littleEndianReader(): ByteBuffer =
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)

    private fun ceilDiv(value: Long, divisor: Long): Long = (value - 1L) / divisor + 1L

    private fun Int.toHexId(): String = "0x" + (toLong() and 0xffff_ffffL).toString(16)

    private fun sanitizeDetail(value: String?): String? = value
        ?.replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(MAX_DETAIL_CHARS)
        ?.takeIf(String::isNotEmpty)

    private data class ParsedIdsig(
        val hashingInfo: HashingInfo,
        val primarySigningInfo: SigningInfo,
        val additionalSigningInfos: List<SigningInfoBlock>,
        val merkleTree: ByteArray?,
    )

    private data class HashingInfo(
        val hashAlgorithm: Int,
        val log2BlockSize: Byte,
        val salt: ByteArray,
        val rawRootHash: ByteArray,
    )

    private data class SigningInfo(
        val apkDigest: ByteArray,
        val certificate: ByteArray,
        val additionalData: ByteArray,
        val publicKey: ByteArray,
        val signatureAlgorithmId: Int,
        val signature: ByteArray,
    )

    private data class SigningInfoBlock(val blockId: Int, val signingInfo: SigningInfo)

    private data class VerifiedV4Signer(val certificate: ByteArray, val apkDigest: ByteArray)

    private class V4VerificationException(
        val reason: ApkSignatureFailureReason,
        message: String? = null,
        val signerCount: Int = 0,
        val state: ApkSignatureVerificationState = ApkSignatureVerificationState.FAILED,
    ) : Exception(message)

    private enum class V4SignatureAlgorithm(
        val id: Int,
        val displayName: String,
        val keyAlgorithm: String,
        val jcaNames: List<String>,
        private val pssDigestName: String? = null,
        private val pssSaltBytes: Int = 0,
    ) {
        RSA_PSS_SHA256(0x0101, "RSA-PSS with SHA-256", "RSA", listOf("SHA256withRSA/PSS", "RSASSA-PSS"), "SHA-256", 32),
        RSA_PSS_SHA512(0x0102, "RSA-PSS with SHA-512", "RSA", listOf("SHA512withRSA/PSS", "RSASSA-PSS"), "SHA-512", 64),
        RSA_PKCS1_SHA256(0x0103, "RSA PKCS#1 with SHA-256", "RSA", listOf("SHA256withRSA")),
        RSA_PKCS1_SHA512(0x0104, "RSA PKCS#1 with SHA-512", "RSA", listOf("SHA512withRSA")),
        ECDSA_SHA256(0x0201, "ECDSA with SHA-256", "EC", listOf("SHA256withECDSA")),
        ECDSA_SHA512(0x0202, "ECDSA with SHA-512", "EC", listOf("SHA512withECDSA")),
        DSA_SHA256(0x0301, "DSA with SHA-256", "DSA", listOf("SHA256withDSA")),
        VERITY_RSA_SHA256(0x0421, "verity RSA with SHA-256", "RSA", listOf("SHA256withRSA")),
        VERITY_ECDSA_SHA256(0x0423, "verity ECDSA with SHA-256", "EC", listOf("SHA256withECDSA")),
        VERITY_DSA_SHA256(0x0425, "verity DSA with SHA-256", "DSA", listOf("SHA256withDSA"));

        val pssParameters: PSSParameterSpec?
            get() = pssDigestName?.let { digestName ->
                val mgf = when (digestName) {
                    "SHA-256" -> MGF1ParameterSpec.SHA256
                    "SHA-512" -> MGF1ParameterSpec.SHA512
                    else -> error("Unsupported PSS digest $digestName")
                }
                PSSParameterSpec(digestName, "MGF1", mgf, pssSaltBytes, 1)
            }

        companion object {
            fun fromId(id: Int): V4SignatureAlgorithm? = entries.firstOrNull { it.id == id }
        }
    }

    private const val V4_VERSION = 2
    private const val HASH_ALGORITHM_SHA256 = 1
    private const val LOG2_BLOCK_SIZE_4096: Byte = 12
    private const val VERITY_BLOCK_BYTES = 4096
    private const val SHA256_BYTES = 32
    private const val V31_SIGNING_BLOCK_ID = 0x1B93AD61
    private const val MAX_HASHING_INFO_BYTES = 4096
    private const val MAX_SIGNING_INFOS_BYTES = 7168
    private const val MAX_ADDITIONAL_SIGNING_INFOS = 4
    private const val MAX_SALT_BYTES = 32
    private const val MAX_APK_DIGEST_BYTES = 1024
    private const val MAX_CERTIFICATE_BYTES = 1024 * 1024
    private const val MAX_ADDITIONAL_DATA_BYTES = 4096
    private const val MAX_PUBLIC_KEY_BYTES = 64 * 1024
    private const val MAX_SIGNATURE_BYTES = 64 * 1024
    private const val MAX_MERKLE_TREE_BYTES = 40 * 1024 * 1024
    private const val FIXED_SIGNED_DATA_BYTES = 4 + 8 + 4 + 1
    private const val MAX_DETAIL_CHARS = 240

    private val V4_DIGEST_ALGORITHM_PRIORITY = listOf(
        setOf(0x0102, 0x0104, 0x0202),
        setOf(0x0421, 0x0423, 0x0425),
        setOf(0x0101, 0x0103, 0x0201, 0x0301),
    )
}
