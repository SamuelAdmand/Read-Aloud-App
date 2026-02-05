package com.samuel.readaloud.domain.player

import android.media.MediaPlayer
import android.util.Log
import com.samuel.readaloud.domain.HighlightRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaPlayerAudioPlayer : AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading = _isLoading.asStateFlow()

    private val _currentHighlight = MutableStateFlow<HighlightRange?>(null)
    override val currentHighlight = _currentHighlight.asStateFlow()

    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    
    private var currentSubtitles: List<SubtitleData> = emptyList()

    override fun play(source: AudioSource, speed: Float) {
        if (source !is AudioSource.LocalFile) return

        stop()
        _isLoading.value = true
        currentSubtitles = source.subtitles

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(source.file.absolutePath)
                prepare()
                
                playbackParams = playbackParams.apply { this.speed = speed }

                if (source.startMillis > 0) {
                    seekTo(source.startMillis.toInt())
                }

                setOnCompletionListener {
                    _isPlaying.value = false
                    stopMonitoring()
                    onCompletionListener?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    onErrorListener?.invoke("MediaPlayer error: $what, $extra")
                    true
                }

                start()
            }
            _isPlaying.value = true
            _isLoading.value = false
            startMonitoring()
        } catch (e: Exception) {
            Log.e("MediaPlayerAudioPlayer", "Playback failed", e)
            _isLoading.value = false
            onErrorListener?.invoke("Failed to start playback: ${e.message}")
        }
    }

    override fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _isPlaying.value = false
                stopMonitoring()
            }
        } catch (e: Exception) {
            Log.w("MediaPlayerAudioPlayer", "Pause failed", e)
        }
    }

    override fun resume(speed: Float) {
        try {
            mediaPlayer?.let { player ->
                player.playbackParams = player.playbackParams.apply { this.speed = speed }
                player.start()
                _isPlaying.value = true
                startMonitoring()
            }
        } catch (e: Exception) {
            Log.e("MediaPlayerAudioPlayer", "Resume failed", e)
        }
    }

    override fun stop() {
        stopMonitoring()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w("MediaPlayerAudioPlayer", "Stop failed", e)
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentHighlight.value = null
    }

    override fun seekTo(millis: Long) {
        try {
            mediaPlayer?.seekTo(millis.toInt())
        } catch (e: Exception) {
            Log.w("MediaPlayerAudioPlayer", "Seek failed", e)
        }
    }

    override fun setSpeed(speed: Float) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.playbackParams = it.playbackParams.apply { this.speed = speed }
                }
            }
        } catch (e: Exception) {
            Log.w("MediaPlayerAudioPlayer", "Set speed failed", e)
        }
    }

    override fun release() {
        stop()
    }

    override fun setOnCompletionListener(listener: () -> Unit) {
        this.onCompletionListener = listener
    }

    override fun setOnErrorListener(listener: (String) -> Unit) {
        this.onErrorListener = listener
    }

    private fun startMonitoring() {
        stopMonitoring()
        monitorJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                val activeSubtitle = currentSubtitles.find {
                    currentPos >= it.startMillis && currentPos < it.endMillis
                }
                if (activeSubtitle != null && _currentHighlight.value != activeSubtitle.globalRange) {
                    _currentHighlight.value = activeSubtitle.globalRange
                }
                delay(50)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
