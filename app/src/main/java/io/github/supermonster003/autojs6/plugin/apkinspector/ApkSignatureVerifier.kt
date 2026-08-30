package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
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

internal enum class ApkSignatureVerificationState {
    PRESENT,
    VERIFIED,
    FAILED,
}

internal enum class ApkSignatureFailureReason {
    MALFORMED_APK,
    MALFORMED_SIGNING_BLOCK,
    MALFORMED_SIGNER,
    NO_SIGNERS,
    NO_SIGNATURES,
    NO_CERTIFICATES,
    UNSUPPORTED_SIGNATURE_ALGORITHM,
    CRYPTO_PROVIDER_UNAVAILABLE,
    MALFORMED_PUBLIC_KEY,
    MALFORMED_CERTIFICATE,
    SIGNATURE_DID_NOT_VERIFY,
    PUBLIC_KEY_MISMATCH,
    SIGNATURE_DIGEST_ALGORITHMS_MISMATCH,
    CONTENT_DIGEST_MISMATCH,
    INVALID_SDK_RANGE,
    SDK_RANGE_MISMATCH,
    MALFORMED_LINEAGE,
    LINEAGE_SIGNATURE_DID_NOT_VERIFY,
    LINEAGE_CERTIFICATE_MISMATCH,
    DUPLICATE_LINEAGE_CERTIFICATE,
    INCONSISTENT_LINEAGES,
    V31_BLOCK_MISSING,
    V3_BLOCK_MISSING,
    ROTATION_MIN_SDK_MISMATCH,
    MALFORMED_V4_SIGNATURE,
    UNSUPPORTED_V4_VERSION,
    UNSUPPORTED_V4_HASHING,
    V4_ROOT_HASH_MISMATCH,
    V4_MERKLE_TREE_MISMATCH,
    V4_APK_DIGEST_MISMATCH,
    V4_SIGNER_MISMATCH,
    V4_SIGNER_COUNT_MISMATCH,
    V4_COMPLEMENTARY_SCHEME_MISSING,
    VERIFICATION_LIMIT_EXCEEDED,
    IO_ERROR,
}

internal enum class ApkSigningCertificateCapability(val mask: Int) {
    INSTALLED_DATA(1),
    SHARED_UID(2),
    SIGNATURE_PERMISSION(4),
    ROLLBACK(8),
    AUTHENTICATION(16),
}

internal data class ApkSigningCertificateLineageNode(
    val certificate: SigningCertificateDetails,
    val capabilityFlags: Int,
)

internal data class ApkSigningCertificateLineage(
    /** Ordered from the original signer to the current signer. */
    val certificates: List<ApkSigningCertificateLineageNode>,
)

internal data class ApkSchemeVerification(
    val state: ApkSignatureVerificationState,
    val signerCount: Int = 0,
    val reason: ApkSignatureFailureReason? = null,
    val detail: String? = null,
    val lineage: ApkSigningCertificateLineage? = null,
    val minimumSdkVersion: Int? = null,
    val rotationMinSdkVersion: Int? = null,
    val verifiedSigners: List<ApkVerifiedSigner> = emptyList(),
)

internal data class ApkVerifiedSigner(
    val certificateEncoding: ByteArray,
    val contentDigestsBySignatureAlgorithm: Map<Int, ByteArray>,
)

internal data class ApkSignatureVerification(
    val hasV1: Boolean,
    val v2: ApkSchemeVerification?,
    val v3: ApkSchemeVerification?,
    val v31: ApkSchemeVerification?,
    val v4: ApkSchemeVerification?,
    val lineage: ApkSigningCertificateLineage?,
) {
    val hasV31: Boolean
        get() = v31 != null
}

/**
 * On-device, bounded verifier for APK Signature Scheme v2, v3, v3.1, and a separately supplied
 * V4 idsig.
 *
 * The official apksig library is documented as an off-device JVM tool. This implementation uses
 * only public JCA and java.io APIs available at this app's API 24 minimum. Its wire parsing and
 * digest calculation follow the public Android v2/v3/v3.1 signing specifications, including
 * cryptographic verification of proof-of-rotation certificate lineages. V4 parsing, signed-data
 * verification, complementary-scheme matching, and fs-verity validation are delegated to the
 * bounded companion verifier.
 */
internal object ApkSignatureVerifier {

    private const val BLOCK_ID_V2 = 0x7109871A
    private const val BLOCK_ID_V3 = 0xF05368C0.toInt()
    private const val BLOCK_ID_V3_1 = 0x1B93AD61
    private const val PROOF_OF_ROTATION_ATTRIBUTE_ID = 0x3BA06F8C
    private const val ROTATION_MIN_SDK_ATTRIBUTE_ID = 0x559F8B02
    private const val ROTATION_ON_DEV_RELEASE_ATTRIBUTE_ID = 0xC2A6B3BA.toInt()
    private const val LINEAGE_FORMAT_VERSION = 1
    private const val ZIP_EOCD_MIN_SIZE = 22
    private const val ZIP_MAX_COMMENT_SIZE = 65_535
    private const val ZIP_EOCD_CENTRAL_DIRECTORY_OFFSET = 16
    private const val SIGNING_BLOCK_FOOTER_SIZE = 24
    private const val SIGNING_BLOCK_MIN_SIZE = 32
    private const val MAX_SIGNING_BLOCK_PAIRS = 4_096
    private const val MAX_SCHEME_BLOCK_BYTES = 16 * 1024 * 1024
    private const val MAX_SIGNER_BYTES = 8 * 1024 * 1024
    private const val MAX_SIGNERS = 64
    private const val MAX_SIGNATURES_PER_SIGNER = 64
    private const val MAX_CERTIFICATES_PER_SIGNER = 64
    private const val MAX_ATTRIBUTES_PER_SIGNER = 256
    private const val MAX_LINEAGE_CERTIFICATES = 64
    private const val MAX_LINEAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_CERTIFICATE_BYTES = 1024 * 1024
    private const val MAX_PUBLIC_KEY_BYTES = 64 * 1024
    private const val MAX_SIGNATURE_BYTES = 64 * 1024
    private const val MAX_DIGEST_BYTES = 1024
    private const val CONTENT_CHUNK_BYTES = 1024 * 1024
    private const val STREAM_BUFFER_BYTES = 64 * 1024
    private const val MAX_DETAIL_CHARS = 240
    private val SIGNING_BLOCK_MAGIC = "APK Sig Block 42".toByteArray(StandardCharsets.US_ASCII)
    private val LINEAGE_FAILURE_REASONS = setOf(
        ApkSignatureFailureReason.MALFORMED_LINEAGE,
        ApkSignatureFailureReason.LINEAGE_SIGNATURE_DID_NOT_VERIFY,
        ApkSignatureFailureReason.LINEAGE_CERTIFICATE_MISMATCH,
        ApkSignatureFailureReason.DUPLICATE_LINEAGE_CERTIFICATE,
        ApkSignatureFailureReason.INCONSISTENT_LINEAGES,
    )

