package com.samuel.readaloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuel.readaloud.domain.HighlightRange
import java.util.regex.Pattern

data class MarkdownBlock(
    val id: String,
    val text: String,
    val globalStartIndex: Int
)

@Composable
fun MarkdownTextPlayer(
    rawText: String,
    currentHighlight: HighlightRange?,
    modifier: Modifier = Modifier
) {
    // 1. Split massive text into manageable blocks (Paragraphs)
    // This prevents OOM errors by not rendering everything at once.
    val blocks = remember(rawText) {
        splitIntoBlocks(rawText)
    }

    val listState = rememberLazyListState()

    // 2. Auto-scroll to the currently highlighted block
    LaunchedEffect(currentHighlight) {
        currentHighlight?.let { highlight ->
            val index = blocks.indexOfFirst { block ->
                highlight.start >= block.globalStartIndex && highlight.start < (block.globalStartIndex + block.text.length + 2) // +2 for newlines
            }
            if (index != -1) {
                // Only scroll if not already visible to avoid jitter
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val isVisible = visibleItems.any { it.index == index }
                if (!isVisible) {
                    listState.animateScrollToItem(index)
                }
            }
        }
    }

    // 3. Render visible blocks only using LazyColumn
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 80.dp) // Space for bottom sheet
    ) {
        items(blocks, key = { it.id }) { block ->
            MarkdownBlockRenderer(
                block = block,
                currentHighlight = currentHighlight
            )
        }
    }
}

@Composable
private fun MarkdownBlockRenderer(
    block: MarkdownBlock,
    currentHighlight: HighlightRange?
) {
    // Parse Markdown for this specific block only
    val renderResult = remember(block.text) {
        MarkdownParser.parse(block.text)
    }

    // Calculate highlight relative to this block
    val displayedText by remember(renderResult, currentHighlight) {
        derivedStateOf {
            val baseText = renderResult.annotatedString

            if (currentHighlight == null) {
                baseText
            } else {
                // Check if global highlight overlaps with this block
                val blockStart = block.globalStartIndex
                val blockEnd = blockStart + block.text.length

                // Intersection logic
                val highlightStart = currentHighlight.start
                val highlightEnd = currentHighlight.end

                if (highlightEnd > blockStart && highlightStart < blockEnd) {
                    // Calculate local indices
                    val localStart = (highlightStart - blockStart).coerceAtLeast(0)
                    val localEnd = (highlightEnd - blockStart).coerceAtMost(block.text.length)

                    // Map to rendered indices
                    val mappedStart = renderResult.mapRawToRendered(localStart)
                    val mappedEnd = renderResult.mapRawToRendered(localEnd)

                    if (mappedStart != -1 && mappedEnd != -1 && mappedStart < mappedEnd) {
                        val builder = AnnotatedString.Builder(baseText)
                        builder.addStyle(
                            style = SpanStyle(
                                background = Color(0xFFFFF176), // Classic Yellow Highlight
                                color = Color.Black
                            ),
                            start = mappedStart,
                            end = mappedEnd
                        )
                        builder.toAnnotatedString()
                    } else {
                        baseText
                    }
                } else {
                    baseText
                }
            }
        }
    }

    Text(
        text = displayedText,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 18.sp,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp) // Spacing between paragraphs
    )
}

// --- Helpers ---

fun splitIntoBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val matcher = Pattern.compile("\\n\\s*\\n").matcher(text) // Split by blank lines

    var currentStart = 0
    var index = 0

    while (matcher.find()) {
        val content = text.substring(currentStart, matcher.start())
        if (content.isNotBlank()) {
            blocks.add(MarkdownBlock("block_$index", content, currentStart))
            index++
        }
        currentStart = matcher.end()
    }

    if (currentStart < text.length) {
        val content = text.substring(currentStart)
        if (content.isNotBlank()) {
            blocks.add(MarkdownBlock("block_$index", content, currentStart))
        }
    }

    return blocks
}

data class RenderResult(
    val annotatedString: AnnotatedString,
    private val offsetMap: IntArray
) {
    fun mapRawToRendered(rawIndex: Int): Int {
        if (rawIndex < 0) return 0
        if (rawIndex >= offsetMap.size) return offsetMap.lastOrNull() ?: offsetMap.size - 1
        return offsetMap[rawIndex]
    }
}

