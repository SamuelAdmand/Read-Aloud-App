package com.samuel.readaloud.domain

import java.text.BreakIterator
import java.util.Locale
import java.util.regex.Pattern

object TextChunker {

    private const val TARGET_CHUNK_SIZE = 600
    private const val MIN_CHUNK_SIZE = 100

    /**
     * Optimized splitting strategy for large articles.
     * CRITICAL: This implementation is LOSSLESS. It preserves all whitespace and newlines
     * to ensure that the offset calculations for highlighting match the original text exactly.
     */
    fun chunkText(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()

        // 1. Split by Paragraphs (Lossless)
        // We find delimiters but ensure we capture them in the list so no characters are lost.
        val paragraphPattern = Pattern.compile("\\n\\s*\\n")
        val matcher = paragraphPattern.matcher(text)

        var currentStart = 0
        val paragraphs = mutableListOf<String>()

        while (matcher.find()) {
            // Content before delimiter
            if (matcher.start() > currentStart) {
                paragraphs.add(text.substring(currentStart, matcher.start()))
            }
            // The delimiter itself (e.g., "\n\n") - Preserving this is key to fixing drift
            paragraphs.add(text.substring(matcher.start(), matcher.end()))
            currentStart = matcher.end()
        }
        // Remaining text
        if (currentStart < text.length) {
            paragraphs.add(text.substring(currentStart))
        }

        // 2. Process Paragraphs
        val currentBuffer = StringBuilder()
        var currentTarget = MIN_CHUNK_SIZE

        for (paragraph in paragraphs) {
            // FIX: Removed .trim(). We must append exactly what is there.

            // If adding this paragraph keeps us under target, or if it's just a delimiter
            if (currentBuffer.length + paragraph.length < currentTarget || paragraph.isBlank()) {
                currentBuffer.append(paragraph)
            } else {
                // Paragraph is substantive content
                if (paragraph.length > TARGET_CHUNK_SIZE) {
                    // Flush existing buffer
                    if (currentBuffer.isNotEmpty()) {
                        chunks.add(currentBuffer.toString())
                        currentBuffer.clear()
                        currentTarget = TARGET_CHUNK_SIZE
                    }

                    // Split huge paragraph (losslessly)
                    val sentenceChunks = splitParagraphBySentences(paragraph, TARGET_CHUNK_SIZE)
                    chunks.addAll(sentenceChunks)
                } else {
                    // Flush buffer
                    if (currentBuffer.isNotEmpty()) {
                        chunks.add(currentBuffer.toString())
                        currentBuffer.clear()
                        currentTarget = TARGET_CHUNK_SIZE
                    }
                    currentBuffer.append(paragraph)
                }
            }
        }

        if (currentBuffer.isNotEmpty()) {
            chunks.add(currentBuffer.toString())
        }

        return chunks
    }

    private fun splitParagraphBySentences(paragraph: String, targetSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        val iterator = BreakIterator.getSentenceInstance(Locale.US)
        iterator.setText(paragraph)

        val sb = StringBuilder()
        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            val sentence = paragraph.substring(start, end)
            sb.append(sentence)

            if (sb.length >= targetSize) {
                chunks.add(sb.toString())
                sb.clear()
            }
            start = end
            end = iterator.next()
        }
        if (sb.isNotEmpty()) {
            chunks.add(sb.toString())
        }
        return chunks
    }

    fun sanitizeMarkdownForTts(text: String): String {
        val sb = StringBuilder(text)
        for (i in sb.indices) {
            val c = sb[i]
            if (c == '#' || c == '*' || c == '_' || c == '`' || c == '>' || c == '[' || c == ']') {
                sb.setCharAt(i, ' ')
            }
        }
        return sb.toString()
    }
}