package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal data class SigningCertificateDetails(
    val subject: String,
    val issuer: String,
    val serialNumberHex: String,
    val notBeforeMillis: Long,
    val notAfterMillis: Long,
    val sha256Fingerprint: String,
)

/** Parses the current APK signers returned by Android without trusting certificate display text. */
internal object SigningCertificateParser {

    private const val MAX_SIGNERS = 64
    private const val MAX_CERTIFICATE_BYTES = 1024 * 1024
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    /**
     * Invalid signer blobs are isolated so one malformed entry cannot suppress the remaining report.
     * Duplicate certificate encodings are collapsed while preserving the platform signer order.
     */
    fun parseAll(encodedCertificates: Iterable<ByteArray>): List<SigningCertificateDetails> {
        val uniqueCertificates = linkedMapOf<String, SigningCertificateDetails>()
        encodedCertificates.take(MAX_SIGNERS).forEach { encoded ->
            val details = try {
                parse(encoded)
            } catch (_: Exception) {
                return@forEach
            }
            uniqueCertificates.putIfAbsent(details.sha256Fingerprint, details)
        }
        return uniqueCertificates.values.toList()
    }

    internal fun parse(encodedCertificate: ByteArray): SigningCertificateDetails {
        require(encodedCertificate.size in 1..MAX_CERTIFICATE_BYTES) {
            "Certificate size is outside the inspection limit"
        }
        val certificate = ByteArrayInputStream(encodedCertificate).use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input)
        } as? X509Certificate ?: error("Certificate is not X.509")
        val canonicalEncoding = certificate.encoded
        return SigningCertificateDetails(
            subject = certificate.subjectX500Principal.name,
            issuer = certificate.issuerX500Principal.name,
            serialNumberHex = certificate.serialNumber.toString(16),
            notBeforeMillis = certificate.notBefore.time,
            notAfterMillis = certificate.notAfter.time,
            sha256Fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(canonicalEncoding)
                .toLowerHex(),
        )
    }

    private fun ByteArray.toLowerHex(): String {
        val result = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            result[index * 2] = HEX_DIGITS[value ushr 4]
            result[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
        }
        return result.concatToString()
    }
}
