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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class HighlightRange(val start: Int, val end: Int)

data class Subtitle(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
    val globalRange: HighlightRange
)

data class CachedChunk(
    val audioFile: File,
    val subtitles: List<Subtitle>
)

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
    private var monitorJob: Job? = null

    // Queue State
    private var chunks: List<String> = emptyList()
    private var chunkOffsets: MutableList<Int> = mutableListOf()
    private var currentChunkIndex = 0
    private var voiceShortName: String = "en-US-AriaNeural"

    // Buffering State
    // Use ConcurrentHashMap for thread safety since multiple coroutines access this
    private val cachedChunks = ConcurrentHashMap<Int, CachedChunk>()
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
        stopMonitoring()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _isLoading.value = false
        _currentHighlight.value = null
        cachedChunks.clear()
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
            val chunkData = getOrFetchChunk(currentChunkIndex)
            _isLoading.value = false

            if (chunkData != null) {
                // 2. Trigger Background Buffering for subsequent chunks
                bufferNextChunks()

                playFile(chunkData)
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
            if (!cachedChunks.containsKey(i) && !fetchingIndices.contains(i)) {
                scope.launch(Dispatchers.IO) {
                    getOrFetchChunk(i)
                }
            }
        }
    }

    /**
     * Suspend function that returns the cached chunk data.
     * If request is already in flight (by bufferNextChunks), it waits for it.
     */
    private suspend fun getOrFetchChunk(index: Int): CachedChunk? {
        // Return immediately if available
        if (cachedChunks.containsKey(index)) return cachedChunks[index]

        // Mark as being fetched to prevent duplicate network calls
        fetchingIndices.add(index)

        val text = chunks[index]
        val fileName = "chunk_$index.mp3"
        val outputFile = File(context.cacheDir, fileName)

        // Generate Audio and SRT
        val result = repository.generateAudio(text, voiceShortName, outputFile)

        fetchingIndices.remove(index)

        return if (result.isSuccess) {
            val (audio, srt) = result.getOrNull() ?: return null

            // Parse SRT and map to global text
            val globalOffset = chunkOffsets.getOrElse(index) { 0 }
            val subtitles = parseSrt(srt, text, globalOffset)

            val cachedChunk = CachedChunk(audio, subtitles)
            cachedChunks[index] = cachedChunk
            cachedChunk
        } else {
            null
        }
    }

    private fun parseSrt(srtFile: File, chunkText: String, chunkGlobalOffset: Int): List<Subtitle> {
        val subtitles = mutableListOf<Subtitle>()
        try {
            val content = srtFile.readText()
            // Regex to match: Index -> Time Range -> Text
            val pattern = Pattern.compile(
                "(\\d+)\\s+(\\d{2}:\\d{2}:\\d{2},\\d{3})\\s+-->\\s+(\\d{2}:\\d{2}:\\d{2},\\d{3})\\s+(.*?)(?=\\r?\\n\\r?\\n\\d|\\z)",
                Pattern.DOTALL
            )
            val matcher = pattern.matcher(content)

            var searchIndex = 0

            while (matcher.find()) {
                val startTimeStr = matcher.group(2)
                val endTimeStr = matcher.group(3)
                val rawText = matcher.group(4)?.trim() ?: ""

                // Clean text for searching (normalize spaces)
                val cleanText = rawText.replace(Regex("\\s+"), " ").trim()

                val startMillis = parseTimestamp(startTimeStr)
                val endMillis = parseTimestamp(endTimeStr)

                // Search for this text in the chunk
                var startIndex = chunkText.indexOf(cleanText, searchIndex)

                // Fallback: match first few words if exact match fails
                if (startIndex == -1 && cleanText.isNotEmpty()) {
                    val firstFewWords = cleanText.split(" ").take(3).joinToString(" ")
                    startIndex = chunkText.indexOf(firstFewWords, searchIndex)
                }

                if (startIndex == -1) {
                    startIndex = searchIndex // Force continue
                }

                // Initial end index based on what we found
                var endIndex = (startIndex + cleanText.length).coerceAtMost(chunkText.length)

                // FIX: Greedily include trailing punctuation (.,!?) that might be missing from SRT text
                while (endIndex < chunkText.length) {
                    val nextChar = chunkText[endIndex]
                    if (nextChar == '.' || nextChar == ',' || nextChar == '?' || nextChar == '!' || nextChar == ';' || nextChar == ':') {
                        endIndex++
                    } else {
                        break
                    }
                }

                searchIndex = endIndex

                val globalStart = chunkGlobalOffset + startIndex
                val globalEnd = chunkGlobalOffset + endIndex

                subtitles.add(Subtitle(startMillis, endMillis, cleanText, HighlightRange(globalStart, globalEnd)))
            }

        } catch (e: Exception) {
            Log.e("TtsManager", "Error parsing SRT", e)
        }
        return subtitles
    }

    private fun parseTimestamp(timestamp: String?): Long {
        if (timestamp == null) return 0L
        // Format: HH:mm:ss,SSS
        try {
            val parts = timestamp.replace(",", ".").split(":")
            if (parts.size == 3) {
                val hours = parts[0].toLong()
                val minutes = parts[1].toLong()
                val seconds = parts[2].toDouble()
                return (hours * 3600000 + minutes * 60000 + seconds * 1000).toLong()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }

    private fun playFile(chunkData: CachedChunk) {
        mediaPlayer?.release()
        stopMonitoring()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(chunkData.audioFile.absolutePath)
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
        startMonitoring(chunkData.subtitles)
    }

    private fun startMonitoring(subtitles: List<Subtitle>) {
        monitorJob = scope.launch {
            Log.d("TtsManager", "Started monitoring ${subtitles.size} subtitles.")
            while (isActive && mediaPlayer?.isPlaying == true) {
                val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L

                // Find the subtitle active at this timestamp
                val activeSubtitle = subtitles.find {
                    currentPos >= it.startMillis && currentPos < it.endMillis
                }

                if (activeSubtitle != null) {
                    // Only update if changed to avoid state churn
                    if (_currentHighlight.value != activeSubtitle.globalRange) {
                        _currentHighlight.value = activeSubtitle.globalRange
                        // Log.v("TtsManager", "Highlight: ${activeSubtitle.text}")
                    }
                }

                delay(50) // Check every 50ms
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopMonitoring()
                _isPlaying.value = false
            } else {
                try {
                    it.playbackParams = it.playbackParams.setSpeed(currentSpeed)
                } catch (e: Exception) { }
                it.start()
                _isPlaying.value = true

                // Resume monitoring
                val currentChunk = cachedChunks[currentChunkIndex]
                if (currentChunk != null) {
                    startMonitoring(currentChunk.subtitles)
                }

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