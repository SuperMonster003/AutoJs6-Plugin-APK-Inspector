package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

class ReleaseHistoryTest {
    private fun first(tag: String) = ReleaseHistoryAssetPolicy.candidatesFor(Locale.forLanguageTag(tag)).first()

    @Test fun mapsChineseLocalesAndFallsBackToEnglish() {
        assertEquals("doc/CHANGELOG-zh-Hant-HK.md", first("zh-HK"))
        assertEquals("doc/CHANGELOG-zh-Hant-HK.md", first("zh-Hant-MO"))
        assertEquals("doc/CHANGELOG-zh-Hant-TW.md", first("zh-TW"))
        assertEquals("doc/CHANGELOG-zh-Hant-TW.md", first("zh-Hant"))
        assertEquals("doc/CHANGELOG-zh-Hans.md", first("zh-Hans-CN"))
        assertEquals("doc/CHANGELOG-zh-Hans.md", first("zh-Hans-HK"))
        assertEquals("doc/CHANGELOG-zh-Hans.md", first("zh"))
        assertEquals(listOf("doc/CHANGELOG-ar.md", "doc/CHANGELOG-en.md"), ReleaseHistoryAssetPolicy.candidatesFor(Locale.forLanguageTag("ar-EG")))
        assertEquals(listOf("doc/CHANGELOG-en.md"), ReleaseHistoryAssetPolicy.candidatesFor(Locale.forLanguageTag("de-DE")))
        assertEquals(listOf("doc/CHANGELOG-en.md"), ReleaseHistoryAssetPolicy.candidatesFor(Locale.ENGLISH))
    }

    /** A missing, blank or unparsable localized changelog falls back to English; nothing usable gives the empty state. */
    @Test fun skipsMissingBlankOrUnparsableLocalizedAssetsAndReportsCompleteFailure() {
        val candidates = ReleaseHistoryAssetPolicy.candidatesFor(Locale.FRANCE)
        val english = "## v1.0.0\n\n###### 2026/10/04\n\n* `Feature` English note\n"
        fun load(french: String?) = loadReleaseHistory(candidates) { path ->
            when {
                path.endsWith("-en.md") -> english
                french == null -> throw FileNotFoundException(path)
                else -> french
            }
        }
        assertEquals("English note", load(null).single().items.single().text)
        assertEquals("English note", load(" ").single().items.single().text)
        assertEquals("English note", load("### Historique\n").single().items.single().text)
        assertEquals("Note", load("## v1.0.0\n\n* `Fonctionnalité` Note\n").single().items.single().text)
        assertTrue(loadReleaseHistory(candidates) { " " }.isEmpty())
        assertTrue(loadReleaseHistory(candidates) { throw FileNotFoundException(it) }.isEmpty())
    }

    /**
     * Every bundled changelog the page can open parses into one entry per version heading, each with a date and
     * tagged items, so a generator change that breaks the dialect fails here instead of showing an empty page.
     */
    @Test fun parsesEveryBundledChangelog() {
        val docs = File("src/main/assets/doc")
        val files = docs.listFiles { file -> file.name.startsWith("CHANGELOG") && file.name.endsWith(".md") }.orEmpty()
        assertTrue("no bundled changelogs under ${docs.absolutePath}", files.size >= 10)
        val heading = Regex("^#{1,2}\\s+v\\S+\\s*$")
        for (file in files) {
            val markdown = file.readText()
            val entries = ReleaseHistoryParser.parse(markdown)
            val headings = markdown.lines().count { heading.matches(it.trim()) }
            assertTrue("${file.name} has no versions", entries.isNotEmpty())
            assertEquals("${file.name} versions", headings, entries.size)
            for (entry in entries) {
                assertNotNull("${file.name} ${entry.version} date", entry.date)
                assertTrue("${file.name} ${entry.version} items", entry.items.isNotEmpty())
                assertTrue("${file.name} ${entry.version} untagged", entry.items.all { it.tag != null })
            }
        }
        val policyAssets = listOf("zh-Hans", "zh-HK", "zh-TW", "en", "ja", "ko", "fr", "es", "ru", "ar")
            .map { ReleaseHistoryAssetPolicy.candidatesFor(Locale.forLanguageTag(it)).first().removePrefix("doc/") }
        for (asset in policyAssets) {
            assertTrue("missing $asset", File(docs, asset).isFile)
        }
    }
}

class ReleaseHistoryParserTest {
    @Test fun parsesTheGeneratedChangelogDialect() {
        val markdown = """
            ******

            ### Release History

            ******

            ## v2.1.0

            ###### 2026/10/13

            * `Hint` First note
            * `Improvement` Second note

            ## v2.0.0

            ###### 2026/10/04

            * `Feature` Third note
            * Untagged note
        """.trimIndent()
        val entries = ReleaseHistoryParser.parse(markdown)
        assertEquals(listOf("v2.1.0", "v2.0.0"), entries.map { it.version })
        assertEquals("2026/10/13", entries[0].date)
        assertEquals(listOf("Hint" to "First note", "Improvement" to "Second note"), entries[0].items.map { it.tag to it.text })
        assertEquals(listOf("Feature" to "Third note", null to "Untagged note"), entries[1].items.map { it.tag to it.text })
    }

    @Test fun parsesTheSingleHashHeadingToo() {
        val entries = ReleaseHistoryParser.parse("# v1.0.0\n\n* `Feature` Note\n")
        assertEquals("v1.0.0", entries.single().version)
        assertEquals(1, entries.single().items.size)
    }

    /** The page names each category once and lists its items beneath; the collapsed summary counts them. */
    @Test fun groupsItemsByCategoryInTheOrderTheyFirstAppear() {
        val entry = ReleaseHistoryParser.parse(
            "## v1.2.0\n\n* `Hint` A\n* `Feature` B\n* `Feature` C\n* `Fix` D\n* `Feature` E\n* Untagged F\n",
        ).single()
        assertEquals(
            listOf("Hint" to listOf("A"), "Feature" to listOf("B", "C", "E"), "Fix" to listOf("D"), null to listOf("Untagged F")),
            entry.groups.map { it.tag to it.items },
        )
        assertEquals(listOf("Hint" to 1, "Feature" to 3, "Fix" to 1), entry.categoryCounts)
    }

    @Test fun ignoresSectionTitlesThatAreNotVersions() {
        assertTrue(ReleaseHistoryParser.parse("### Release History\n\n* `Feature` Orphan\n").isEmpty())
    }
}
