package com.samuel.readaloud.domain

import java.text.BreakIterator
import java.util.Locale
import java.util.regex.Pattern

object TextChunker {

    private const val TARGET_CHUNK_SIZE = 600
    private const val MIN_CHUNK_SIZE = 100 // For the very first chunk (fast start)

    /**
     * Optimized splitting strategy for large articles (e.g., Wikipedia).
     *
     * 1. **Paragraph Split**: First, split by double newlines (`\n\n`). This is fast and prevents
     *    running the heavy BreakIterator on the entire massive string at once.
     * 2. **Refinement**: If a paragraph is small, use it. If it's huge, split it further by sentences.
     * 3. **Batching**: Group small paragraphs together to reduce network requests.
     */
    fun chunkText(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()

        // 1. Split by Paragraphs first (Fast)
        // We use a Regex to find paragraph boundaries
        val paragraphPattern = Pattern.compile("\\n\\s*\\n")
        val matcher = paragraphPattern.matcher(text)

        var currentStart = 0
        val paragraphs = mutableListOf<String>()

        while (matcher.find()) {
            paragraphs.add(text.substring(currentStart, matcher.start()))
            // We include the newlines in the next chunk or handle them implicitly?
            // To keep context flow, we treat the delimiter as a separator.
            currentStart = matcher.end()
        }
        if (currentStart < text.length) {
            paragraphs.add(text.substring(currentStart))
        }

        // 2. Process Paragraphs
        val currentBuffer = StringBuilder()
        var currentTarget = MIN_CHUNK_SIZE // Start small for low latency

        for (paragraph in paragraphs) {
            val cleanedPara = paragraph.trim()
            if (cleanedPara.isEmpty()) continue

            if (currentBuffer.length + cleanedPara.length < currentTarget) {
                // Append to batch
                if (currentBuffer.isNotEmpty()) currentBuffer.append("\n\n")
                currentBuffer.append(cleanedPara)
            } else {
                // If the paragraph ITSELF is huge (larger than target), we must split it by sentence
                if (cleanedPara.length > TARGET_CHUNK_SIZE) {
                    // Flush existing buffer first
                    if (currentBuffer.isNotEmpty()) {
                        chunks.add(currentBuffer.toString())
                        currentBuffer.clear()
                        currentTarget = TARGET_CHUNK_SIZE
                    }

                    // Split this huge paragraph
                    val sentenceChunks = splitParagraphBySentences(cleanedPara, TARGET_CHUNK_SIZE)
                    chunks.addAll(sentenceChunks)
                } else {
                    // Flush buffer and start new one with this paragraph
                    if (currentBuffer.isNotEmpty()) {
                        chunks.add(currentBuffer.toString())
                        currentBuffer.clear()
                        currentTarget = TARGET_CHUNK_SIZE
                    }
                    currentBuffer.append(cleanedPara)
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

    /**
     * Replaces Markdown syntax characters with spaces to prevent TTS from reading them,
     * while preserving the exact string length for accurate highlighting mapping.
     */
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