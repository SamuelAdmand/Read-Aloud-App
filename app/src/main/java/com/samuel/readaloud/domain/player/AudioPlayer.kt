package com.samuel.readaloud.domain.player

import com.samuel.readaloud.domain.HighlightRange
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Represents the source for audio playback.
 */
sealed class AudioSource {
    data class LocalFile(
        val file: File,
        val subtitles: List<SubtitleData>,
        val startMillis: Long = 0
    ) : AudioSource()

    data class SystemText(
        val text: String,
        val voice: String
    ) : AudioSource()
}

/**
 * Simplified subtitle data for the player.
 */
data class SubtitleData(
    val startMillis: Long,
    val endMillis: Long,
    val globalRange: HighlightRange
)

/**
 * Common interface for all audio playback engines in the app.
 */
interface AudioPlayer {
    val isPlaying: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val currentHighlight: StateFlow<HighlightRange?>

    fun play(source: AudioSource, speed: Float)
    fun pause()
    fun resume(speed: Float)
    fun stop()
    fun seekTo(millis: Long)
    fun setSpeed(speed: Float)
    fun release()
    
    fun setOnCompletionListener(listener: () -> Unit)
    fun setOnErrorListener(listener: (String) -> Unit)
}
