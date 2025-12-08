package com.samuel.readaloud.domain

import java.text.BreakIterator
import java.util.Locale

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
     * Removes markdown symbols that might interfere with TTS pronunciation,
     * replacing them with spaces to preserve character count/offsets if needed.
     */
    fun sanitizeMarkdownForTts(text: String): String {
        val sb = StringBuilder(text)
        for (i in sb.indices) {
            val c = sb[i]
            // Replace common markdown syntax chars with space so TTS reads the text naturally
            if (c == '#' || c == '*' || c == '_' || c == '`' || c == '>' || c == '[' || c == ']') {
                sb.setCharAt(i, ' ')
            }
        }
        return sb.toString()
    }
}