    fun verify(apkFile: File, v4IdsigFile: File? = null): ApkSignatureVerification {
        val hasV1 = apkFile.isFile && runCatching {
            ApkSignatureDetector.hasV1Signature(apkFile)
        }.getOrDefault(false)
        if (!apkFile.isFile) {
            return ApkSignatureVerification(hasV1, null, null, null, null, null)
        }

        val baseVerification = try {
            RandomAccessFile(apkFile, "r").use { apk ->
                val zipLayout = findZipLayout(apk)
                val blocks = readSigningBlocks(apk, zipLayout)
                val digestCache = mutableMapOf<ContentDigestAlgorithm, ByteArray>()
                val digestProvider: (ContentDigestAlgorithm) -> ByteArray = { algorithm ->
                    digestCache.getOrPut(algorithm) {
                        computeChunkedContentDigest(apk, zipLayout, blocks.signingBlockOffset, algorithm)
                    }
                }
                val v2 = blocks.v2?.toVerification(Scheme.V2, digestProvider)
                var v3 = blocks.v3?.toVerification(Scheme.V3, digestProvider)
                var v31 = blocks.v31?.toVerification(Scheme.V31, digestProvider)
                val v31MinimumSdkVersion = v31?.minimumSdkVersion

                v3?.rotationMinSdkVersion?.let { expectedMinimumSdk ->
                    v3 = when {
                        v31 == null -> v3?.withFailure(
                            ApkSignatureFailureReason.V31_BLOCK_MISSING,
                            "expected V3.1 minimum SDK $expectedMinimumSdk",
                        )

                        v31MinimumSdkVersion != null &&
                            v31MinimumSdkVersion != expectedMinimumSdk -> v3?.withFailure(
                            ApkSignatureFailureReason.ROTATION_MIN_SDK_MISMATCH,
                            "V3 attribute $expectedMinimumSdk, V3.1 $v31MinimumSdkVersion",
                        )

                        else -> v3
                    }
                }
                if (v31 != null && v3 == null) {
                    v31 = v31?.withFailure(
                        ApkSignatureFailureReason.V3_BLOCK_MISSING,
                        "V3.1 requires a V3 base block",
                    )
                }

                var lineage: ApkSigningCertificateLineage? = null
                val verifiedLineages = listOfNotNull(
                    v3?.takeIf { it.state == ApkSignatureVerificationState.VERIFIED }?.lineage,
                    v31?.takeIf { it.state == ApkSignatureVerificationState.VERIFIED }?.lineage,
                )
                try {
                    lineage = consolidateLineages(verifiedLineages)
                } catch (error: SchemeVerificationException) {
                    v3 = v3?.takeIf { it.lineage != null }?.withFailure(
                        error.reason,
                        error.message,
                    ) ?: v3
                    v31 = v31?.takeIf { it.lineage != null }?.withFailure(
                        error.reason,
                        error.message,
                    ) ?: v31
                }

                ApkSignatureVerification(
                    hasV1 = hasV1,
                    v2 = v2,
                    v3 = v3,
                    v31 = v31,
                    v4 = null,
                    lineage = lineage,
                )
            }
        } catch (error: Exception) {
            fallbackToPresence(apkFile, hasV1, error)
        }
        return if (v4IdsigFile != null) {
            baseVerification.copy(
                v4 = ApkV4SignatureVerifier.verify(
                    apkFile = apkFile,
                    idsigFile = v4IdsigFile,
                    v2 = baseVerification.v2,
                    v3 = baseVerification.v3,
                    v31 = baseVerification.v31,
                ),
            )
        } else {
            baseVerification
        }
    }

    private fun ApkSchemeVerification.withFailure(
        failureReason: ApkSignatureFailureReason,
        failureDetail: String?,
    ): ApkSchemeVerification = if (state == ApkSignatureVerificationState.VERIFIED) {
        copy(
            state = ApkSignatureVerificationState.FAILED,
            reason = failureReason,
            detail = sanitizeDetail(failureDetail),
            lineage = null,
        )
    } else {
        this
    }

    private fun SchemeBlock.toVerification(
        scheme: Scheme,
        digestProvider: (ContentDigestAlgorithm) -> ByteArray,
    ): ApkSchemeVerification = when (this) {
        is SchemeBlock.Unavailable -> ApkSchemeVerification(
            state = if (reason == ApkSignatureFailureReason.MALFORMED_SIGNING_BLOCK) {
                ApkSignatureVerificationState.FAILED
            } else {
                ApkSignatureVerificationState.PRESENT
            },
            reason = reason,
            detail = sanitizeDetail(detail),
        )

        is SchemeBlock.Encoded -> try {
            verifyScheme(bytes, scheme, digestProvider)
        } catch (error: SchemeUnavailableException) {
            ApkSchemeVerification(
                state = ApkSignatureVerificationState.PRESENT,
                reason = error.reason,
                detail = sanitizeDetail(error.message),
            )
        } catch (error: SchemeVerificationException) {
            ApkSchemeVerification(
                state = ApkSignatureVerificationState.FAILED,
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
                reason = ApkSignatureFailureReason.MALFORMED_SIGNER,
                detail = sanitizeDetail(error.message),
            )
        }
    }

    private fun verifyScheme(
        block: ByteArray,
        scheme: Scheme,
        digestProvider: (ContentDigestAlgorithm) -> ByteArray,
    ): ApkSchemeVerification {
        val blockReader = block.asLittleEndianBuffer()
        val signers = blockReader.readLengthPrefixedSlice(
            label = "${scheme.label} signers",
            maxBytes = MAX_SCHEME_BLOCK_BYTES,
        )
        blockReader.requireFullyConsumed("${scheme.label} block")
        if (!signers.hasRemaining()) {
            throw SchemeVerificationException(ApkSignatureFailureReason.NO_SIGNERS)
        }

        val parsedSigners = mutableListOf<ParsedSigner>()
        while (signers.hasRemaining()) {
            if (parsedSigners.size >= MAX_SIGNERS) {
                throw SchemeUnavailableException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "${scheme.label} signer count exceeds $MAX_SIGNERS",
                )
            }
            val signerIndex = parsedSigners.size + 1
            val signer = signers.readLengthPrefixedSlice(
                label = "${scheme.label} signer $signerIndex",
                maxBytes = MAX_SIGNER_BYTES,
            )
            try {
                parsedSigners += parseAndVerifySigner(signer, scheme)
            } catch (error: SchemeVerificationException) {
                throw error.withSigner(signerIndex)
            } catch (error: SchemeUnavailableException) {
                throw SchemeUnavailableException(
                    error.reason,
                    "signer $signerIndex: ${error.message.orEmpty()}",
                )
            }
        }

