package com.samuel.readaloud.domain

import java.text.BreakIterator
import java.util.Locale

object TextChunker {

    /**
     * Splits text strictly into sentences for precise highlighting.
     * Uses Java's BreakIterator for robust locale-aware sentence boundary detection.
     */
    fun chunkText(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        iterator.setText(text)

        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) {
                chunks.add(sentence)
            }
            start = end
            end = iterator.next()
        }

        return chunks
    }
}