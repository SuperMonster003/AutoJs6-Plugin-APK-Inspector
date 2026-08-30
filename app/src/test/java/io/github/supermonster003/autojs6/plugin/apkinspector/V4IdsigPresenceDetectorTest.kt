package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileInputStream
import java.io.InputStream

class V4IdsigPresenceDetectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun derivesOnlyBoundedApkSidecarNames() {
        assertEquals(
            "sample.apk.idsig",
            V4IdsigPresenceDetector.candidateName("sample.apk"),
        )
        assertEquals(
            "Sample.APK.idsig",
            V4IdsigPresenceDetector.candidateName("Sample.APK"),
        )
        listOf("sample.apks", "sample.apkm", "sample.xapk", "sample.apkz", "sample.aab").forEach { name ->
            assertNull(V4IdsigPresenceDetector.candidateName(name))
        }
        assertNull(V4IdsigPresenceDetector.candidateName("../sample.apk"))

        val maximumPackageName =
            "a".repeat(PackageRequestPolicy.MAX_DISPLAY_NAME_LENGTH - ".apk".length) + ".apk"
        assertNull(V4IdsigPresenceDetector.candidateName(maximumPackageName))
    }

    @Test
    fun missingCandidateIsReportedAsAbsent() {
        val missing = temporaryFolder.root.resolve("missing.apk.idsig")

        assertFalse(
            V4IdsigPresenceDetector.probePresence {
                FileInputStream(missing)
            },
        )
        assertFalse(V4IdsigPresenceDetector.probePresence { null })
    }

    @Test
    fun existingCandidateIsReportedAsPresent() {
        val originalContent = byteArrayOf(1, 2, 3, 4)
        val existing = temporaryFolder.newFile("sample.apk.idsig").apply {
            writeBytes(originalContent)
        }

        assertTrue(
            V4IdsigPresenceDetector.probePresence {
                FileInputStream(existing)
            },
        )
        assertArrayEquals(originalContent, existing.readBytes())
    }

    @Test
    fun existingCandidateIsClosedWithoutReadingContent() {
        val probe = TrackingInputStream()

        assertTrue(V4IdsigPresenceDetector.probePresence { probe })
        assertTrue(probe.closed)
        assertEquals(0, probe.readCalls)
    }

    private class TrackingInputStream : InputStream() {
        var readCalls = 0
            private set
        var closed = false
            private set

        override fun read(): Int {
            readCalls += 1
            return 0
        }

        override fun close() {
            closed = true
        }
    }
}