object MarkdownParser {
    // Same Regex Patterns as before
    private val HEADER = Pattern.compile("(?m)^(#{1,3})[ \\t]+(.*?)$")
    private val BLOCKQUOTE = Pattern.compile("(?m)^>[ \\t]+(.*?)$")
    private val LIST_ITEM = Pattern.compile("(?m)^[ \\t]*([-*+]|\\d+\\.)\\s+(.*)$")
    private val LINK = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]*)\\)")
    private val BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*")
    private val ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)([^*]+)\\*")

    fun parse(rawText: String): RenderResult {
        val builder = AnnotatedString.Builder()
        val map = IntArray(rawText.length + 1) { -1 }
        val styles = mutableListOf<StyleRange>()

        // Block Level Parsing
        matchAndAdd(HEADER, rawText, styles) { m ->
            val hashes = m.group(1) ?: ""
            val contentStart = m.start(2)
            add(StyleRange(m.start(), contentStart, isHidden = true))
            val size = when(hashes.length) {
                1 -> 26.sp; 2 -> 23.sp; else -> 20.sp
            }
            add(StyleRange(contentStart, m.end(), SpanStyle(fontSize = size, fontWeight = FontWeight.Bold)))
        }

        matchAndAdd(BLOCKQUOTE, rawText, styles) { m ->
            val contentStart = m.start(1)
            add(StyleRange(m.start(), contentStart, isHidden = true))
            add(StyleRange(contentStart, m.end(),
                style = SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF555555), background = Color(0xFFF5F5F5)),
                paragraphStyle = ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 16.sp))
            ))
        }

        matchAndAdd(LIST_ITEM, rawText, styles) { m ->
            add(StyleRange(m.start(), m.end(), paragraphStyle = ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 24.sp))))
            add(StyleRange(m.start(1), m.end(1), SpanStyle(fontWeight = FontWeight.Bold)))
        }

        // Inline Parsing
        matchAndAdd(LINK, rawText, styles) { m ->
            val textStart = m.start(1)
            val textEnd = m.end(1)
            add(StyleRange(m.start(), textStart, isHidden = true))
            add(StyleRange(textStart, textEnd, SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline)))
            add(StyleRange(textEnd, m.end(), isHidden = true))
        }

        matchAndAdd(BOLD, rawText, styles) { m ->
            add(StyleRange(m.start(), m.start() + 2, isHidden = true))
            add(StyleRange(m.end() - 2, m.end(), isHidden = true))
            add(StyleRange(m.start() + 2, m.end() - 2, SpanStyle(fontWeight = FontWeight.Bold)))
        }

        matchAndAdd(ITALIC, rawText, styles) { m ->
            add(StyleRange(m.start(), m.start() + 1, isHidden = true))
            add(StyleRange(m.end() - 1, m.end(), isHidden = true))
            add(StyleRange(m.start() + 1, m.end() - 1, SpanStyle(fontStyle = FontStyle.Italic)))
        }

        // Build Output
        var currentRenderIndex = 0
        for (i in rawText.indices) {
            val isHidden = styles.any { it.isHidden && i >= it.start && i < it.end }
            map[i] = currentRenderIndex
            if (!isHidden) {
                builder.append(rawText[i])
                currentRenderIndex++
            }
        }
        map[rawText.length] = currentRenderIndex

        val finalString = builder.toAnnotatedString()
        val styledBuilder = AnnotatedString.Builder(finalString)

        styles.filter { !it.isHidden }.forEach { span ->
            val renderStart = if (span.start < map.size) map[span.start] else currentRenderIndex
            val renderEnd = if (span.end < map.size) map[span.end] else currentRenderIndex
            if (renderStart < renderEnd) {
                if (span.style != null) styledBuilder.addStyle(span.style, renderStart, renderEnd)
                if (span.paragraphStyle != null) styledBuilder.addStyle(span.paragraphStyle, renderStart, renderEnd)
            }
        }

        return RenderResult(styledBuilder.toAnnotatedString(), map)
    }

    private inline fun matchAndAdd(pattern: Pattern, text: String, styles: MutableList<StyleRange>, onMatch: MutableList<StyleRange>.(java.util.regex.Matcher) -> Unit) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) styles.onMatch(matcher)
    }

    private data class StyleRange(val start: Int, val end: Int, val style: SpanStyle? = null, val paragraphStyle: ParagraphStyle? = null, val isHidden: Boolean = false)
}