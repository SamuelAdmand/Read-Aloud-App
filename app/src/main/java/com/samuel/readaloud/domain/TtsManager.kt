package com.samuel.readaloud.domain

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import androidx.core.content.ContextCompat
import com.samuel.readaloud.repository.ContentRepository
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
    private val cachedChunks = ConcurrentHashMap<Int, CachedChunk>()
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

    private var currentSpeed: Float = 1.0f

    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed) ?: android.media.PlaybackParams().setSpeed(speed)
        }
    }

    fun playText(text: String, voice: String, title: String = "") {
        // Sync with repository to ensure PlayerScreen displays this text and title
        ContentRepository.updateContent(text, title)

        resetPlaybackState()
        ContextCompat.startForegroundService(context, Intent(context, TtsMediaService::class.java))

        voiceShortName = voice
        // Use the title stored in the repository
        _currentTitle.value = ContentRepository.getCurrentTitle()

        chunks = TextChunker.chunkText(text)

        // FIX: Calculate offsets by accumulation since TextChunker is now lossless.
        // This prevents "drifting" errors caused by indexOf searching.
        chunkOffsets.clear()
        var runningOffset = 0
        chunks.forEach { chunk ->
            chunkOffsets.add(runningOffset)
            runningOffset += chunk.length
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
            _isLoading.value = true
            val chunkData = getOrFetchChunk(currentChunkIndex)
            _isLoading.value = false

            if (chunkData != null) {
                bufferNextChunks()
                playFile(chunkData)
            } else {
                Log.e("TtsManager", "Failed chunk $currentChunkIndex")
                stop()
            }
        }
    }

    private fun bufferNextChunks() {
        val start = currentChunkIndex + 1
        val end = (currentChunkIndex + 1 + BUFFER_SIZE).coerceAtMost(chunks.size)

        for (i in start until end) {
            if (!cachedChunks.containsKey(i) && !fetchingIndices.contains(i)) {
                scope.launch(Dispatchers.IO) {
                    getOrFetchChunk(i)
                }
            }
        }
    }

    private suspend fun getOrFetchChunk(index: Int): CachedChunk? {
        if (cachedChunks.containsKey(index)) return cachedChunks[index]

        fetchingIndices.add(index)

        val text = chunks[index]
        // FIX: Sanitize text for TTS to avoid reading Markdown symbols (like #, *),
        // but keep length identical to preserve highlighting offsets.
        val ttsText = TextChunker.sanitizeMarkdownForTts(text)

        val fileName = "chunk_$index.mp3"
        val outputFile = File(context.cacheDir, fileName)

        // Generate Audio using the sanitized text
        val result = repository.generateAudio(ttsText, voiceShortName, outputFile)

        fetchingIndices.remove(index)

        return if (result.isSuccess) {
            val (audio, srt) = result.getOrNull() ?: return null

            val globalOffset = chunkOffsets.getOrElse(index) { 0 }
            // Pass the ORIGINAL raw text (with Markdown) to parseSrt so we map back to the source
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

                // Clean SRT text (single spaces)
                val cleanText = rawText.replace(Regex("\\s+"), " ").trim()

                val startMillis = parseTimestamp(startTimeStr)
                val endMillis = parseTimestamp(endTimeStr)

                var startIndex = -1
                var endIndex = -1

                // 1. Try Fuzzy Regex Match (Handles newlines/tabs in chunkText)
                try {
                    // Escape text, then replace spaces with \s+ (one or more whitespace)
                    val escapedText = Pattern.quote(cleanText)
                    val regexPattern = escapedText.replace(" ", "\\E\\s+\\Q")

                    val textMatcher = Pattern.compile(regexPattern).matcher(chunkText)
                    if (textMatcher.find(searchIndex)) {
                        startIndex = textMatcher.start()
                        endIndex = textMatcher.end()
                    }
                } catch (e: Exception) {
                    Log.w("TtsManager", "Regex match failed for '$cleanText'", e)
                }

                // 2. Fallback: Simple Search (if regex fails or text is too simple)
                if (startIndex == -1) {
                    startIndex = chunkText.indexOf(cleanText, searchIndex)
                    if (startIndex != -1) {
                        endIndex = startIndex + cleanText.length
                    }
                }

                // 3. Last Resort Fallback: Match first 3 words
                if (startIndex == -1 && cleanText.isNotEmpty()) {
                    val firstFewWords = cleanText.split(" ").take(3).joinToString(" ")
                    startIndex = chunkText.indexOf(firstFewWords, searchIndex)
                    if (startIndex != -1) {
                        // Estimate end
                        endIndex = (startIndex + cleanText.length).coerceAtMost(chunkText.length)
                    }
                }

                if (startIndex == -1) {
                    // Could not find text. Don't update searchIndex to give next subtitle a chance?
                    // Or advance slightly? Let's keep searchIndex as is.
                    Log.w("TtsManager", "Could not map text: '$cleanText'")
                    continue
                }

                // Greedy Punctuation: Include trailing .,!?:;
                while (endIndex < chunkText.length) {
                    val nextChar = chunkText[endIndex]
                    if (nextChar in listOf('.', ',', '?', '!', ';', ':')) {
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
            while (isActive && mediaPlayer?.isPlaying == true) {
                val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                val activeSubtitle = subtitles.find {
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
                val currentChunk = cachedChunks[currentChunkIndex]
                if (currentChunk != null) {
                    startMonitoring(currentChunk.subtitles)
                }
                bufferNextChunks()
            }
        }
    }

    fun stop() {
        resetPlaybackState()
        context.stopService(Intent(context, TtsMediaService::class.java))
    }
}