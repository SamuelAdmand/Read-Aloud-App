package com.samuel.readaloud.domain

import java.text.BreakIterator
import java.util.Locale

object TextChunker {

    /**
     * Splits text into chunks using a Dynamic Buffering strategy.
     *
     * 1. **Chunk 0 (Fast Start)**: Capped at ~100 characters. Ensures playback starts instantly.
     * 2. **Chunk 1+ (Batching)**: Accumulates text up to ~500 characters.
     *    - This drastically reduces network requests.
     *    - It heals "False Splits" (like "U.S.") by sending them together in one request to the TTS engine.
     */
    fun chunkText(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()
        val iterator = BreakIterator.getSentenceInstance(Locale.US)
        iterator.setText(text)

        val currentBuffer = StringBuilder()

        // Initial limit is small for low latency.
        // Subsequent limits are large for efficiency and context preservation.
        var currentTargetLength = 100
        val batchTargetLength = 600

        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            // FIX: Do not trim() or modify the text. Keep exact whitespace/newlines.
            val sentence = text.substring(start, end)

            currentBuffer.append(sentence)

            // Check if buffer has reached the target size
            if (currentBuffer.length >= currentTargetLength) {
                chunks.add(currentBuffer.toString())
                currentBuffer.clear()

                // Switch to larger batching for the rest of the text
                currentTargetLength = batchTargetLength
            }

            start = end
            end = iterator.next()
        }

        // Add any remaining text
        if (currentBuffer.isNotEmpty()) {
            chunks.add(currentBuffer.toString())
        }

        return chunks
    }

    /**
     * Replaces Markdown syntax characters with spaces to prevent TTS from reading them,
     * while preserving the exact string length for accurate highlighting mapping.
     */
    fun sanitizeMarkdownForTts(text: String): String {
        val sb = StringBuilder(text)
        for (i in sb.indices) {
            val c = sb[i]
            // Replace common Markdown markers with space: #, *, _, `, >, [, ]
            // We keep ( and ) for now as they are often used in normal text too.
            if (c == '#' || c == '*' || c == '_' || c == '`' || c == '>' || c == '[' || c == ']') {
                sb.setCharAt(i, ' ')
            }
        }
        return sb.toString()
    }
}