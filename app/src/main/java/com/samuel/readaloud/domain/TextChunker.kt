package com.samuel.readaloud.domain

import java.text.BreakIterator
import java.util.Locale

object TextChunker {

    /**
     * Splits a long string into chunks, where each chunk contains roughly [batchSize] sentences.
     */
    fun chunkText(text: String, locale: Locale = Locale.US, batchSize: Int = 5): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)

        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()
        var sentenceCount = 0

        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end)

            // Filter out empty or whitespace-only "sentences" that BreakIterator might catch
            if (sentence.isNotBlank()) {
                currentChunk.append(sentence)
                sentenceCount++
            }

            // If we hit the batch size, push to list and reset
            if (sentenceCount >= batchSize) {
                chunks.add(currentChunk.toString())
                currentChunk.clear()
                sentenceCount = 0
            }

            start = end
            end = iterator.next()
        }

        // Add any remaining text
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        return chunks
    }
}