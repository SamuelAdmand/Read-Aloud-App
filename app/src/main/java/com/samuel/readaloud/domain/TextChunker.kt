package com.samuel.readaloud.domain

import java.text.BreakIterator
import java.util.Locale
import java.util.regex.Pattern

object TextChunker {

    // Start small (~200 chars) for instant playback
    private const val INITIAL_TARGET_SIZE = 200
    // Increase size by this amount after each chunk
    private const val GROWTH_STEP = 200
    // Cap chunk size to avoid timeouts (~1200 chars is safe for most TTS)
    private const val MAX_TARGET_SIZE = 800

    /**
     * Splits text into chunks for TTS using a "Smart Chunking" strategy.
     *
     * 1. **Fast Start**: The first chunk is kept small so audio generation finishes quickly,
     *    minimizing user wait time.
     * 2. **Gradual Growth**: Subsequent chunks increase in size to reduce the total number
     *    of network requests and buffering events.
     * 3. **Sentence Boundaries**: Uses `BreakIterator` to ensure chunks never end abruptly
     *    (e.g., handles "U.S.A." or "Dr." correctly without splitting).
     * 4. **Lossless**: Preserves all original whitespace and characters for accurate highlighting.
     */
    fun chunkText(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val iterator = BreakIterator.getSentenceInstance(Locale.US)
        iterator.setText(text)

        var start = iterator.first()
        var end = iterator.next()

        val currentChunkBuilder = StringBuilder()
        var currentTargetSize = INITIAL_TARGET_SIZE

        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end)
            currentChunkBuilder.append(sentence)

            // If we have enough text for a chunk, finalize it
            // Note: We always finish the current sentence before splitting
            if (currentChunkBuilder.length >= currentTargetSize) {
                chunks.add(currentChunkBuilder.toString())
                currentChunkBuilder.clear()

                // Smart Chunking: Increase target size for the next chunk
                if (currentTargetSize < MAX_TARGET_SIZE) {
                    currentTargetSize = (currentTargetSize + GROWTH_STEP).coerceAtMost(MAX_TARGET_SIZE)
                }
            }

            start = end
            end = iterator.next()
        }

        // Add any remaining text as the final chunk
        if (currentChunkBuilder.isNotEmpty()) {
            chunks.add(currentChunkBuilder.toString())
        }

        return chunks
    }

    /**
     * Removes markdown symbols, embedded URLs, and citation brackets that interfere
     * with TTS pronunciation, replacing them with spaces of identical length to preserve
     * exact character offsets for subtitle highlighting and seek positions.
     */
    fun sanitizeMarkdownForTts(text: String): String {
        val sb = StringBuilder(text)

        // 1. Mask (url) in Markdown links [label](url) with spaces (handles nested parentheses like in Wikipedia URLs)
        var searchFrom = 0
        while (searchFrom < sb.length) {
            val openBracket = sb.indexOf("[", searchFrom)
            if (openBracket == -1) break
            val linkSeparator = sb.indexOf("](", openBracket)
            if (linkSeparator == -1) break

            var parenDepth = 0
            var closeParen = -1
            for (i in (linkSeparator + 1) until sb.length) {
                if (sb[i] == '(') {
                    parenDepth++
                } else if (sb[i] == ')') {
                    parenDepth--
                    if (parenDepth == 0) {
                        closeParen = i
                        break
                    }
                }
            }

            if (closeParen != -1) {
                val label = sb.substring(openBracket + 1, linkSeparator).trim()
                // If label itself is a citation (e.g. [3] or [[3]) or a raw web address, mask entire link
                val isCitationLabel = label.matches(Regex("^\\[?\\s*\\d+\\s*\\]?$")) ||
                    label.matches(Regex("^\\[?(?:edit|citation needed|note\\s*\\d+)\\]?$", RegexOption.IGNORE_CASE))
                val isUrlLabel = label.startsWith("http://", ignoreCase = true) ||
                    label.startsWith("https://", ignoreCase = true) ||
                    label.startsWith("www.", ignoreCase = true) ||
                    label.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))

                if (isCitationLabel || isUrlLabel) {
                    var startMask = openBracket
                    if (startMask > 0 && sb[startMask - 1] == '[') startMask--
                    for (i in startMask..closeParen) {
                        sb.setCharAt(i, ' ')
                    }
                } else {
                    // Only mask the `](url)` part with spaces, leaving the human-readable label intact
                    for (i in (linkSeparator + 1)..closeParen) {
                        sb.setCharAt(i, ' ')
                    }
                }
                searchFrom = closeParen + 1
            } else {
                searchFrom = linkSeparator + 2
            }
        }

        // 2. Mask raw standalone web URLs (https://... or http://... or www....) with spaces
        val rawUrlPattern = Pattern.compile("(?:https?://|www\\.)\\S+")
        val rawUrlMatcher = rawUrlPattern.matcher(sb)
        while (rawUrlMatcher.find()) {
            for (i in rawUrlMatcher.start() until rawUrlMatcher.end()) {
                if (i in sb.indices) {
                    sb.setCharAt(i, ' ')
                }
            }
        }

        // 3. Mask citation brackets like [1], [2], [14], [[3]], [edit] with spaces
        val citePattern = Pattern.compile("\\[+\\s*(\\d+|edit|citation needed|note\\s*\\d+)\\s*\\]+", Pattern.CASE_INSENSITIVE)
        val citeMatcher = citePattern.matcher(sb)
        while (citeMatcher.find()) {
            for (i in citeMatcher.start() until citeMatcher.end()) {
                if (i in sb.indices) {
                    sb.setCharAt(i, ' ')
                }
            }
        }

        // 4. Mask boilerplate preamble lines (datelines, read-times, promo lines) with spaces
        val lines = sb.split("\n")
        var lineStart = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^(?:\\d+\\s*min(?:ute)?s?\\s*read\\b|read\\s*time\\b).*", RegexOption.IGNORE_CASE)) ||
                trimmed.matches(Regex("^(?:First\\s+)?(?:published|updated|last\\s+modified)(?:\\s*on)?\\s*:.*", RegexOption.IGNORE_CASE)) ||
                trimmed.matches(Regex("^(?:also\\s+read|read\\s+also|read\\s+more|must\\s+read|related\\s+story)\\s*:.*", RegexOption.IGNORE_CASE))) {
                for (i in lineStart until (lineStart + line.length)) {
                    if (i in sb.indices) sb.setCharAt(i, ' ')
                }
            }
            lineStart += line.length + 1
        }

        // 5. Replace markdown syntax symbols with spaces
        for (i in sb.indices) {
            val c = sb[i]
            if (c == '#' || c == '*' || c == '_' || c == '`' || c == '>' || c == '[' || c == ']' || c == '~') {
                sb.setCharAt(i, ' ')
            }
        }

        return sb.toString()
    }
}