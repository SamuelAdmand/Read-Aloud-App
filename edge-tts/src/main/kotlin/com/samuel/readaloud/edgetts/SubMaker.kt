package com.samuel.readaloud.edgetts

class SubMaker {
    private val cues = mutableListOf<Subtitle>()
    private var type: String? = null

    fun feed(chunk: TTSChunk) {
        val (text, offset, duration) = when (chunk) {
            is TTSChunk.WordBoundary -> Triple(chunk.text, chunk.offset, chunk.duration)
            is TTSChunk.SentenceBoundary -> Triple(chunk.text, chunk.offset, chunk.duration)
            else -> return
        }

        val chunkType = if (chunk is TTSChunk.WordBoundary) "WordBoundary" else "SentenceBoundary"
        
        if (type == null) {
            type = chunkType
        } else if (type != chunkType) {
            // In Python it raises ValueError, but here we can just ignore or handle it.
            // For now, let's just stick to the first type received.
            return
        }

        // Python uses ticks (100-nanoseconds) / 10 = microseconds.
        // Microseconds / 1000 = milliseconds.
        // So offset / 10000 = milliseconds.
        cues.add(
            Subtitle(
                index = cues.size + 1,
                startMs = offset / 10000,
                endMs = (offset + duration) / 10000,
                content = text
            )
        )
    }

    fun getSrt(): String {
        return SrtComposer.compose(cues)
    }
}
