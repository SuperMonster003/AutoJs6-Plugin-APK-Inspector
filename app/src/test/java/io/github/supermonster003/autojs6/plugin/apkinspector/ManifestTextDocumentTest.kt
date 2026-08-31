package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTextDocumentTest {

    @Test
    fun addsPaddedLineNumbersAndMapsSourceOffsets() {
        val source = (1..12).joinToString("\n") { line -> "<item id=\"$line\"/>" }

        val document = ManifestTextDocument.create(source)

        assertTrue(document.displayText.startsWith(" 1 │ <item id=\"1\"/>"))
        assertTrue(document.displayText.contains("\n12 │ <item id=\"12\"/>"))
        assertEquals(12, document.lineNumberRanges.size)
        val sourceMatch = checkNotNull(ManifestTextSearch.find(source, "id=\"12\"").matches.singleOrNull())
        val displayMatch = document.displayRanges(sourceMatch).single()
        assertEquals("id=\"12\"", document.displayText.substring(displayMatch.start, displayMatch.endExclusive))
        assertEquals(displayMatch.start, document.displayOffset(sourceMatch.start))
    }

    @Test
    fun preservesFinalEmptyLineAfterNewline() {
        val document = ManifestTextDocument.create("<a/>\n")

        assertEquals("1 │ <a/>\n2 │ ", document.displayText)
        assertEquals(2, document.lineNumberRanges.size)
        assertEquals(document.displayText.length, document.displayOffset(document.source.length))
    }

    @Test
    fun highlightsXmlNamesValuesCommentsAndCdata() {
        val source = """
            <?xml version="1.0"?>
            <!-- note -->
            <manifest android:name="Demo"><![CDATA[value]]></manifest>
        """.trimIndent()

        val document = ManifestTextDocument.create(source)
        val tokens = document.syntaxRanges.groupBy { it.kind }.mapValues { (_, ranges) ->
            ranges.map { document.displayText.substring(it.range.start, it.range.endExclusive) }
        }

        assertTrue(tokens.getValue(XmlSyntaxKind.TAG).any { it.contains("<?xml") })
        assertTrue(tokens.getValue(XmlSyntaxKind.ATTRIBUTE).contains("version"))
        assertTrue(tokens.getValue(XmlSyntaxKind.ATTRIBUTE).contains("android:name"))
        assertTrue(tokens.getValue(XmlSyntaxKind.VALUE).contains("\"Demo\""))
        assertTrue(tokens.getValue(XmlSyntaxKind.COMMENT).contains("<!-- note -->"))
        assertTrue(tokens.getValue(XmlSyntaxKind.CDATA).contains("<![CDATA[value]]>"))
    }

    @Test
    fun splitsMultilineSyntaxAroundInsertedLineNumberPrefixes() {
        val document = ManifestTextDocument.create("<!-- first\nsecond -->")
        val comments = document.syntaxRanges.filter { it.kind == XmlSyntaxKind.COMMENT }

        assertEquals(2, comments.size)
        assertEquals("<!-- first\n", document.displayText.substring(
            comments[0].range.start,
            comments[0].range.endExclusive,
        ))
        assertEquals("second -->", document.displayText.substring(
            comments[1].range.start,
            comments[1].range.endExclusive,
        ))
        assertFalse(comments.any { range ->
            document.lineNumberRanges.any { lineNumber ->
                range.range.start < lineNumber.endExclusive && lineNumber.start < range.range.endExclusive
            }
        })
    }

    @Test
    fun suppressesLineNumbersWhenTheLineBoundIsExceeded() {
        val source = "one\ntwo\nthree"

        val document = ManifestTextDocument.create(source, maxNumberedLines = 2)

        assertTrue(document.lineNumbersSuppressed)
        assertEquals(source, document.displayText)
        assertTrue(document.lineNumberRanges.isEmpty())
        assertEquals(4, document.displayOffset(4))
    }

    @Test
    fun syntaxRangeLimitProducesBoundedPartialHighlighting() {
        val document = ManifestTextDocument.create(
            "<a x=\"1\"><b y=\"2\"/></a>",
            maxSyntaxRanges = 3,
        )

        assertTrue(document.syntaxTruncated)
        assertEquals(3, document.syntaxRanges.size)
        assertTrue(document.syntaxRanges.all {
            it.range.start >= 0 && it.range.endExclusive <= document.displayText.length
        })
    }

    @Test
    fun searchIsCaseInsensitiveNonOverlappingAndBounded() {
        val result = ManifestTextSearch.find("Abc abc ABC abc", "abc", maxMatches = 3)

        assertEquals(listOf(0, 4, 8), result.matches.map { it.start })
        assertTrue(result.truncated)
    }

    @Test
    fun emptyAndMissingSearchesReturnNoMatches() {
        assertEquals(emptyList<TextRange>(), ManifestTextSearch.find("manifest", "").matches)
        assertEquals(emptyList<TextRange>(), ManifestTextSearch.find("manifest", "service").matches)
        assertFalse(ManifestTextSearch.find("manifest", "service").truncated)
    }
}
