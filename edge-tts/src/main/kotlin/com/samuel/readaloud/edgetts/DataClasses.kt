package com.samuel.readaloud.edgetts

sealed class TTSChunk {
    data class Audio(val data: ByteArray) : TTSChunk()
    data class WordBoundary(val text: String, val offset: Long, val duration: Long) : TTSChunk()
    data class SentenceBoundary(val text: String, val offset: Long, val duration: Long) : TTSChunk()
}

data class TTSConfig(
    val voice: String = Constants.DEFAULT_VOICE,
    val rate: String = "+0%",
    val volume: String = "+0%",
    val pitch: String = "+0Hz",
    val boundary: String = "SentenceBoundary"
)

data class CommunicateState(
    var partialText: String = "",
    var offsetCompensation: Long = 0,
    var lastDurationOffset: Long = 0,
    var streamWasCalled: Boolean = false
)

data class EdgeVoice(
    val Name: String,
    val ShortName: String,
    val Gender: String,
    val Locale: String,
    val FriendlyName: String? = null
)
