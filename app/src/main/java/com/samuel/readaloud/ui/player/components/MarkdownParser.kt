package com.samuel.readaloud.ui.player.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.samuel.readaloud.domain.HighlightRange
import java.util.regex.Pattern

enum class BlockType {
    H1, H2, H3, H4, H5, H6,
    PARAGRAPH,
    BLOCKQUOTE,
    UNORDERED_LIST,
    ORDERED_LIST,
    CODE_BLOCK,
    THEMATIC_BREAK
}

data class MarkdownParsedBlock(
    val id: String,
    val type: BlockType,
    val rawText: String,
    val contentText: String,
    val globalStartIndex: Int,
    val listItems: List<String> = emptyList()
)

data class RenderedBlockResult(
    val annotatedString: AnnotatedString,
    val offsetMap: IntArray
) {
    fun mapRawToRendered(rawIndex: Int): Int {
        if (rawIndex < 0) return 0
        if (rawIndex >= offsetMap.size) return offsetMap.lastOrNull() ?: (offsetMap.size - 1)
        val rendered = offsetMap[rawIndex]
        return if (rendered >= 0) rendered else 0
    }

    fun mapRenderedToRaw(renderedIndex: Int): Int {
        for (i in offsetMap.indices) {
            if (offsetMap[i] == renderedIndex) return i
        }
        return 0
    }
}

object MarkdownParser {

