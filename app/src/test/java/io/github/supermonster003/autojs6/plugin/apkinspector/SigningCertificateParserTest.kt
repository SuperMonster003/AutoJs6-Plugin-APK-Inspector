package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SigningCertificateParserTest {

    @Test
    fun parsesCertificateIdentityValidityAndFingerprint() {
        val certificate = SigningCertificateParser.parse(CERTIFICATE_ONE)

        assertEquals(
            "CN=Roadmap Signer One,OU=APK Inspector,O=AutoJs6,L=Shanghai,ST=Shanghai,C=CN",
            certificate.subject,
        )
        assertEquals(certificate.subject, certificate.issuer)
        assertEquals("2dc3ca60", certificate.serialNumberHex)
        assertEquals(1_735_660_800_000L, certificate.notBeforeMillis)
        assertEquals(2_051_020_800_000L, certificate.notAfterMillis)
        assertEquals(
            "42e1c2d34209501bbc7950cb6103b2ed9806f4a19281e6ddb3e4096679c0e837",
            certificate.sha256Fingerprint,
        )
    }

    @Test
    fun multipleSignersKeepOrderAndMalformedEntriesAreIsolated() {
        val certificates = SigningCertificateParser.parseAll(
            listOf(
                CERTIFICATE_ONE,
                byteArrayOf(0x01, 0x02, 0x03),
                CERTIFICATE_TWO,
                CERTIFICATE_ONE,
            ),
        )

        assertEquals(2, certificates.size)
        assertTrue(certificates[0].subject.startsWith("CN=Roadmap Signer One,"))
        assertEquals(
            "3765475ca9c08d075af380578eadbdfacdae0b2cfdcf75ac789aa9b467606bed",
            certificates[1].sha256Fingerprint,
        )
        assertTrue(certificates[1].subject.startsWith("CN=Roadmap Signer Two,"))
    }

    @Test
    fun emptyAndOversizedBlobsDegradeToNoCertificate() {
        val certificates = SigningCertificateParser.parseAll(
            listOf(
                byteArrayOf(),
                ByteArray(1024 * 1024 + 1),
            ),
        )

        assertTrue(certificates.isEmpty())
    }

    companion object {
        private val CERTIFICATE_ONE = decode(
            """
            MIIDkzCCAnugAwIBAgIELcPKYDANBgkqhkiG9w0BAQsFADB6MQswCQYDVQQGEwJDTjERMA8GA1UECBMIU2hhbmdoYWkxETAPBgNVBAcTCFNoYW5naGFpMRAwDgYDVQQKEwdBdXRvSnM2MRYwFAYDVQQLEw1BUEsgSW5zcGVjdG9yMRswGQYDVQQDExJSb2FkbWFwIFNpZ25lciBPbmUwHhcNMjQxMjMxMTYwMDAwWhcNMzQxMjI5MTYwMDAwWjB6MQswCQYDVQQGEwJDTjERMA8GA1UECBMIU2hhbmdoYWkxETAPBgNVBAcTCFNoYW5naGFpMRAwDgYDVQQKEwdBdXRvSnM2MRYwFAYDVQQLEw1BUEsgSW5zcGVjdG9yMRswGQYDVQQDExJSb2FkbWFwIFNpZ25lciBPbmUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCGigm+M64w4dmy9vdSsaWIFqfn6aCefN1uQkQWkT9Yasz3FbtThflkh+gFvWoG1ORrmmxNnfuUoz9fHiiKLy21w/pZrzPJYEZ3KV67NrdzcH2oobOkaybPWIGMYOOQg74hORufkU7AH1isBYY9nymQ4l6dbVNqm+Cet2/B3SRm+Qx5ndbuAhwJsRlb2oTmzR5w3kLQ0foqzpGh4mrymend5ZRrJ6iiO6+oQbJbOvAPOfiQTmvlTNJMAZNKbCTrhKGaXfK0rjfzPr/oMipuCtz4Wn/7XcDBQ8sGl9UHQ8DvUpX5BXt6Q/fKi65EcS1TBiquCvRrH46aoIWOeabqcEXfAgMBAAGjITAfMB0GA1UdDgQWBBRDn+yXgJ12dVU/swRgIk1F0JGVvDANBgkqhkiG9w0BAQsFAAOCAQEAOSMKTE9og71eRGEmA3h4OdNRdn2MCBoq7V2L74QqdXhT7+tOsyNUmtCMjlvR70Wrn0gOkGvy371cgH0vvlfP2ZrXSKBBoJKbzbl0a1eBKcxO7e0zTYMNG+9YrQ2m+ZZqPbZrmY7JDJ7knkpMeo8SW3A1ls6ZaNDlmDpxy4Oeh6WgMTaU3CkIs02MIVUs9miNs1FU9dxZ5cPIQzeRTYt9DZPodtz9PqbCtZSFOjCufgGDPDN7NykokEFHqLozZysAfJa3EjIcGnorvYFJ6AFSD74TMsWFcnQtKyGgkTICKUb13eVHIu/RIRUBxmr2JoM+HVRAFyDvLO8rty5cjOjhrA==
            """.trimIndent(),
        )

        private val CERTIFICATE_TWO = decode(
            """
            MIICCzCCAa+gAwIBAgIEO1vufzAMBggqhkjOPQQDAgUAMHoxCzAJBgNVBAYTAkNOMREwDwYDVQQIEwhTaGFuZ2hhaTERMA8GA1UEBxMIU2hhbmdoYWkxEDAOBgNVBAoTB0F1dG9KczYxFjAUBgNVBAsTDUFQSyBJbnNwZWN0b3IxGzAZBgNVBAMTElJvYWRtYXAgU2lnbmVyIFR3bzAeFw0yNTAxMDExNjAwMDBaFw0zNDEyMzAxNjAwMDBaMHoxCzAJBgNVBAYTAkNOMREwDwYDVQQIEwhTaGFuZ2hhaTERMA8GA1UEBxMIU2hhbmdoYWkxEDAOBgNVBAoTB0F1dG9KczYxFjAUBgNVBAsTDUFQSyBJbnNwZWN0b3IxGzAZBgNVBAMTElJvYWRtYXAgU2lnbmVyIFR3bzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABHhPsCZr8wTM/fG+rlLnCZRbU0ZDk4QO3VqhrBi0uVARFCLOrq+hn1EeXV140592QASR3YqTrVRLw64bISwY8d+jITAfMB0GA1UdDgQWBBQP0rJ8TR/rwBRhUB5/lpz9UrWLUzAMBggqhkjOPQQDAgUAA0gAMEUCIQDtdkcYP3rlYe+JlnGNWZVZj9hI/oQuMI+2ifFadEaBOQIgHo7t4hb5aTBzVE/rZDOShq9dJjmgiI+4ugpo0E12FzM=
            """.trimIndent(),
        )

        private fun decode(base64: String): ByteArray = Base64.getMimeDecoder().decode(base64)
    }
}
