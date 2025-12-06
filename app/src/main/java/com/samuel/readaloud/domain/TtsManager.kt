package com.samuel.readaloud.domain

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.samuel.readaloud.repository.TtsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var isFetching = false

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

    fun playText(text: String, voice: String) {
        stop() // Reset previous playback

        sourceText = text // Save for editing
        voiceShortName = voice
        _currentTitle.value = text.take(30) + "..." // Simple title for now

        // 1. Split text into chunks of 5 sentences
        chunks = TextChunker.chunkText(text)
        currentChunkIndex = 0

        Log.d("TtsManager", "Text split into ${chunks.size} chunks.")

        if (chunks.isNotEmpty()) {
            processQueue()
        }
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
        // Return if already cached
        if (cachedFiles.containsKey(index)) return cachedFiles[index]

        val text = chunks[index]
        val fileName = "chunk_$index.mp3"
        val outputFile = File(context.cacheDir, fileName)

        // Generate via Python
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

            // Apply current speed
            try {
                playbackParams = playbackParams.setSpeed(currentSpeed)
            } catch (e: Exception) {
                Log.e("TtsManager", "Failed to set speed", e)
            }

            setOnCompletionListener {
                // When finished, move to next chunk
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
                // Re-apply speed just in case
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
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _isLoading.value = false // Reset loading
        cachedFiles.clear()
        currentChunkIndex = 0
    }
}