    private val HR_PATTERN = Pattern.compile("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")
    private val HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$")
    private val BLOCKQUOTE_PATTERN = Pattern.compile("^>\\s*(.*)$")
    private val UNORDERED_LIST_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.*)$")
    private val ORDERED_LIST_PATTERN = Pattern.compile("^\\s*(\\d+)[.)]\\s+(.*)$")

    // Inline regexes
    private val INLINE_CODE = Pattern.compile("`([^`]+)`")
    private val BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*|__([^_]+)__")
    private val ITALIC = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)")
    private val STRIKETHROUGH = Pattern.compile("~~([^~]+)~~")
    private val LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)")
    private val CITE_MARKER = Pattern.compile("\\[+\\s*(\\d+|edit|citation needed|note\\s*\\d+)\\s*\\]+", Pattern.CASE_INSENSITIVE)
    private val CITE_LINK_PATTERN = Pattern.compile("\\[+\\s*(\\d+|edit|citation needed|note\\s*\\d+)\\s*\\]+\\(#[^)]*\\)", Pattern.CASE_INSENSITIVE)

    fun splitIntoBlocks(rawText: String): List<MarkdownParsedBlock> {
        if (rawText.isBlank()) return emptyList()

        val blocks = mutableListOf<MarkdownParsedBlock>()
        // Normalize line endings
        val text = rawText.replace("\r\n", "\n").replace("\r", "\n")
        val lines = text.lines()

        var currentGlobalOffset = 0
        var blockIndex = 0
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()

            if (trimmedLine.isEmpty()) {
                currentGlobalOffset += line.length + 1 // +1 for \n
                i++
                continue
            }

            // 1. Code Block (``` ... ```)
            if (trimmedLine.startsWith("```")) {
                val blockStart = currentGlobalOffset
                val codeBuilder = StringBuilder()
                var rawBuilder = StringBuilder(line).append("\n")
                i++
                currentGlobalOffset += line.length + 1

                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuilder.append(lines[i]).append("\n")
                    rawBuilder.append(lines[i]).append("\n")
                    currentGlobalOffset += lines[i].length + 1
                    i++
                }

                if (i < lines.size) {
                    rawBuilder.append(lines[i])
                    currentGlobalOffset += lines[i].length + 1
                    i++
                }

                blocks.add(
                    MarkdownParsedBlock(
                        id = "block_${blockIndex++}",
                        type = BlockType.CODE_BLOCK,
                        rawText = rawBuilder.toString(),
                        contentText = codeBuilder.toString().trimEnd(),
                        globalStartIndex = blockStart
                    )
                )
                continue
            }

            // 2. Horizontal Rule (---, ***, ___)
            if (HR_PATTERN.matcher(trimmedLine).matches()) {
                blocks.add(
                    MarkdownParsedBlock(
                        id = "block_${blockIndex++}",
                        type = BlockType.THEMATIC_BREAK,
                        rawText = line,
                        contentText = "",
                        globalStartIndex = currentGlobalOffset
                    )
                )
                currentGlobalOffset += line.length + 1
                i++
                continue
            }

            // 3. Headings (# to ######)
            val headerMatcher = HEADER_PATTERN.matcher(trimmedLine)
            if (headerMatcher.matches()) {
                val level = headerMatcher.group(1)?.length ?: 1
                val content = headerMatcher.group(2) ?: ""
                val type = when (level) {
                    1 -> BlockType.H1
                    2 -> BlockType.H2
                    3 -> BlockType.H3
                    4 -> BlockType.H4
                    5 -> BlockType.H5
                    else -> BlockType.H6
                }
                blocks.add(
                    MarkdownParsedBlock(
                        id = "block_${blockIndex++}",
                        type = type,
                        rawText = line,
                        contentText = content,
                        globalStartIndex = currentGlobalOffset
                    )
                )
                currentGlobalOffset += line.length + 1
                i++
                continue
            }

            // 4. Blockquote (> ...)
            if (trimmedLine.startsWith(">")) {
                val quoteStart = currentGlobalOffset
                val quoteRaw = StringBuilder()
                val quoteContent = StringBuilder()

                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    val qLine = lines[i]
                    quoteRaw.append(qLine).append("\n")
                    val qMatcher = BLOCKQUOTE_PATTERN.matcher(qLine.trim())
                    val cleanText = if (qMatcher.matches()) qMatcher.group(1) ?: "" else qLine.removePrefix(">").trim()
                    quoteContent.append(cleanText).append(" ")
                    currentGlobalOffset += qLine.length + 1
                    i++
                }

                blocks.add(
                    MarkdownParsedBlock(
                        id = "block_${blockIndex++}",
                        type = BlockType.BLOCKQUOTE,
                        rawText = quoteRaw.toString().trimEnd(),
                        contentText = quoteContent.toString().trim(),
                        globalStartIndex = quoteStart
                    )
                )
                continue
            }

            // 5. Unordered List (- or * or +)
            if (UNORDERED_LIST_PATTERN.matcher(trimmedLine).matches()) {
                val listStart = currentGlobalOffset
                val listRaw = StringBuilder()
                val items = mutableListOf<String>()

                while (i < lines.size && UNORDERED_LIST_PATTERN.matcher(lines[i].trim()).matches()) {
                    val lLine = lines[i]
                    listRaw.append(lLine).append("\n")
                    val m = UNORDERED_LIST_PATTERN.matcher(lLine.trim())
                    if (m.matches()) {
                        items.add(m.group(1) ?: "")
                    }
                    currentGlobalOffset += lLine.length + 1
                    i++
                }

                blocks.add(
                    MarkdownParsedBlock(
                        id = "block_${blockIndex++}",
                        type = BlockType.UNORDERED_LIST,
                        rawText = listRaw.toString().trimEnd(),
                        contentText = items.joinToString("\n"),
                        globalStartIndex = listStart,
                        listItems = items
                    )
                )
                continue
            }

            // 6. Ordered List (1. ...)
            if (ORDERED_LIST_PATTERN.matcher(trimmedLine).matches()) {
                val listStart = currentGlobalOffset
                val listRaw = StringBuilder()
                val items = mutableListOf<String>()

                while (i < lines.size && ORDERED_LIST_PATTERN.matcher(lines[i].trim()).matches()) {
                    val lLine = lines[i]
                    listRaw.append(lLine).append("\n")
                    val m = ORDERED_LIST_PATTERN.matcher(lLine.trim())
                    if (m.matches()) {
                        items.add(m.group(2) ?: "")
                    }
                    currentGlobalOffset += lLine.length + 1
                    i++
                }

                blocks.add(
                    MarkdownParsedBlock(
                        id = "block_${blockIndex++}",
                        type = BlockType.ORDERED_LIST,
                        rawText = listRaw.toString().trimEnd(),
                        contentText = items.joinToString("\n"),
                        globalStartIndex = listStart,
                        listItems = items
                    )
                )
                continue
            }

            // 7. Standard Paragraph (consume contiguous lines until blank line or block syntax)
            val pStart = currentGlobalOffset
            val pRaw = StringBuilder()
            val pContent = StringBuilder()

            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                val pLine = lines[i]
                val pTrim = pLine.trim()

                // If another block starts, break paragraph
                if (pTrim.startsWith("#") || pTrim.startsWith(">") || pTrim.startsWith("```")
                    || UNORDERED_LIST_PATTERN.matcher(pTrim).matches()
                    || ORDERED_LIST_PATTERN.matcher(pTrim).matches()
                    || HR_PATTERN.matcher(pTrim).matches()) {
                    break
                }

                pRaw.append(pLine).append("\n")
                if (pContent.isNotEmpty()) pContent.append(" ")
                pContent.append(pTrim)
                currentGlobalOffset += pLine.length + 1
                i++
            }

            if (pContent.isNotBlank()) {
                blocks.add(
                    MarkdownParsedBlock(
                        id = "block_${blockIndex++}",
                        type = BlockType.PARAGRAPH,
                        rawText = pRaw.toString().trimEnd(),
                        contentText = pContent.toString(),
                        globalStartIndex = pStart
                    )
                )
            }
        }

        return blocks
    }

    /**
     * Parses inline formatting (bold, italic, code, links, strikethrough)
     * and generates an AnnotatedString while recording exact character mappings to the raw text.
     */
    fun parseInlineFormatting(
        rawText: String,
        baseFontSize: TextUnit,
        linkColor: Color,
        codeBackgroundColor: Color,
        codeTextColor: Color
    ): RenderedBlockResult {
        val styles = mutableListOf<InlineStyleRange>()

        // 0. Citations [1], [2], [[3]], [[3]](#citenote...) - hide completely
        matchStyles(CITE_LINK_PATTERN, rawText) { m ->
            styles.add(InlineStyleRange(m.start(), m.end(), isHidden = true))
        }
        matchStyles(CITE_MARKER, rawText) { m ->
            styles.add(InlineStyleRange(m.start(), m.end(), isHidden = true))
        }

        // 1. Links [text](url)
        matchStyles(LINK, rawText) { m ->
            val textStart = m.start(1)
            val textEnd = m.end(1)
            val label = rawText.substring(textStart, textEnd).trim()
            val isCite = label.matches(Regex("^\\[?\\s*\\d+\\s*\\]?$")) ||
                label.matches(Regex("^\\[?(?:edit|citation needed|note\\s*\\d+)\\]?$", RegexOption.IGNORE_CASE))

            if (isCite) {
                styles.add(InlineStyleRange(m.start(), m.end(), isHidden = true))
            } else {
                styles.add(InlineStyleRange(m.start(), textStart, isHidden = true))
                styles.add(
                    InlineStyleRange(
                        start = textStart,
                        end = textEnd,
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                )
                styles.add(InlineStyleRange(textEnd, m.end(), isHidden = true))
            }
        }

        // 2. Bold (**text** or __text__)
        matchStyles(BOLD, rawText) { m ->
            val start = m.start()
            val end = m.end()
            styles.add(InlineStyleRange(start, start + 2, isHidden = true))
            styles.add(InlineStyleRange(end - 2, end, isHidden = true))
            styles.add(
                InlineStyleRange(
                    start = start + 2,
                    end = end - 2,
                    style = SpanStyle(fontWeight = FontWeight.Bold)
                )
            )
        }

        // 3. Italic (*text* or _text_)
        matchStyles(ITALIC, rawText) { m ->
            val start = m.start()
            val end = m.end()
            styles.add(InlineStyleRange(start, start + 1, isHidden = true))
            styles.add(InlineStyleRange(end - 1, end, isHidden = true))
            styles.add(
                InlineStyleRange(
                    start = start + 1,
                    end = end - 1,
                    style = SpanStyle(fontStyle = FontStyle.Italic)
                )
            )
        }

        // 4. Strikethrough (~~text~~)
        matchStyles(STRIKETHROUGH, rawText) { m ->
            val start = m.start()
            val end = m.end()
            styles.add(InlineStyleRange(start, start + 2, isHidden = true))
            styles.add(InlineStyleRange(end - 2, end, isHidden = true))
            styles.add(
                InlineStyleRange(
                    start = start + 2,
                    end = end - 2,
                    style = SpanStyle(textDecoration = TextDecoration.LineThrough)
                )
            )
        }

        // 5. Inline Code (`code`)
        matchStyles(INLINE_CODE, rawText) { m ->
            val start = m.start()
            val end = m.end()
            styles.add(InlineStyleRange(start, start + 1, isHidden = true))
            styles.add(InlineStyleRange(end - 1, end, isHidden = true))
            styles.add(
                InlineStyleRange(
                    start = start + 1,
                    end = end - 1,
                    style = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackgroundColor,
                        color = codeTextColor,
                        fontSize = (baseFontSize.value * 0.9f).sp
                    )
                )
            )
        }

        // Build clean string and offset map
        val offsetMap = IntArray(rawText.length + 1) { -1 }
        val builder = StringBuilder()
        var currentRenderedIndex = 0

        for (i in rawText.indices) {
            val isHidden = styles.any { it.isHidden && i >= it.start && i < it.end }
            offsetMap[i] = currentRenderedIndex
            if (!isHidden) {
                builder.append(rawText[i])
                currentRenderedIndex++
            }
        }
        offsetMap[rawText.length] = currentRenderedIndex

        val finalString = builder.toString()
        val annotatedBuilder = AnnotatedString.Builder(finalString)

        // Apply visible styles onto the rendered string
        styles.filter { !it.isHidden && it.style != null }.forEach { span ->
            val rStart = if (span.start < offsetMap.size) offsetMap[span.start] else currentRenderedIndex
            val rEnd = if (span.end < offsetMap.size) offsetMap[span.end] else currentRenderedIndex
            if (rStart in 0 until rEnd && rEnd <= finalString.length) {
                annotatedBuilder.addStyle(span.style!!, rStart, rEnd)
            }
        }

        return RenderedBlockResult(annotatedBuilder.toAnnotatedString(), offsetMap)
    }

    private inline fun matchStyles(
        pattern: Pattern,
        text: String,
        onMatch: (java.util.regex.Matcher) -> Unit
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            onMatch(matcher)
        }
    }

    private data class InlineStyleRange(
        val start: Int,
        val end: Int,
        val style: SpanStyle? = null,
        val isHidden: Boolean = false
    )
}
