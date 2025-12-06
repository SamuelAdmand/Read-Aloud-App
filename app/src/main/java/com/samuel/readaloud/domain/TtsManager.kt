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
 * Represents a range in the global text to highlight.
 */
data class HighlightRange(val start: Int, val end: Int)

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
    private var chunkOffsets: MutableList<Int> = mutableListOf()
    private var currentChunkIndex = 0
    private var voiceShortName: String = "en-US-AriaNeural"

    // Buffering State: Maps Index -> AudioFile
    private val cachedFiles = mutableMapOf<Int, File>()

    // UI State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle = _currentTitle.asStateFlow()

    // Highlight State (Global indices)
    private val _currentHighlight = MutableStateFlow<HighlightRange?>(null)
    val currentHighlight = _currentHighlight.asStateFlow()

    // Text Source
    var sourceText: String = ""
        private set

    private var currentSpeed: Float = 1.0f

    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed) ?: android.media.PlaybackParams().setSpeed(speed)
        }
    }

    fun importText(text: String, title: String = "") {
        sourceText = text
        if (title.isNotEmpty()) {
            _currentTitle.value = title
        } else {
            _currentTitle.value = text.take(30) + "..."
        }
    }

    fun playText(text: String, voice: String) {
        resetPlaybackState()
        ContextCompat.startForegroundService(context, Intent(context, TtsMediaService::class.java))

        sourceText = text
        voiceShortName = voice
        _currentTitle.value = text.take(30) + "..."

        // 1. Chunk the raw text directly
        chunks = TextChunker.chunkText(text)

        // 2. Calculate global offsets Robustly
        chunkOffsets.clear()
        var searchIndex = 0
        chunks.forEach { chunk ->
            // Try to find the chunk starting from where the last one ended.
            val index = sourceText.indexOf(chunk, startIndex = searchIndex)

            if (index != -1) {
                chunkOffsets.add(index)
                searchIndex = index + chunk.length
            } else {
                // Fallback: Just assume it follows the previous one if exact match fails
                chunkOffsets.add(searchIndex)
                searchIndex += chunk.length
            }
        }

        currentChunkIndex = 0
        Log.d("TtsManager", "Text split into ${chunks.size} chunks. Offsets: $chunkOffsets")

        if (chunks.isNotEmpty()) {
            processQueue()
        }
    }


    private fun resetPlaybackState() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _isLoading.value = false
        _currentHighlight.value = null
        cachedFiles.clear()
        currentChunkIndex = 0
    }

    private fun processQueue() {
        if (currentChunkIndex >= chunks.size) {
            stop()
            return
        }

        scope.launch {
            _isLoading.value = true
            val file = getOrFetchChunk(currentChunkIndex)
            _isLoading.value = false

            if (file != null) {
                // Update Highlight IMMEDIATELY when we prepare to play this chunk
                updateHighlightForChunk(currentChunkIndex)

                playFile(file)

                // Pre-fetch next
                if (currentChunkIndex + 1 < chunks.size) {
                    launch(Dispatchers.IO) {
                        getOrFetchChunk(currentChunkIndex + 1)
                    }
                }
            } else {
                Log.e("TtsManager", "Failed chunk $currentChunkIndex")
                stop()
            }
        }
    }

    private fun updateHighlightForChunk(index: Int) {
        if (index in chunkOffsets.indices) {
            val start = chunkOffsets[index]
            // The highlight ends where the chunk text ends
            val chunkLength = chunks[index].length
            val end = start + chunkLength

            _currentHighlight.value = HighlightRange(start, end)
        }
    }

    private suspend fun getOrFetchChunk(index: Int): File? {
        if (cachedFiles.containsKey(index)) return cachedFiles[index]

        val text = chunks[index]
        val fileName = "chunk_$index.mp3"
        val outputFile = File(context.cacheDir, fileName)

        // Note: TtsRepository must be updated to return Result<File> as requested in previous steps
        val result = repository.generateAudio(text, voiceShortName, outputFile)

        return if (result.isSuccess) {
            val file = result.getOrNull()
            if (file != null) {
                cachedFiles[index] = file
                file
            } else null
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

                // Ensure highlight is set when resuming
                updateHighlightForChunk(currentChunkIndex)
            }
        }
    }

    fun stop() {
        resetPlaybackState()
        context.stopService(Intent(context, TtsMediaService::class.java))
    }
}