        parsedSigners.forEachIndexed { signerIndex, signer ->
            signer.expectedContentDigests.forEach { (algorithm, expectedDigest) ->
                val actualDigest = try {
                    digestProvider(algorithm)
                } catch (error: NoSuchAlgorithmException) {
                    throw SchemeUnavailableException(
                        ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
                        algorithm.jcaName,
                    )
                }
                if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
                    throw SchemeVerificationException(
                        reason = ApkSignatureFailureReason.CONTENT_DIGEST_MISMATCH,
                        message = "signer ${signerIndex + 1}, ${algorithm.jcaName}",
                        signerCount = parsedSigners.size,
                    )
                }
            }
        }

        val rotationMinimumSdks = parsedSigners.mapNotNull(ParsedSigner::rotationMinSdkVersion)
            .distinct()
        if (rotationMinimumSdks.size > 1) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.ROTATION_MIN_SDK_MISMATCH,
                rotationMinimumSdks.joinToString(),
                parsedSigners.size,
            )
        }

        return ApkSchemeVerification(
            state = ApkSignatureVerificationState.VERIFIED,
            signerCount = parsedSigners.size,
            lineage = consolidateLineages(parsedSigners.mapNotNull(ParsedSigner::lineage)),
            minimumSdkVersion = parsedSigners.mapNotNull(ParsedSigner::minimumSdkVersion).minOrNull(),
            rotationMinSdkVersion = rotationMinimumSdks.singleOrNull(),
            verifiedSigners = parsedSigners.map { signer ->
                ApkVerifiedSigner(
                    certificateEncoding = signer.certificateEncoding,
                    contentDigestsBySignatureAlgorithm = signer.contentDigestsBySignatureAlgorithm,
                )
            },
        )
    }

    private fun parseAndVerifySigner(
        signer: ByteBuffer,
        scheme: Scheme,
    ): ParsedSigner {
        val signedData = signer.readLengthPrefixedBytes(
            label = "signed data",
            maxBytes = MAX_SIGNER_BYTES,
        )
        val outerMinSdk: Int?
        val outerMaxSdk: Int?
        if (scheme.hasSdkRange) {
            outerMinSdk = signer.readInt32("minimum SDK")
            outerMaxSdk = signer.readInt32("maximum SDK")
            if (outerMinSdk < 0 || outerMinSdk > outerMaxSdk) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.INVALID_SDK_RANGE,
                    "$outerMinSdk..$outerMaxSdk",
                )
            }
        } else {
            outerMinSdk = null
            outerMaxSdk = null
        }
        val signatures = parseSignatures(
            signer.readLengthPrefixedSlice("signatures", MAX_SIGNER_BYTES),
        )
        val publicKeyBytes = signer.readLengthPrefixedBytes("public key", MAX_PUBLIC_KEY_BYTES)
        signer.requireFullyConsumed("signer")

        verifySignaturesOverSignedData(signatures, publicKeyBytes, signedData)

        val signedDataReader = signedData.asLittleEndianBuffer()
        val digests = parseDigests(
            signedDataReader.readLengthPrefixedSlice("digests", MAX_SIGNER_BYTES),
        )
        val certificates = parseCertificates(
            signedDataReader.readLengthPrefixedSlice("certificates", MAX_SIGNER_BYTES),
        )
        if (scheme.hasSdkRange) {
            val signedMinSdk = signedDataReader.readInt32("signed minimum SDK")
            val signedMaxSdk = signedDataReader.readInt32("signed maximum SDK")
            if (signedMinSdk != outerMinSdk || signedMaxSdk != outerMaxSdk) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.SDK_RANGE_MISMATCH,
                    "outer $outerMinSdk..$outerMaxSdk, signed $signedMinSdk..$signedMaxSdk",
                )
            }
        }
        val additionalAttributes = signedDataReader.readLengthPrefixedSlice(
            "additional attributes",
            MAX_SIGNER_BYTES,
        )
        if (scheme.hasSdkRange) {
            signedDataReader.requireFullyConsumed("signed data")
        }
        // Current AOSP apksigner appends a signed, empty extension field to V2 signed-data.
        // Android's V2 verifier intentionally ignores trailing signed-data bytes, so mirror that
        // behavior while still authenticating the complete byte array above.

        val signerCertificate = certificates.firstOrNull()
            ?: throw SchemeVerificationException(ApkSignatureFailureReason.NO_CERTIFICATES)
        if (!MessageDigest.isEqual(signerCertificate.certificate.publicKey.encoded, publicKeyBytes)) {
            throw SchemeVerificationException(ApkSignatureFailureReason.PUBLIC_KEY_MISMATCH)
        }
        val parsedAttributes = parseAdditionalAttributes(
            attributes = additionalAttributes,
            scheme = scheme,
            signerCertificate = signerCertificate,
        )

        val signatureAlgorithmIds = signatures.map(SignatureRecord::algorithmId)
        val digestAlgorithmIds = digests.map(DigestRecord::algorithmId)
        if (signatureAlgorithmIds != digestAlgorithmIds) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.SIGNATURE_DIGEST_ALGORITHMS_MISMATCH,
            )
        }

        val expectedContentDigests = linkedMapOf<ContentDigestAlgorithm, ByteArray>()
        signatures.forEach { signature ->
            val algorithm = signature.algorithm ?: return@forEach
            val contentDigestAlgorithm = algorithm.contentDigestAlgorithm ?: return@forEach
            val digest = digests.first { it.algorithmId == signature.algorithmId }.bytes
            if (digest.size != contentDigestAlgorithm.outputBytes) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_SIGNER,
                    "${contentDigestAlgorithm.jcaName} digest has ${digest.size} bytes",
                )
            }
            val previous = expectedContentDigests.putIfAbsent(contentDigestAlgorithm, digest)
            if (previous != null && !MessageDigest.isEqual(previous, digest)) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_SIGNER,
                    "inconsistent ${contentDigestAlgorithm.jcaName} digests",
                )
            }
        }
        if (expectedContentDigests.isEmpty()) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.UNSUPPORTED_SIGNATURE_ALGORITHM,
                "no supported chunked content digest",
            )
        }
        return ParsedSigner(
            expectedContentDigests = expectedContentDigests,
            certificateEncoding = signerCertificate.encoded,
            contentDigestsBySignatureAlgorithm = digests.associate { digest ->
                digest.algorithmId to digest.bytes
            },
            lineage = parsedAttributes.lineage,
            minimumSdkVersion = outerMinSdk,
            rotationMinSdkVersion = parsedAttributes.rotationMinSdkVersion,
        )
    }

    private fun parseSignatures(signatures: ByteBuffer): List<SignatureRecord> {
        val result = mutableListOf<SignatureRecord>()
        val seenAlgorithmIds = mutableSetOf<Int>()
        while (signatures.hasRemaining()) {
            if (result.size >= MAX_SIGNATURES_PER_SIGNER) {
                throw SchemeUnavailableException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "signature count exceeds $MAX_SIGNATURES_PER_SIGNER",
                )
            }
            val record = signatures.readLengthPrefixedSlice("signature", MAX_SIGNATURE_BYTES + 8)
            val algorithmId = record.readInt32("signature algorithm")
            val bytes = record.readLengthPrefixedBytes("signature bytes", MAX_SIGNATURE_BYTES)
            record.requireFullyConsumed("signature record")
            if (!seenAlgorithmIds.add(algorithmId)) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_SIGNER,
                    "duplicate signature algorithm ${algorithmId.toHexId()}",
                )
            }
            result += SignatureRecord(
                algorithmId = algorithmId,
                bytes = bytes,
                algorithm = SignatureAlgorithm.fromId(algorithmId),
            )
        }
        if (result.isEmpty()) {
            throw SchemeVerificationException(ApkSignatureFailureReason.NO_SIGNATURES)
        }
        if (result.none { it.algorithm != null }) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.UNSUPPORTED_SIGNATURE_ALGORITHM,
                result.joinToString { it.algorithmId.toHexId() },
            )
        }
        return result
    }

    private fun parseDigests(digests: ByteBuffer): List<DigestRecord> {
        val result = mutableListOf<DigestRecord>()
        val seenAlgorithmIds = mutableSetOf<Int>()
        while (digests.hasRemaining()) {
            if (result.size >= MAX_SIGNATURES_PER_SIGNER) {
                throw SchemeUnavailableException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "digest count exceeds $MAX_SIGNATURES_PER_SIGNER",
                )
            }
            val record = digests.readLengthPrefixedSlice("digest", MAX_DIGEST_BYTES + 8)
            val algorithmId = record.readInt32("digest algorithm")
            val bytes = record.readLengthPrefixedBytes("digest bytes", MAX_DIGEST_BYTES)
            record.requireFullyConsumed("digest record")
            if (!seenAlgorithmIds.add(algorithmId)) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_SIGNER,
                    "duplicate digest algorithm ${algorithmId.toHexId()}",
                )
            }
            result += DigestRecord(algorithmId, bytes)
        }
        return result
    }

    private fun parseCertificates(certificates: ByteBuffer): List<ParsedCertificate> {
        val result = mutableListOf<ParsedCertificate>()
        val factory = try {
            CertificateFactory.getInstance("X.509")
        } catch (error: CertificateException) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
                "X.509",
            )
        }
        while (certificates.hasRemaining()) {
            if (result.size >= MAX_CERTIFICATES_PER_SIGNER) {
                throw SchemeUnavailableException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "certificate count exceeds $MAX_CERTIFICATES_PER_SIGNER",
                )
            }
            val encoded = certificates.readLengthPrefixedBytes(
                label = "certificate",
                maxBytes = MAX_CERTIFICATE_BYTES,
            )
            val certificate = try {
                factory.generateCertificate(encoded.inputStream()) as? X509Certificate
            } catch (error: CertificateException) {
                null
            } ?: throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_CERTIFICATE,
                "certificate ${result.size + 1}",
            )
            result += ParsedCertificate(certificate, encoded)
        }
        return result
    }

    private fun parseAdditionalAttributes(
        attributes: ByteBuffer,
        scheme: Scheme,
        signerCertificate: ParsedCertificate,
    ): ParsedAdditionalAttributes {
        var count = 0
        var lineage: ApkSigningCertificateLineage? = null
        var rotationMinSdkVersion: Int? = null
        while (attributes.hasRemaining()) {
            count++
            if (count > MAX_ATTRIBUTES_PER_SIGNER) {
                throw SchemeUnavailableException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "attribute count exceeds $MAX_ATTRIBUTES_PER_SIGNER",
                )
            }
            val attribute = attributes.readLengthPrefixedSlice("additional attribute", MAX_SIGNER_BYTES)
            val id = attribute.readInt32("attribute ID")
            when {
                id == PROOF_OF_ROTATION_ATTRIBUTE_ID && scheme.hasSdkRange -> {
                    if (lineage != null) {
                        throw SchemeVerificationException(
                            ApkSignatureFailureReason.MALFORMED_LINEAGE,
                            "duplicate proof-of-rotation attribute",
                        )
                    }
                    val value = attribute.readRemainingBytes("proof-of-rotation", MAX_LINEAGE_BYTES)
                    lineage = parseAndVerifyLineage(value, signerCertificate)
                }

                id == ROTATION_MIN_SDK_ATTRIBUTE_ID && scheme == Scheme.V3 -> {
                    if (rotationMinSdkVersion != null) {
                        throw SchemeVerificationException(
                            ApkSignatureFailureReason.MALFORMED_SIGNER,
                            "duplicate rotation minimum SDK attribute",
                        )
                    }
                    rotationMinSdkVersion = attribute.readInt32("rotation minimum SDK")
                    attribute.requireFullyConsumed("rotation minimum SDK attribute")
                    if (rotationMinSdkVersion < 0) {
                        throw SchemeVerificationException(
                            ApkSignatureFailureReason.INVALID_SDK_RANGE,
                            rotationMinSdkVersion.toString(),
                        )
                    }
                }

                id == ROTATION_ON_DEV_RELEASE_ATTRIBUTE_ID && scheme == Scheme.V31 -> {
                    attribute.position(attribute.limit())
                }

                else -> attribute.position(attribute.limit())
            }
        }
        return ParsedAdditionalAttributes(lineage, rotationMinSdkVersion)
    }

    private fun parseAndVerifyLineage(
        encodedLineage: ByteArray,
        signerCertificate: ParsedCertificate,
    ): ApkSigningCertificateLineage {
        try {
            val reader = encodedLineage.asLittleEndianBuffer()
            val version = reader.readInt32("lineage version")
            if (version != LINEAGE_FORMAT_VERSION) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_LINEAGE,
                    "unsupported lineage version $version",
                )
            }
            val factory = try {
                CertificateFactory.getInstance("X.509")
            } catch (error: CertificateException) {
                throw SchemeUnavailableException(
                    ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
                    "X.509",
                )
            }
            val nodes = mutableListOf<ParsedLineageNode>()
            while (reader.hasRemaining()) {
                if (nodes.size >= MAX_LINEAGE_CERTIFICATES) {
                    throw SchemeUnavailableException(
                        ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                        "lineage certificate count exceeds $MAX_LINEAGE_CERTIFICATES",
                    )
                }
                val nodeIndex = nodes.size + 1
                val node = reader.readLengthPrefixedSlice(
                    "lineage node $nodeIndex",
                    MAX_LINEAGE_BYTES,
                )
                val signedData = node.readLengthPrefixedBytes(
                    "lineage signed data $nodeIndex",
                    MAX_LINEAGE_BYTES,
                )
                val capabilityFlags = node.readInt32("lineage capability flags")
                val childSignatureAlgorithmId = node.readInt32("lineage child signature algorithm")
                val signature = node.readLengthPrefixedBytes(
                    "lineage signature $nodeIndex",
                    MAX_SIGNATURE_BYTES,
                )
                node.requireFullyConsumed("lineage node $nodeIndex")

                val signedDataReader = signedData.asLittleEndianBuffer()
                val encodedCertificate = signedDataReader.readLengthPrefixedBytes(
                    "lineage certificate $nodeIndex",
                    MAX_CERTIFICATE_BYTES,
                )
                val parentSignatureAlgorithmId = signedDataReader.readInt32(
                    "lineage parent signature algorithm",
                )
                signedDataReader.requireFullyConsumed("lineage signed data $nodeIndex")

                val previous = nodes.lastOrNull()
                if (previous != null) {
                    if (previous.childSignatureAlgorithmId != parentSignatureAlgorithmId) {
                        throw SchemeVerificationException(
                            ApkSignatureFailureReason.LINEAGE_SIGNATURE_DID_NOT_VERIFY,
                            "certificate $nodeIndex algorithm IDs differ",
                        )
                    }
                    val algorithm = SignatureAlgorithm.fromId(parentSignatureAlgorithmId)
                        ?: throw SchemeUnavailableException(
                            ApkSignatureFailureReason.UNSUPPORTED_SIGNATURE_ALGORITHM,
                            "lineage ${parentSignatureAlgorithmId.toHexId()}",
                        )
                    verifySignature(
                        algorithm = algorithm,
                        publicKey = previous.certificate.certificate.publicKey,
                        signedData = signedData,
                        signatureBytes = signature,
                        failureReason = ApkSignatureFailureReason.LINEAGE_SIGNATURE_DID_NOT_VERIFY,
                    )
                }

                if (nodes.any { it.certificate.encoded.contentEquals(encodedCertificate) }) {
                    throw SchemeVerificationException(
                        ApkSignatureFailureReason.DUPLICATE_LINEAGE_CERTIFICATE,
                        "certificate $nodeIndex",
                    )
                }
                val certificate = try {
                    factory.generateCertificate(encodedCertificate.inputStream()) as? X509Certificate
                } catch (error: CertificateException) {
                    null
                } ?: throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_LINEAGE,
                    "certificate $nodeIndex",
                )
                nodes += ParsedLineageNode(
                    certificate = ParsedCertificate(certificate, encodedCertificate),
                    capabilityFlags = capabilityFlags,
                    childSignatureAlgorithmId = childSignatureAlgorithmId,
                )
            }
            if (nodes.isEmpty()) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_LINEAGE,
                    "lineage has no certificates",
                )
            }
            if (!nodes.last().certificate.encoded.contentEquals(signerCertificate.encoded)) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.LINEAGE_CERTIFICATE_MISMATCH,
                    "lineage leaf does not match the current signer",
                )
            }
            return ApkSigningCertificateLineage(
                certificates = nodes.map { node ->
                    ApkSigningCertificateLineageNode(
                        certificate = SigningCertificateParser.parse(node.certificate.encoded),
                        capabilityFlags = node.capabilityFlags,
                    )
                },
            )
        } catch (error: SchemeUnavailableException) {
            throw error
        } catch (error: SchemeVerificationException) {
            if (error.reason in LINEAGE_FAILURE_REASONS) throw error
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_LINEAGE,
                error.message,
            )
        } catch (error: Exception) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_LINEAGE,
                error.message,
            )
        }
    }

    private fun consolidateLineages(
        lineages: List<ApkSigningCertificateLineage>,
    ): ApkSigningCertificateLineage? {
        val longest = lineages.maxByOrNull { it.certificates.size } ?: return null
        lineages.forEach { candidate ->
            val candidateFingerprints = candidate.certificates.map {
                it.certificate.sha256Fingerprint
            }
            val expectedPrefix = longest.certificates.take(candidate.certificates.size).map {
                it.certificate.sha256Fingerprint
            }
            if (candidateFingerprints != expectedPrefix) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.INCONSISTENT_LINEAGES,
                    "verified lineages do not share the same certificate prefix",
                )
            }
        }
        return longest
    }

    private fun verifySignaturesOverSignedData(
        signatures: List<SignatureRecord>,
        publicKeyBytes: ByteArray,
        signedData: ByteArray,
    ) {
        val publicKeys = mutableMapOf<String, PublicKey>()
        signatures.forEach { record ->
            val algorithm = record.algorithm ?: return@forEach
            val publicKey = publicKeys.getOrPut(algorithm.keyAlgorithm) {
                decodePublicKey(algorithm.keyAlgorithm, publicKeyBytes)
            }
            verifySignature(
                algorithm = algorithm,
                publicKey = publicKey,
                signedData = signedData,
                signatureBytes = record.bytes,
                failureReason = ApkSignatureFailureReason.SIGNATURE_DID_NOT_VERIFY,
            )
        }
    }

    private fun verifySignature(
        algorithm: SignatureAlgorithm,
        publicKey: PublicKey,
        signedData: ByteArray,
        signatureBytes: ByteArray,
        failureReason: ApkSignatureFailureReason,
    ) {
        val verifier = createSignatureVerifier(algorithm)
        try {
            verifier.initVerify(publicKey)
            algorithm.pssParameters?.let(verifier::setParameter)
            verifier.update(signedData)
            if (!verifier.verify(signatureBytes)) {
                throw SchemeVerificationException(failureReason, algorithm.displayName)
            }
        } catch (error: SchemeVerificationException) {
            throw error
        } catch (error: InvalidKeyException) {
            throw SchemeVerificationException(failureReason, error.message)
        } catch (error: InvalidAlgorithmParameterException) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
                "${algorithm.displayName}: ${error.message.orEmpty()}",
            )
        } catch (error: SignatureException) {
            throw SchemeVerificationException(
                failureReason,
                "${algorithm.displayName}: ${error.message.orEmpty()}",
            )
        }
    }

    private fun decodePublicKey(keyAlgorithm: String, encoded: ByteArray): PublicKey {
        return try {
            KeyFactory.getInstance(keyAlgorithm).generatePublic(X509EncodedKeySpec(encoded))
        } catch (error: NoSuchAlgorithmException) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
                keyAlgorithm,
            )
        } catch (error: InvalidKeySpecException) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_PUBLIC_KEY,
                error.message,
            )
        }
    }

    private fun createSignatureVerifier(algorithm: SignatureAlgorithm): Signature {
        var lastError: NoSuchAlgorithmException? = null
        algorithm.jcaNames.forEach { name ->
            try {
                return Signature.getInstance(name)
            } catch (error: NoSuchAlgorithmException) {
                lastError = error
            }
        }
        throw SchemeUnavailableException(
            ApkSignatureFailureReason.CRYPTO_PROVIDER_UNAVAILABLE,
            "${algorithm.displayName}: ${lastError?.message.orEmpty()}",
        )
    }

    private fun findZipLayout(apk: RandomAccessFile): ZipLayout {
        val fileSize = apk.length()
        if (fileSize < ZIP_EOCD_MIN_SIZE) {
            throw SchemeVerificationException(ApkSignatureFailureReason.MALFORMED_APK, "ZIP EOCD missing")
        }
        val tailSize = minOf(fileSize, (ZIP_EOCD_MIN_SIZE + ZIP_MAX_COMMENT_SIZE).toLong()).toInt()
        val tail = ByteArray(tailSize)
        apk.seek(fileSize - tailSize)
        apk.readFully(tail)
        for (index in tailSize - ZIP_EOCD_MIN_SIZE downTo 0) {
            if (!tail.hasEocdSignatureAt(index)) continue
            val commentLength = tail.readUnsignedShortLittleEndian(index + 20)
            if (index + ZIP_EOCD_MIN_SIZE + commentLength != tailSize) continue
            val centralDirectoryOffset = tail.readUnsignedIntLittleEndian(
                index + ZIP_EOCD_CENTRAL_DIRECTORY_OFFSET,
            )
            if (centralDirectoryOffset == 0xFFFF_FFFFL) {
                throw SchemeUnavailableException(
                    ApkSignatureFailureReason.MALFORMED_APK,
                    "ZIP64 APK verification is not supported",
                )
            }
            val eocdOffset = fileSize - tailSize + index
            if (centralDirectoryOffset > eocdOffset) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_APK,
                    "central directory offset exceeds EOCD offset",
                )
            }
            return ZipLayout(
                centralDirectoryOffset = centralDirectoryOffset,
                eocdOffset = eocdOffset,
                eocd = tail.copyOfRange(index, tailSize),
            )
        }
        throw SchemeVerificationException(ApkSignatureFailureReason.MALFORMED_APK, "ZIP EOCD missing")
    }

    private fun readSigningBlocks(
        apk: RandomAccessFile,
        zipLayout: ZipLayout,
    ): SigningBlocks {
        val centralDirectoryOffset = zipLayout.centralDirectoryOffset
        if (centralDirectoryOffset < SIGNING_BLOCK_MIN_SIZE) {
            return SigningBlocks(signingBlockOffset = centralDirectoryOffset)
        }
        apk.seek(centralDirectoryOffset - SIGNING_BLOCK_FOOTER_SIZE)
        val blockSizeInFooter = apk.readLittleEndianLong()
        val magic = ByteArray(SIGNING_BLOCK_MAGIC.size)
        apk.readFully(magic)
        if (!magic.contentEquals(SIGNING_BLOCK_MAGIC)) {
            return SigningBlocks(signingBlockOffset = centralDirectoryOffset)
        }
        if (blockSizeInFooter < SIGNING_BLOCK_FOOTER_SIZE || blockSizeInFooter > centralDirectoryOffset - 8) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_SIGNING_BLOCK,
                "invalid block size $blockSizeInFooter",
            )
        }
        val blockStart = centralDirectoryOffset - blockSizeInFooter - 8
        apk.seek(blockStart)
        if (apk.readLittleEndianLong() != blockSizeInFooter) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_SIGNING_BLOCK,
                "header and footer sizes differ",
            )
        }

        val entriesEnd = centralDirectoryOffset - SIGNING_BLOCK_FOOTER_SIZE
        var v2: SchemeBlock? = null
        var v3: SchemeBlock? = null
        var v31: SchemeBlock? = null
        var pairCount = 0
        while (apk.filePointer < entriesEnd) {
            pairCount++
            if (pairCount > MAX_SIGNING_BLOCK_PAIRS) {
                throw SchemeUnavailableException(
                    ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                    "signing block pair count exceeds $MAX_SIGNING_BLOCK_PAIRS",
                )
            }
            val pairSize = apk.readLittleEndianLong()
            if (pairSize < 4 || pairSize > entriesEnd - apk.filePointer) {
                throw SchemeVerificationException(
                    ApkSignatureFailureReason.MALFORMED_SIGNING_BLOCK,
                    "invalid pair size $pairSize",
                )
            }
            val id = apk.readLittleEndianInt()
            val valueSize = pairSize - 4
            val valueStart = apk.filePointer
            when (id) {
                BLOCK_ID_V2 -> v2 = readSchemeBlock(apk, valueSize, "V2", v2)
                BLOCK_ID_V3 -> v3 = readSchemeBlock(apk, valueSize, "V3", v3)
                BLOCK_ID_V3_1 -> v31 = readSchemeBlock(apk, valueSize, "V3.1", v31)
            }
            apk.seek(valueStart + valueSize)
        }
        if (apk.filePointer != entriesEnd) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_SIGNING_BLOCK,
                "pairs are not aligned",
            )
        }
        return SigningBlocks(
            signingBlockOffset = blockStart,
            v2 = v2,
            v3 = v3,
            v31 = v31,
        )
    }

    private fun readSchemeBlock(
        apk: RandomAccessFile,
        valueSize: Long,
        label: String,
        existing: SchemeBlock?,
    ): SchemeBlock {
        if (existing != null) {
            return SchemeBlock.Unavailable(
                ApkSignatureFailureReason.MALFORMED_SIGNING_BLOCK,
                "duplicate $label block",
            )
        }
        if (valueSize > MAX_SCHEME_BLOCK_BYTES) {
            return SchemeBlock.Unavailable(
                ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                "$label block is $valueSize bytes",
            )
        }
        return SchemeBlock.Encoded(ByteArray(valueSize.toInt()).also(apk::readFully))
    }

    private fun computeChunkedContentDigest(
        apk: RandomAccessFile,
        zipLayout: ZipLayout,
        signingBlockOffset: Long,
        algorithm: ContentDigestAlgorithm,
    ): ByteArray {
        val centralDirectoryLength = zipLayout.eocdOffset - zipLayout.centralDirectoryOffset
        val patchedEocd = zipLayout.eocd.copyOf().also { eocd ->
            eocd.writeUnsignedIntLittleEndian(
                ZIP_EOCD_CENTRAL_DIRECTORY_OFFSET,
                signingBlockOffset,
            )
        }
        val chunkCountLong = chunkCount(signingBlockOffset) +
            chunkCount(centralDirectoryLength) +
            chunkCount(patchedEocd.size.toLong())
        if (chunkCountLong > Int.MAX_VALUE) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                "content chunk count exceeds ${Int.MAX_VALUE}",
            )
        }

        val topLevelDigest = MessageDigest.getInstance(algorithm.jcaName)
        topLevelDigest.update(byteArrayOf(0x5A))
        topLevelDigest.update(littleEndianInt(chunkCountLong))
        val chunkDigest = MessageDigest.getInstance(algorithm.jcaName)
        val streamBuffer = ByteArray(STREAM_BUFFER_BYTES)

        updateFileSectionDigests(
            apk = apk,
            offset = 0,
            length = signingBlockOffset,
            topLevelDigest = topLevelDigest,
            chunkDigest = chunkDigest,
            streamBuffer = streamBuffer,
        )
        updateFileSectionDigests(
            apk = apk,
            offset = zipLayout.centralDirectoryOffset,
            length = centralDirectoryLength,
            topLevelDigest = topLevelDigest,
            chunkDigest = chunkDigest,
            streamBuffer = streamBuffer,
        )
        updateMemorySectionDigests(
            bytes = patchedEocd,
            topLevelDigest = topLevelDigest,
            chunkDigest = chunkDigest,
        )
        return topLevelDigest.digest()
    }

    private fun updateFileSectionDigests(
        apk: RandomAccessFile,
        offset: Long,
        length: Long,
        topLevelDigest: MessageDigest,
        chunkDigest: MessageDigest,
        streamBuffer: ByteArray,
    ) {
        var sectionOffset = offset
        var remaining = length
        while (remaining > 0) {
            val chunkSize = minOf(remaining, CONTENT_CHUNK_BYTES.toLong()).toInt()
            chunkDigest.reset()
            chunkDigest.update(byteArrayOf(0xA5.toByte()))
            chunkDigest.update(littleEndianInt(chunkSize.toLong()))
            apk.seek(sectionOffset)
            var chunkRemaining = chunkSize
            while (chunkRemaining > 0) {
                val readSize = minOf(chunkRemaining, streamBuffer.size)
                apk.readFully(streamBuffer, 0, readSize)
                chunkDigest.update(streamBuffer, 0, readSize)
                chunkRemaining -= readSize
            }
            topLevelDigest.update(chunkDigest.digest())
            sectionOffset += chunkSize
            remaining -= chunkSize
        }
    }

    private fun updateMemorySectionDigests(
        bytes: ByteArray,
        topLevelDigest: MessageDigest,
        chunkDigest: MessageDigest,
    ) {
        var offset = 0
        while (offset < bytes.size) {
            val chunkSize = minOf(bytes.size - offset, CONTENT_CHUNK_BYTES)
            chunkDigest.reset()
            chunkDigest.update(byteArrayOf(0xA5.toByte()))
            chunkDigest.update(littleEndianInt(chunkSize.toLong()))
            chunkDigest.update(bytes, offset, chunkSize)
            topLevelDigest.update(chunkDigest.digest())
            offset += chunkSize
        }
    }

    private fun fallbackToPresence(
        apkFile: File,
        hasV1: Boolean,
        error: Exception,
    ): ApkSignatureVerification {
        val ids = runCatching { ApkSignatureDetector.readSigningBlockIds(apkFile) }
            .getOrDefault(emptySet())
        val reason = when (error) {
            is SchemeUnavailableException -> error.reason
            is SchemeVerificationException -> error.reason
            is IOException -> ApkSignatureFailureReason.IO_ERROR
            else -> ApkSignatureFailureReason.MALFORMED_APK
        }
        val fallbackState = if (error is SchemeVerificationException) {
            ApkSignatureVerificationState.FAILED
        } else {
            ApkSignatureVerificationState.PRESENT
        }
        fun resultIfFound(id: Int): ApkSchemeVerification? = if (id in ids) {
            ApkSchemeVerification(
                state = fallbackState,
                reason = reason,
                detail = sanitizeDetail(error.message),
            )
        } else {
            null
        }
        return ApkSignatureVerification(
            hasV1 = hasV1,
            v2 = resultIfFound(BLOCK_ID_V2),
            v3 = resultIfFound(BLOCK_ID_V3),
            v31 = resultIfFound(BLOCK_ID_V3_1),
            v4 = null,
            lineage = null,
        )
    }

    private fun ByteBuffer.readLengthPrefixedSlice(
        label: String,
        maxBytes: Int,
    ): ByteBuffer {
        val length = readUnsignedInt32("$label length")
        if (length > maxBytes) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                "$label is $length bytes",
            )
        }
        if (length > remaining()) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_SIGNER,
                "$label length $length exceeds ${remaining()} remaining bytes",
            )
        }
        return slice().order(ByteOrder.LITTLE_ENDIAN).apply {
            limit(length)
            this@readLengthPrefixedSlice.position(this@readLengthPrefixedSlice.position() + length)
        }
    }

    private fun ByteBuffer.readLengthPrefixedBytes(label: String, maxBytes: Int): ByteArray {
        val slice = readLengthPrefixedSlice(label, maxBytes)
        return ByteArray(slice.remaining()).also(slice::get)
    }

    private fun ByteBuffer.readRemainingBytes(label: String, maxBytes: Int): ByteArray {
        if (remaining() > maxBytes) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                "$label is ${remaining()} bytes",
            )
        }
        return ByteArray(remaining()).also(::get)
    }

    private fun ByteBuffer.readUnsignedInt32(label: String): Int {
        val signed = readInt32(label)
        val unsigned = signed.toLong() and 0xFFFF_FFFFL
        if (unsigned > Int.MAX_VALUE) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                "$label exceeds ${Int.MAX_VALUE}",
            )
        }
        return unsigned.toInt()
    }

    private fun ByteBuffer.readInt32(label: String): Int {
        if (remaining() < Int.SIZE_BYTES) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_SIGNER,
                "$label is truncated",
            )
        }
        return int
    }

    private fun ByteBuffer.requireFullyConsumed(label: String) {
        if (hasRemaining()) {
            throw SchemeVerificationException(
                ApkSignatureFailureReason.MALFORMED_SIGNER,
                "$label has ${remaining()} trailing bytes",
            )
        }
    }

    private fun ByteArray.asLittleEndianBuffer(): ByteBuffer =
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)

    private fun ByteArray.hasEocdSignatureAt(offset: Int): Boolean =
        this[offset] == 0x50.toByte() &&
            this[offset + 1] == 0x4B.toByte() &&
            this[offset + 2] == 0x05.toByte() &&
            this[offset + 3] == 0x06.toByte()

    private fun ByteArray.readUnsignedShortLittleEndian(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.readUnsignedIntLittleEndian(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)

    private fun ByteArray.writeUnsignedIntLittleEndian(offset: Int, value: Long) {
        if (value !in 0..0xFFFF_FFFFL) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.MALFORMED_APK,
                "ZIP offset exceeds the 32-bit EOCD range",
            )
        }
        repeat(4) { index ->
            this[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte()
        }
    }

    private fun RandomAccessFile.readLittleEndianLong(): Long {
        val bytes = ByteArray(Long.SIZE_BYTES)
        readFully(bytes)
        var value = 0L
        for (index in bytes.indices.reversed()) {
            value = (value shl 8) or (bytes[index].toLong() and 0xFF)
        }
        if (value < 0) throw IOException("Unsigned 64-bit value exceeds supported range")
        return value
    }

    private fun RandomAccessFile.readLittleEndianInt(): Int {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        if (b0 or b1 or b2 or b3 < 0) throw EOFException()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun littleEndianInt(value: Long): ByteArray {
        if (value !in 0..0xFFFF_FFFFL) {
            throw SchemeUnavailableException(
                ApkSignatureFailureReason.VERIFICATION_LIMIT_EXCEEDED,
                "unsigned 32-bit value overflow",
            )
        }
        return ByteArray(4) { index -> ((value ushr (index * 8)) and 0xFF).toByte() }
    }

    private fun chunkCount(byteCount: Long): Long = if (byteCount == 0L) {
        0
    } else {
        (byteCount - 1) / CONTENT_CHUNK_BYTES + 1
    }

    private fun Int.toHexId(): String = "0x" + (toLong() and 0xFFFF_FFFFL).toString(16)

    private fun sanitizeDetail(value: String?): String? = value
        ?.replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(MAX_DETAIL_CHARS)
        ?.takeIf(String::isNotEmpty)

    private enum class Scheme(
        val label: String,
        val hasSdkRange: Boolean,
    ) {
        V2("V2", false),
        V3("V3", true),
        V31("V3.1", true),
    }

    private enum class ContentDigestAlgorithm(
        val jcaName: String,
        val outputBytes: Int,
    ) {
        CHUNKED_SHA256("SHA-256", 32),
        CHUNKED_SHA512("SHA-512", 64),
    }

    private enum class SignatureAlgorithm(
        val id: Int,
        val displayName: String,
        val keyAlgorithm: String,
        val jcaNames: List<String>,
        val contentDigestAlgorithm: ContentDigestAlgorithm?,
        private val pssDigestName: String? = null,
        private val pssSaltBytes: Int = 0,
    ) {
        RSA_PSS_SHA256(
            0x0101,
            "RSA-PSS with SHA-256",
            "RSA",
            listOf("SHA256withRSA/PSS", "RSASSA-PSS"),
            ContentDigestAlgorithm.CHUNKED_SHA256,
            "SHA-256",
            32,
        ),
        RSA_PSS_SHA512(
            0x0102,
            "RSA-PSS with SHA-512",
            "RSA",
            listOf("SHA512withRSA/PSS", "RSASSA-PSS"),
            ContentDigestAlgorithm.CHUNKED_SHA512,
            "SHA-512",
            64,
        ),
        RSA_PKCS1_SHA256(
            0x0103,
            "RSA PKCS#1 with SHA-256",
            "RSA",
            listOf("SHA256withRSA"),
            ContentDigestAlgorithm.CHUNKED_SHA256,
        ),
        RSA_PKCS1_SHA512(
            0x0104,
            "RSA PKCS#1 with SHA-512",
            "RSA",
            listOf("SHA512withRSA"),
            ContentDigestAlgorithm.CHUNKED_SHA512,
        ),
        ECDSA_SHA256(
            0x0201,
            "ECDSA with SHA-256",
            "EC",
            listOf("SHA256withECDSA"),
            ContentDigestAlgorithm.CHUNKED_SHA256,
        ),
        ECDSA_SHA512(
            0x0202,
            "ECDSA with SHA-512",
            "EC",
            listOf("SHA512withECDSA"),
            ContentDigestAlgorithm.CHUNKED_SHA512,
        ),
        DSA_SHA256(
            0x0301,
            "DSA with SHA-256",
            "DSA",
            listOf("SHA256withDSA"),
            ContentDigestAlgorithm.CHUNKED_SHA256,
        ),
        VERITY_RSA_SHA256(
            0x0421,
            "verity RSA with SHA-256",
            "RSA",
            listOf("SHA256withRSA"),
            null,
        ),
        VERITY_ECDSA_SHA256(
            0x0423,
            "verity ECDSA with SHA-256",
            "EC",
            listOf("SHA256withECDSA"),
            null,
        ),
        VERITY_DSA_SHA256(
            0x0425,
            "verity DSA with SHA-256",
            "DSA",
            listOf("SHA256withDSA"),
            null,
        );

        val pssParameters: PSSParameterSpec?
            get() = pssDigestName?.let { digestName ->
                val mgfParameters = when (digestName) {
                    "SHA-256" -> MGF1ParameterSpec.SHA256
                    "SHA-512" -> MGF1ParameterSpec.SHA512
                    else -> error("Unsupported PSS digest $digestName")
                }
                PSSParameterSpec(digestName, "MGF1", mgfParameters, pssSaltBytes, 1)
            }

        companion object {
            fun fromId(id: Int): SignatureAlgorithm? = entries.firstOrNull { it.id == id }
        }
    }

    private data class ZipLayout(
        val centralDirectoryOffset: Long,
        val eocdOffset: Long,
        val eocd: ByteArray,
    )

    private data class SigningBlocks(
        val signingBlockOffset: Long,
        val v2: SchemeBlock? = null,
        val v3: SchemeBlock? = null,
        val v31: SchemeBlock? = null,
    )

    private sealed interface SchemeBlock {
        data class Encoded(val bytes: ByteArray) : SchemeBlock

        data class Unavailable(
            val reason: ApkSignatureFailureReason,
            val detail: String,
        ) : SchemeBlock
    }

    private data class SignatureRecord(
        val algorithmId: Int,
        val bytes: ByteArray,
        val algorithm: SignatureAlgorithm?,
    )

    private data class DigestRecord(
        val algorithmId: Int,
        val bytes: ByteArray,
    )

    private data class ParsedCertificate(
        val certificate: X509Certificate,
        val encoded: ByteArray,
    )

    private data class ParsedAdditionalAttributes(
        val lineage: ApkSigningCertificateLineage?,
        val rotationMinSdkVersion: Int?,
    )

    private data class ParsedLineageNode(
        val certificate: ParsedCertificate,
        val capabilityFlags: Int,
        val childSignatureAlgorithmId: Int,
    )

    private data class ParsedSigner(
        val expectedContentDigests: Map<ContentDigestAlgorithm, ByteArray>,
        val certificateEncoding: ByteArray,
        val contentDigestsBySignatureAlgorithm: Map<Int, ByteArray>,
        val lineage: ApkSigningCertificateLineage?,
        val minimumSdkVersion: Int?,
        val rotationMinSdkVersion: Int?,
    )

    private class SchemeVerificationException(
        val reason: ApkSignatureFailureReason,
        message: String? = null,
        val signerCount: Int = 0,
    ) : Exception(message) {
        fun withSigner(index: Int): SchemeVerificationException = SchemeVerificationException(
            reason = reason,
            message = "signer $index${message?.let { ": $it" }.orEmpty()}",
            signerCount = maxOf(signerCount, index),
        )
    }

    private class SchemeUnavailableException(
        val reason: ApkSignatureFailureReason,
        message: String? = null,
    ) : Exception(message)
}
