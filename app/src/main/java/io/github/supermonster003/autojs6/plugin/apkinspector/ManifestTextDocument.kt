package io.github.supermonster003.autojs6.plugin.apkinspector

internal data class TextRange(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0)
        require(endExclusive >= start)
    }
}

internal enum class XmlSyntaxKind {
    TAG,
    ATTRIBUTE,
    VALUE,
    COMMENT,
    DECLARATION,
    CDATA,
}

internal data class XmlSyntaxRange(
    val range: TextRange,
    val kind: XmlSyntaxKind,
)

internal data class ManifestSearchResult(
    val matches: List<TextRange>,
    val truncated: Boolean,
)

/**
 * Immutable, Android-free presentation model for the manifest viewer.
 *
 * Source offsets remain available for search while display offsets account for inserted line
 * numbers. Expensive parsing and search can therefore stay off the main thread.
 */
internal class ManifestTextDocument private constructor(
    val source: String,
    val displayText: String,
    val syntaxRanges: List<XmlSyntaxRange>,
    val lineNumberRanges: List<TextRange>,
    val lineNumbersSuppressed: Boolean,
    val syntaxTruncated: Boolean,
    private val sourceLineStarts: IntArray,
    private val displayContentStarts: IntArray,
) {

    fun displayOffset(sourceOffset: Int): Int {
        val safeOffset = sourceOffset.coerceIn(0, source.length)
        if (lineNumbersSuppressed) {
            return safeOffset
        }
        val line = lineIndexFor(sourceLineStarts, safeOffset)
        return displayContentStarts[line] + safeOffset - sourceLineStarts[line]
    }

    fun displayRanges(sourceRange: TextRange): List<TextRange> {
        require(sourceRange.endExclusive <= source.length)
        if (sourceRange.start == sourceRange.endExclusive) {
            return emptyList()
        }
        if (lineNumbersSuppressed) {
            return listOf(sourceRange)
        }
        return mapSourceRange(sourceRange, sourceLineStarts, displayContentStarts)
    }

    companion object {
        const val DEFAULT_MAX_NUMBERED_LINES = 100_000
        const val DEFAULT_MAX_SYNTAX_RANGES = 50_000

        fun create(
            source: String,
            maxNumberedLines: Int = DEFAULT_MAX_NUMBERED_LINES,
            maxSyntaxRanges: Int = DEFAULT_MAX_SYNTAX_RANGES,
        ): ManifestTextDocument {
            require(maxNumberedLines >= 0)
            require(maxSyntaxRanges >= 0)

            val lineCount = source.count { it == '\n' } + 1
            val lineNumberWidth = lineCount.toString().length
            val prefixLength = lineNumberWidth + LINE_NUMBER_SEPARATOR.length
            val projectedLength = source.length.toLong() + lineCount.toLong() * prefixLength
            val canNumberLines = lineCount <= maxNumberedLines && projectedLength <= Int.MAX_VALUE
            val display = if (canNumberLines) {
                buildNumberedDisplay(source, lineCount, lineNumberWidth)
            } else {
                DisplayBuild(
                    text = source,
                    lineNumberRanges = emptyList(),
                    sourceLineStarts = intArrayOf(0),
                    displayContentStarts = intArrayOf(0),
                )
            }

            val scanned = XmlSyntaxScanner.scan(source, maxSyntaxRanges)
            val mappedSyntax = ArrayList<XmlSyntaxRange>(minOf(scanned.ranges.size, maxSyntaxRanges))
            var mappedSyntaxTruncated = scanned.truncated
            syntaxLoop@ for (syntaxRange in scanned.ranges) {
                val mapped = if (canNumberLines) {
                    mapSourceRange(
                        syntaxRange.range,
                        display.sourceLineStarts,
                        display.displayContentStarts,
                    )
                } else {
                    listOf(syntaxRange.range)
                }
                for (range in mapped) {
                    if (mappedSyntax.size >= maxSyntaxRanges) {
                        mappedSyntaxTruncated = true
                        break@syntaxLoop
                    }
                    mappedSyntax += XmlSyntaxRange(range, syntaxRange.kind)
                }
            }

            return ManifestTextDocument(
                source = source,
                displayText = display.text,
                syntaxRanges = mappedSyntax,
                lineNumberRanges = display.lineNumberRanges,
                lineNumbersSuppressed = !canNumberLines,
                syntaxTruncated = mappedSyntaxTruncated,
                sourceLineStarts = display.sourceLineStarts,
                displayContentStarts = display.displayContentStarts,
            )
        }

        private fun buildNumberedDisplay(
            source: String,
            lineCount: Int,
            lineNumberWidth: Int,
        ): DisplayBuild {
            val sourceLineStarts = IntArray(lineCount)
            val displayContentStarts = IntArray(lineCount)
            val lineNumberRanges = ArrayList<TextRange>(lineCount)
            val prefixLength = lineNumberWidth + LINE_NUMBER_SEPARATOR.length
            val output = StringBuilder(source.length + lineCount * prefixLength)
            var sourceStart = 0

            repeat(lineCount) { lineIndex ->
                sourceLineStarts[lineIndex] = sourceStart
                val prefixStart = output.length
                output.append((lineIndex + 1).toString().padStart(lineNumberWidth, ' '))
                output.append(LINE_NUMBER_SEPARATOR)
                displayContentStarts[lineIndex] = output.length
                lineNumberRanges += TextRange(prefixStart, output.length)

                val newline = source.indexOf('\n', sourceStart)
                if (newline >= 0) {
                    output.append(source, sourceStart, newline + 1)
                    sourceStart = newline + 1
                } else {
                    output.append(source, sourceStart, source.length)
                    sourceStart = source.length
                }
            }

            return DisplayBuild(
                text = output.toString(),
                lineNumberRanges = lineNumberRanges,
                sourceLineStarts = sourceLineStarts,
                displayContentStarts = displayContentStarts,
            )
        }

        private fun mapSourceRange(
            sourceRange: TextRange,
            sourceLineStarts: IntArray,
            displayContentStarts: IntArray,
        ): List<TextRange> {
            val output = ArrayList<TextRange>(2)
            var sourceOffset = sourceRange.start
            var lineIndex = lineIndexFor(sourceLineStarts, sourceOffset)
            while (sourceOffset < sourceRange.endExclusive) {
                val nextLineStart = sourceLineStarts.getOrNull(lineIndex + 1) ?: Int.MAX_VALUE
                val segmentEnd = minOf(sourceRange.endExclusive, nextLineStart)
                val displayStart = displayContentStarts[lineIndex] +
                    sourceOffset - sourceLineStarts[lineIndex]
                val displayEnd = displayContentStarts[lineIndex] +
                    segmentEnd - sourceLineStarts[lineIndex]
                if (displayEnd > displayStart) {
                    output += TextRange(displayStart, displayEnd)
                }
                sourceOffset = segmentEnd
                lineIndex += 1
            }
            return output
        }

        private fun lineIndexFor(lineStarts: IntArray, offset: Int): Int {
            val result = lineStarts.binarySearch(offset)
            return if (result >= 0) result else (-result - 2).coerceAtLeast(0)
        }

        private const val LINE_NUMBER_SEPARATOR = " │ "
    }

    private data class DisplayBuild(
        val text: String,
        val lineNumberRanges: List<TextRange>,
        val sourceLineStarts: IntArray,
        val displayContentStarts: IntArray,
    )
}

