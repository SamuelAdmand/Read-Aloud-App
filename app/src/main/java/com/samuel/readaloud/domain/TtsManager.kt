package com.samuel.readaloud.domain

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import androidx.core.content.ContextCompat
import com.samuel.readaloud.repository.TtsRepository
import com.samuel.readaloud.service.TtsMediaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages the TTS playback queue, buffering logic, and media player.
 * Should be a Singleton (in a real app, injected via Hilt/Koin).
 */
class TtsManager private constructor(
    private val context: Context,
    private val repository: TtsRepository
) {
    companion object {
        @Volatile
        private var instance: TtsManager? = null

        fun getInstance(context: Context): TtsManager {
            return instance ?: synchronized(this) {
                instance ?: TtsManager(
                    context.applicationContext,
                    TtsRepository()
                ).also { instance = it }
            }
        }
    }
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaPlayer: MediaPlayer? = null

    // Queue State
    private var chunks: List<String> = emptyList()
    private var currentChunkIndex = 0
    private var voiceShortName: String = "en-US-AriaNeural" // Default

    // Buffering State
    private val cachedFiles = mutableMapOf<Int, File>()

    // UI State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _currentTitle = MutableStateFlow("")
    val currentTitle = _currentTitle.asStateFlow()

    // Text Source for Editing
    var sourceText: String = ""
        private set

    // Speed State
    private var currentSpeed: Float = 1.0f

    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed) ?: android.media.PlaybackParams().setSpeed(speed)
        }
    }

    /**
     * Updates the source text and title without starting playback.
     * Useful for importing text from external sources (files, links).
     */
    fun importText(text: String, title: String = "") {
        sourceText = text
        if (title.isNotEmpty()) {
            _currentTitle.value = title
        } else {
            _currentTitle.value = text.take(30) + "..."
        }
    }

    fun playText(text: String, voice: String) {
        // 1. Reset playback state internally WITHOUT stopping the service
        resetPlaybackState()

        // 2. Start Foreground Service
        // It is safe to call this even if the service is already running.
        ContextCompat.startForegroundService(context, Intent(context, TtsMediaService::class.java))

        sourceText = text
        voiceShortName = voice
        _currentTitle.value = text.take(30) + "..."

        // 3. Split text and start processing
        chunks = TextChunker.chunkText(text)
        currentChunkIndex = 0

        Log.d("TtsManager", "Text split into ${chunks.size} chunks.")

        if (chunks.isNotEmpty()) {
            processQueue()
        }
    }

    /**
     * Helper to reset player and queue without killing the service.
     */
    private fun resetPlaybackState() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _isLoading.value = false
        cachedFiles.clear()
        currentChunkIndex = 0
    }

    private fun processQueue() {
        if (currentChunkIndex >= chunks.size) {
            stop()
            return
        }

        scope.launch {
            // Step A: Ensure current chunk is ready
            _isLoading.value = true
            val currentFile = getOrFetchChunk(currentChunkIndex)
            _isLoading.value = false

            if (currentFile != null) {
                playFile(currentFile)

                // Step B: Pre-fetch next chunk in background while current plays
                if (currentChunkIndex + 1 < chunks.size) {
                    launch(Dispatchers.IO) {
                        getOrFetchChunk(currentChunkIndex + 1)
                    }
                }
            } else {
                Log.e("TtsManager", "Failed to generate audio for chunk $currentChunkIndex")
                stop()
            }
        }
    }

    private suspend fun getOrFetchChunk(index: Int): File? {
        if (cachedFiles.containsKey(index)) return cachedFiles[index]

        val text = chunks[index]
        val fileName = "chunk_$index.mp3"
        val outputFile = File(context.cacheDir, fileName)

        val result = repository.generateAudio(text, voiceShortName, outputFile)

        return if (result.isSuccess) {
            cachedFiles[index] = outputFile
            outputFile
        } else {
            null
        }
    }

    private fun playFile(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()

            try {
                playbackParams = playbackParams.setSpeed(currentSpeed)
            } catch (e: Exception) {
                Log.e("TtsManager", "Failed to set speed", e)
            }

            setOnCompletionListener {
                currentChunkIndex++
                processQueue()
            }

            start()
        }
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            } else {
                try {
                    it.playbackParams = it.playbackParams.setSpeed(currentSpeed)
                } catch (e: Exception) {
                    Log.e("TtsManager", "Failed to set speed on resume", e)
                }
                it.start()
                _isPlaying.value = true
            }
        }
    }

    fun stop() {
        // Reset state AND stop the service
        resetPlaybackState()
        context.stopService(Intent(context, TtsMediaService::class.java))
    }
}