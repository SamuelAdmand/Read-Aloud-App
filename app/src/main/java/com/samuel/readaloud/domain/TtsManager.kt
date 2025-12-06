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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class HighlightRange(val start: Int, val end: Int)

class TtsManager private constructor(
    private val context: Context,
    private val repository: TtsRepository
) {
    companion object {
        @Volatile
        private var instance: TtsManager? = null
        // Maintain a buffer of 3 sentences ahead to prevent network stutter
        private const val BUFFER_SIZE = 3

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

    // Buffering State
    // Use ConcurrentHashMap for thread safety since multiple coroutines access this
    private val cachedFiles = ConcurrentHashMap<Int, File>()
    // Track which chunks are currently being downloaded to avoid duplicate requests
    private val fetchingIndices = Collections.synchronizedSet(mutableSetOf<Int>())

    // UI State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle = _currentTitle.asStateFlow()

    private val _currentHighlight = MutableStateFlow<HighlightRange?>(null)
    val currentHighlight = _currentHighlight.asStateFlow()

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

        chunks = TextChunker.chunkText(text)

        // Calculate offsets
        chunkOffsets.clear()
        var searchIndex = 0
        chunks.forEach { chunk ->
            val index = sourceText.indexOf(chunk, startIndex = searchIndex)
            if (index != -1) {
                chunkOffsets.add(index)
                searchIndex = index + chunk.length
            } else {
                chunkOffsets.add(searchIndex)
                searchIndex += chunk.length
            }
        }

        currentChunkIndex = 0
        Log.d("TtsManager", "Text split into ${chunks.size} chunks.")

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
        fetchingIndices.clear()
        currentChunkIndex = 0
    }

    private fun processQueue() {
        if (currentChunkIndex >= chunks.size) {
            stop()
            return
        }

        scope.launch {
            // 1. Get the IMMEDIATE chunk (Block UI if not ready, for low initial latency)
            _isLoading.value = true
            val file = getOrFetchChunk(currentChunkIndex)
            _isLoading.value = false

            if (file != null) {
                // 2. Trigger Background Buffering for subsequent chunks
                bufferNextChunks()

                updateHighlightForChunk(currentChunkIndex)
                playFile(file)
            } else {
                Log.e("TtsManager", "Failed chunk $currentChunkIndex")
                stop()
            }
        }
    }

    /**
     * Looks ahead and starts downloading future chunks in parallel/background.
     */
    private fun bufferNextChunks() {
        val start = currentChunkIndex + 1
        val end = (currentChunkIndex + 1 + BUFFER_SIZE).coerceAtMost(chunks.size)

        for (i in start until end) {
            // Only fetch if not already cached and not currently being fetched
            if (!cachedFiles.containsKey(i) && !fetchingIndices.contains(i)) {
                scope.launch(Dispatchers.IO) {
                    getOrFetchChunk(i)
                }
            }
        }
    }

    private fun updateHighlightForChunk(index: Int) {
        if (index in chunkOffsets.indices) {
            val start = chunkOffsets[index]
            val chunkLength = chunks[index].length
            val end = start + chunkLength
            _currentHighlight.value = HighlightRange(start, end)
        }
    }

    /**
     * Suspend function that returns the file.
     * If request is already in flight (by bufferNextChunks), it waits for it.
     */
    private suspend fun getOrFetchChunk(index: Int): File? {
        // Return immediately if available
        if (cachedFiles.containsKey(index)) return cachedFiles[index]

        // Mark as being fetched to prevent duplicate network calls
        fetchingIndices.add(index)

        val text = chunks[index]
        val fileName = "chunk_$index.mp3"
        val outputFile = File(context.cacheDir, fileName)

        // Generate Audio
        val result = repository.generateAudio(text, voiceShortName, outputFile)

        fetchingIndices.remove(index)

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
                } catch (e: Exception) { }
                it.start()
                _isPlaying.value = true
                updateHighlightForChunk(currentChunkIndex)
                // Resume buffering if needed
                bufferNextChunks()
            }
        }
    }

    fun stop() {
        resetPlaybackState()
        context.stopService(Intent(context, TtsMediaService::class.java))
    }
}