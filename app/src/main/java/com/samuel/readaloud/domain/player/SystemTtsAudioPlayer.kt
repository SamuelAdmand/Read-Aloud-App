package com.samuel.readaloud.domain.player

import com.samuel.readaloud.domain.HighlightRange
import com.samuel.readaloud.domain.SystemTtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemTtsAudioPlayer(private val engine: SystemTtsEngine) : AudioPlayer {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading = _isLoading.asStateFlow()

    private val _currentHighlight = MutableStateFlow<HighlightRange?>(null)
    override val currentHighlight = _currentHighlight.asStateFlow()

    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    
    private var onHighlightMapped: ((Int, Int) -> Unit)? = null

    init {
        engine.setListeners(
            onHighlight = { start, end ->
                onHighlightMapped?.invoke(start, end)
            },
            onComplete = {
                _isPlaying.value = false
                onCompletionListener?.invoke()
            },
            onError = {
                _isPlaying.value = false
                onErrorListener?.invoke("System TTS Error")
            }
        )
    }

    fun setHighlightMapper(mapper: (Int, Int) -> Unit) {
        this.onHighlightMapped = mapper
    }
    
    fun updateHighlight(range: HighlightRange?) {
        _currentHighlight.value = range
    }

    override fun play(source: AudioSource, speed: Float) {
        if (source !is AudioSource.SystemText || source.text.isBlank()) {
            _isPlaying.value = false
            return
        }
        
        _isPlaying.value = true
        _isLoading.value = false
        engine.speak(source.text, source.voice, speed)
    }

    override fun pause() {
        engine.stop()
        _isPlaying.value = false
    }

    override fun resume(speed: Float) {
        // System TTS doesn't support resuming. 
        // TtsManager will call processQueue() if we stay in paused state.
    }

    override fun stop() {
        engine.stop()
        _isPlaying.value = false
        _currentHighlight.value = null
    }

    override fun seekTo(millis: Long) {
        // Not supported directly by System TTS
    }

    override fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
    }

    override fun release() {
        engine.shutdown()
    }

    override fun setOnCompletionListener(listener: () -> Unit) {
        this.onCompletionListener = listener
    }

    override fun setOnErrorListener(listener: (String) -> Unit) {
        this.onErrorListener = listener
    }
}
