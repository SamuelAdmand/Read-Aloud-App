package com.samuel.readaloud.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * A streamlined, robust Markdown renderer supporting only essential article features:
 * 1. Headings (H1-H3)
 * 2. Paragraphs & Line Breaks
 * 3. Bold & Italic
 * 4. Links
 * 5. Lists (Ordered & Unordered)
 * 6. Blockquotes
 *
 * Maintains strict index mapping for TTS synchronization.
 */
@Composable
fun MarkdownTextPlayer(
    rawText: String,
    currentHighlight: HighlightRange?,
    modifier: Modifier = Modifier
) {
    // Parse markdown only when text changes
    val renderResult = remember(rawText) {
        MarkdownParser.parse(rawText)
    }

    // Calculate highlight dynamically
    val displayedText by remember(renderResult, currentHighlight) {
        derivedStateOf {
            val baseText = renderResult.annotatedString

            if (currentHighlight == null) {
                baseText
            } else {
                // Map Raw indices (TTS) -> Rendered indices (UI)
                val mappedStart = renderResult.mapRawToRendered(currentHighlight.start)
                val mappedEnd = renderResult.mapRawToRendered(currentHighlight.end)

                if (mappedStart != -1 && mappedEnd != -1 && mappedStart < mappedEnd) {
                    val builder = AnnotatedString.Builder(baseText)
                    builder.addStyle(
                        style = SpanStyle(
                            color = Color(0xFF2196F3), // Highlight Blue
                            fontWeight = FontWeight.Bold,
                            background = Color.Transparent
                        ),
                        start = mappedStart,
                        end = mappedEnd
                    )
                    builder.toAnnotatedString()
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
            lineHeight = 26.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.fillMaxWidth()
    )
}

data class RenderResult(
    val annotatedString: AnnotatedString,
    private val offsetMap: IntArray
) {
    fun mapRawToRendered(rawIndex: Int): Int {
        if (rawIndex <= 0) return 0
        if (rawIndex >= offsetMap.size) return offsetMap.lastOrNull() ?: offsetMap.size - 1
        return offsetMap[rawIndex]
    }
}

object MarkdownParser {

    // --- Regex Patterns ---

    // Headings: # to ### (H1-H3 only)
    private val HEADER = Pattern.compile("(?m)^(#{1,3})[ \\t]+(.*?)$")

    // Blockquote: > text
    private val BLOCKQUOTE = Pattern.compile("(?m)^>[ \\t]+(.*?)$")

    // Lists: Match the WHOLE line to apply ParagraphStyle correctly.
    // Matches: Start -> (Whitespace) -> (Marker: -, *, +, or 1.) -> (Whitespace) -> (Content) -> End
    private val LIST_ITEM = Pattern.compile("(?m)^[ \\t]*([-*+]|\\d+\\.)\\s+(.*)$")

    // Links: [text](url)
    private val LINK = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]*)\\)")

    // Bold: **text**
    private val BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*")

    // Italic: *text* (single asterisk)
    private val ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)([^*]+)\\*")

    fun parse(rawText: String): RenderResult {
        val builder = AnnotatedString.Builder()
        val map = IntArray(rawText.length + 1) { -1 }

        val styles = mutableListOf<StyleRange>()

        // --- 1. Block Level Parsing ---

        // Headers (H1-H3)
        matchAndAdd(HEADER, rawText, styles) { m ->
            val hashes = m.group(1) ?: ""
            val contentStart = m.start(2)

            // Hide hashes
            add(StyleRange(m.start(), contentStart, isHidden = true))

            val size = when(hashes.length) {
                1 -> 26.sp // H1
                2 -> 23.sp // H2
                else -> 20.sp // H3
            }
            // Style the content
            add(StyleRange(contentStart, m.end(),
                SpanStyle(fontSize = size, fontWeight = FontWeight.Bold)
            ))
        }

        // Blockquotes
        matchAndAdd(BLOCKQUOTE, rawText, styles) { m ->
            val contentStart = m.start(1)
            // Hide "> "
            add(StyleRange(m.start(), contentStart, isHidden = true))

            // Style content: Indent + Italic + Background shade
            add(StyleRange(contentStart, m.end(),
                style = SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF555555),
                    background = Color(0xFFF5F5F5)
                ),
                paragraphStyle = ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 16.sp))
            ))
        }

        // Lists (Ordered & Unordered)
        matchAndAdd(LIST_ITEM, rawText, styles) { m ->
            // FIX: Reduced restLine indent to 24.sp to tighten the visual gap
            add(StyleRange(m.start(), m.end(),
                paragraphStyle = ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 24.sp))
            ))

            // Bold the marker (Group 1)
            val markerStart = m.start(1)
            val markerEnd = m.end(1)
            add(StyleRange(markerStart, markerEnd, SpanStyle(fontWeight = FontWeight.Bold)))
        }

        // --- 2. Inline Parsing ---

        // Links
        matchAndAdd(LINK, rawText, styles) { m ->
            val textStart = m.start(1)
            val textEnd = m.end(1)

            // Hide [
            add(StyleRange(m.start(), textStart, isHidden = true))

            // Style Text (Blue + Underline)
            add(StyleRange(textStart, textEnd,
                SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline)
            ))

            // Hide ](url)
            add(StyleRange(textEnd, m.end(), isHidden = true))
        }

        // Bold
        matchAndAdd(BOLD, rawText, styles) { m ->
            add(StyleRange(m.start(), m.start() + 2, isHidden = true)) // Hide **
            add(StyleRange(m.end() - 2, m.end(), isHidden = true))     // Hide **
            add(StyleRange(m.start() + 2, m.end() - 2, SpanStyle(fontWeight = FontWeight.Bold)))
        }

        // Italic
        matchAndAdd(ITALIC, rawText, styles) { m ->
            add(StyleRange(m.start(), m.start() + 1, isHidden = true)) // Hide *
            add(StyleRange(m.end() - 1, m.end(), isHidden = true))     // Hide *
            add(StyleRange(m.start() + 1, m.end() - 1, SpanStyle(fontStyle = FontStyle.Italic)))
        }

        // --- 3. Build Output & Map ---
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
                if (span.style != null) {
                    styledBuilder.addStyle(span.style, renderStart, renderEnd)
                }
                if (span.paragraphStyle != null) {
                    styledBuilder.addStyle(span.paragraphStyle, renderStart, renderEnd)
                }
            }
        }

        return RenderResult(styledBuilder.toAnnotatedString(), map)
    }

    private inline fun matchAndAdd(
        pattern: Pattern,
        text: String,
        styles: MutableList<StyleRange>,
        onMatch: MutableList<StyleRange>.(java.util.regex.Matcher) -> Unit
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            styles.onMatch(matcher)
        }
    }

    private data class StyleRange(
        val start: Int,
        val end: Int,
        val style: SpanStyle? = null,
        val paragraphStyle: ParagraphStyle? = null,
        val isHidden: Boolean = false
    )
}