internal object ManifestTextSearch {
    const val DEFAULT_MAX_MATCHES = 10_000

    fun find(
        source: String,
        query: String,
        maxMatches: Int = DEFAULT_MAX_MATCHES,
    ): ManifestSearchResult {
        require(maxMatches >= 0)
        if (query.isEmpty()) {
            return ManifestSearchResult(emptyList(), truncated = false)
        }

        val matches = ArrayList<TextRange>(minOf(64, maxMatches))
        var offset = 0
        while (offset <= source.length - query.length) {
            val match = source.indexOf(query, startIndex = offset, ignoreCase = true)
            if (match < 0) {
                break
            }
            if (matches.size >= maxMatches) {
                return ManifestSearchResult(matches, truncated = true)
            }
            matches += TextRange(match, match + query.length)
            offset = match + query.length
        }
        return ManifestSearchResult(matches, truncated = false)
    }
}

private object XmlSyntaxScanner {

    fun scan(source: String, maxRanges: Int): ScanResult {
        val collector = RangeCollector(maxRanges)
        var cursor = 0
        while (cursor < source.length) {
            val opening = source.indexOf('<', cursor)
            if (opening < 0) {
                break
            }
            val end = when {
                source.startsWith("<!--", opening) -> {
                    val rangeEnd = endAfter(source, "-->", opening + 4)
                    if (!collector.add(opening, rangeEnd, XmlSyntaxKind.COMMENT)) break
                    rangeEnd
                }

                source.startsWith("<![CDATA[", opening) -> {
                    val rangeEnd = endAfter(source, "]]>", opening + 9)
                    if (!collector.add(opening, rangeEnd, XmlSyntaxKind.CDATA)) break
                    rangeEnd
                }

                source.startsWith("<!", opening) -> {
                    val rangeEnd = findMarkupEnd(source, opening)
                    if (!collector.add(opening, rangeEnd, XmlSyntaxKind.DECLARATION)) break
                    rangeEnd
                }

                else -> {
                    val rangeEnd = findMarkupEnd(source, opening)
                    if (!tokenizeMarkup(source, opening, rangeEnd, collector)) break
                    rangeEnd
                }
            }
            cursor = maxOf(opening + 1, end)
        }
        return ScanResult(collector.ranges, collector.truncated)
    }

