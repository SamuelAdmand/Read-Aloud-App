package com.samuel.readaloud.domain

object TextChunker {

    /**
     * Splits text into chunks to optimize for low-latency TTS.
     *
     * Strategy:
     * 1. **Fast Start**: The first chunk is small (~15 words) to start playback immediately.
     * 2. **Stable Buffer**: Subsequent chunks are larger (~60 words) for efficiency.
     * 3. **Smart Snap**: Breaks occur at natural punctuation boundaries (., !, ?, ;, :) to preserve intonation.
     */
    fun chunkText(text: String): List<String> {
        val chunks = mutableListOf<String>()

        // 1. Split by whitespace to get tokens
        val tokens = text.trim().split(Regex("\\s+"))
        if (tokens.isEmpty() || text.isBlank()) return emptyList()

        var currentChunk = StringBuilder()
        var currentWordCount = 0

        // Dynamic target: Start with 15 words for low latency, then switch to 60
        var targetWordCount = 15
        val stableWordCount = 60

        // Safety limit: If no punctuation is found for a long time, force a break
        val maxWordCount = 100

        for (token in tokens) {
            currentChunk.append(token).append(" ")
            currentWordCount++

            // Check if token ends with a sentence or phrase terminator
            // Matches: . , ! ? ; : " ] )
            val endsWithPunctuation = token.matches(Regex(".*[.!?,;:\")\\]]$"))

            // Decision to break chunk
            val shouldBreak = (currentWordCount >= targetWordCount && endsWithPunctuation) ||
                    (currentWordCount >= maxWordCount)

            if (shouldBreak) {
                chunks.add(currentChunk.toString().trim())

                // Reset for next chunk
                currentChunk.clear()
                currentWordCount = 0

                // Switch to larger chunks after the first one to reduce overhead
                targetWordCount = stableWordCount
            }
        }

        // Add any remaining text
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks
    }
}