package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException

class ReleaseHistoryTest {
    @Test fun mapsChineseLocalesAndFallsBackToEnglish() {
        assertEquals("doc/CHANGELOG-zh-Hant-HK.md", releaseHistoryCandidates("zh", "HK", "").first())
        assertEquals("doc/CHANGELOG-zh-Hant-TW.md", releaseHistoryCandidates("zh", "TW", "").first())
        assertEquals("doc/CHANGELOG-zh-Hant-TW.md", releaseHistoryCandidates("zh", "", "Hant").first())
        assertEquals("doc/CHANGELOG-zh-Hans.md", releaseHistoryCandidates("zh", "CN", "Hans").first())
        assertEquals(listOf("doc/CHANGELOG-en.md"), releaseHistoryCandidates("de", "DE", ""))
    }
    @Test fun skipsMissingOrBlankLocalizedAssetsAndReportsCompleteFailure() {
        val candidates = releaseHistoryCandidates("fr", "FR", "")
        assertEquals("English history", loadReleaseHistory(candidates) {
            if (it.endsWith("-en.md")) "English history" else throw FileNotFoundException()
        })
        assertNull(loadReleaseHistory(candidates) { " " })
    }
}