    private fun tokenizeMarkup(
        source: String,
        start: Int,
        endExclusive: Int,
        collector: RangeCollector,
    ): Boolean {
        val hasClosingBracket = endExclusive > start && source[endExclusive - 1] == '>'
        val terminatorStart = when {
            !hasClosingBracket -> endExclusive
            endExclusive - start >= 2 && source[endExclusive - 2] == '?' -> endExclusive - 2
            else -> endExclusive - 1
        }
        var cursor = start + 1
        if (cursor < terminatorStart && (source[cursor] == '/' || source[cursor] == '?')) {
            cursor += 1
        }
        while (cursor < terminatorStart && source[cursor].isWhitespace()) {
            cursor += 1
        }
        val nameStart = cursor
        while (cursor < terminatorStart && !isMarkupDelimiter(source[cursor])) {
            cursor += 1
        }
        if (!collector.add(start, cursor.coerceAtLeast(start + 1), XmlSyntaxKind.TAG)) {
            return false
        }
        if (cursor == nameStart) {
            cursor = minOf(start + 1, terminatorStart)
        }

        while (cursor < terminatorStart) {
            while (cursor < terminatorStart && source[cursor].isWhitespace()) {
                cursor += 1
            }
            if (cursor >= terminatorStart) {
                break
            }
            if (source[cursor] == '/' || source[cursor] == '?') {
                if (!collector.add(cursor, cursor + 1, XmlSyntaxKind.TAG)) return false
                cursor += 1
                continue
            }

            val attributeStart = cursor
            while (cursor < terminatorStart && !isAttributeDelimiter(source[cursor])) {
                cursor += 1
            }
            if (cursor == attributeStart) {
                if (!collector.add(cursor, cursor + 1, XmlSyntaxKind.TAG)) return false
                cursor += 1
                continue
            }
            if (!collector.add(attributeStart, cursor, XmlSyntaxKind.ATTRIBUTE)) {
                return false
            }
            while (cursor < terminatorStart && source[cursor].isWhitespace()) {
                cursor += 1
            }
            if (cursor >= terminatorStart || source[cursor] != '=') {
                continue
            }
            if (!collector.add(cursor, cursor + 1, XmlSyntaxKind.TAG)) return false
            cursor += 1
            while (cursor < terminatorStart && source[cursor].isWhitespace()) {
                cursor += 1
            }
            if (cursor >= terminatorStart) {
                break
            }

            val valueStart = cursor
            val quote = source[cursor].takeIf { it == '\'' || it == '"' }
            if (quote != null) {
                cursor += 1
                while (cursor < terminatorStart && source[cursor] != quote) {
                    cursor += 1
                }
                if (cursor < terminatorStart) {
                    cursor += 1
                }
            } else {
                while (cursor < terminatorStart && !source[cursor].isWhitespace() &&
                    source[cursor] != '/' && source[cursor] != '?'
                ) {
                    cursor += 1
                }
            }
            if (!collector.add(valueStart, cursor, XmlSyntaxKind.VALUE)) {
                return false
            }
        }

        return !hasClosingBracket || collector.add(terminatorStart, endExclusive, XmlSyntaxKind.TAG)
    }

    private fun findMarkupEnd(source: String, start: Int): Int {
        var quote: Char? = null
        var cursor = start + 1
        while (cursor < source.length) {
            val character = source[cursor]
            if (quote != null) {
                if (character == quote) {
                    quote = null
                }
            } else if (character == '\'' || character == '"') {
                quote = character
            } else if (character == '>') {
                return cursor + 1
            }
            cursor += 1
        }
        return source.length
    }

    private fun endAfter(source: String, terminator: String, start: Int): Int {
        val closing = source.indexOf(terminator, start)
        return if (closing >= 0) closing + terminator.length else source.length
    }

    private fun isMarkupDelimiter(character: Char): Boolean =
        character.isWhitespace() || character == '/' || character == '?' || character == '>'

    private fun isAttributeDelimiter(character: Char): Boolean =
        character.isWhitespace() || character == '=' || character == '/' ||
            character == '?' || character == '>'

    private class RangeCollector(private val maxRanges: Int) {
        val ranges = ArrayList<XmlSyntaxRange>(minOf(256, maxRanges))
        var truncated = false
            private set

        fun add(start: Int, endExclusive: Int, kind: XmlSyntaxKind): Boolean {
            if (endExclusive <= start) {
                return true
            }
            if (ranges.size >= maxRanges) {
                truncated = true
                return false
            }
            ranges += XmlSyntaxRange(TextRange(start, endExclusive), kind)
            return true
        }
    }

    data class ScanResult(
        val ranges: List<XmlSyntaxRange>,
        val truncated: Boolean,
    )
}
