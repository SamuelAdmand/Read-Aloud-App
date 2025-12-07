package com.samuel.readaloud.domain

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import androidx.core.content.ContextCompat
import com.samuel.readaloud.repository.ContentRepository
import com.samuel.readaloud.repository.LibraryRepository
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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.samuel.readaloud.worker.DownloadWorker

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
    private val libraryRepository = LibraryRepository(context)
    private var currentArticleId: Long = -1L
    companion object {
        @Volatile
        private var instance: TtsManager? = null
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
    private val _isSaved = MutableStateFlow(false)
    val isSaved = _isSaved.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle = _currentTitle.asStateFlow()

    private val _currentHighlight = MutableStateFlow<HighlightRange?>(null)
    val currentHighlight = _currentHighlight.asStateFlow()

    private var currentSpeed: Float = 1.0f
    private var pendingSeekGlobalIndex: Int? = null
    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed) ?: android.media.PlaybackParams().setSpeed(speed)
        }
    }
    fun seekToLocation(globalIndex: Int) {
        if (chunks.isEmpty()) return

        // 1. Find which chunk contains this index
        val targetChunkIndex = chunkOffsets.indexOfLast { it <= globalIndex }
        if (targetChunkIndex == -1) return

        // 2. If it's the current chunk and playing, just seek
        if (targetChunkIndex == currentChunkIndex && mediaPlayer != null) {
            val chunk = cachedChunks[currentChunkIndex] ?: return
            val subtitle = chunk.subtitles.find {
                globalIndex >= it.globalRange.start && globalIndex < it.globalRange.end
            } ?: chunk.subtitles.minByOrNull { kotlin.math.abs(it.globalRange.start - globalIndex) }

            subtitle?.let {
                mediaPlayer?.seekTo(it.startMillis.toInt())
            }
            return
        }

        // 3. If it's a different chunk, switch and set pending seek
        stopMonitoring()
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }

        currentChunkIndex = targetChunkIndex
        pendingSeekGlobalIndex = globalIndex
        processQueue()
    }
    fun skipNext() {
        val player = mediaPlayer ?: return
        val currentChunk = cachedChunks[currentChunkIndex] ?: return
        val currentPos = player.currentPosition.toLong()

        // Find current subtitle index
        val currentIndex = currentChunk.subtitles.indexOfFirst {
            currentPos >= it.startMillis && currentPos < it.endMillis
        }

        if (currentIndex != -1 && currentIndex < currentChunk.subtitles.size - 1) {
            // Seek to next subtitle in current chunk
            val nextSubtitle = currentChunk.subtitles[currentIndex + 1]
            player.seekTo(nextSubtitle.startMillis.toInt())
        } else {
            // Move to next chunk
            if (currentChunkIndex < chunks.size - 1) {
                currentChunkIndex++
                processQueue()
            }
        }
    }

    fun skipPrevious() {
        val player = mediaPlayer ?: return
        val currentChunk = cachedChunks[currentChunkIndex] ?: return
        val currentPos = player.currentPosition.toLong()

        // Find current subtitle index
        val currentIndex = currentChunk.subtitles.indexOfFirst {
            currentPos >= it.startMillis && currentPos < it.endMillis
        }

        // If we are deep into a subtitle (>2s), restart it. Otherwise go to prev.
        // For strict "Previous" button behavior (SRT based):
        if (currentIndex > 0) {
            val prevSubtitle = currentChunk.subtitles[currentIndex - 1]
            player.seekTo(prevSubtitle.startMillis.toInt())
        } else {
            // Move to previous chunk
            if (currentChunkIndex > 0) {
                currentChunkIndex--
                processQueue()
            } else {
                // Restart beginning
                player.seekTo(0)
            }
        }
    }
    fun playText(text: String, voice: String, title: String = "", sourceUrl: String? = null) {
        // 1. Graceful Transition: Hard reset (clear content) before starting new
        resetPlaybackState(clearContent = true)

        // Sync with repository
        ContentRepository.updateContent(text, title)
        _currentTitle.value = ContentRepository.getCurrentTitle()

        // Start Service
        ContextCompat.startForegroundService(context, Intent(context, TtsMediaService::class.java))

        voiceShortName = voice

        // Chunk text first
        chunks = TextChunker.chunkText(text)

        chunkOffsets.clear()
        var runningOffset = 0
        chunks.forEach { chunk ->
            chunkOffsets.add(runningOffset)
            runningOffset += chunk.length
        }

        currentChunkIndex = 0
        Log.d("TtsManager", "Text split into ${chunks.size} chunks.")

        // Save to History/Database
        scope.launch {
            val finalTitle = title.ifBlank { ContentRepository.getCurrentTitle() }
            currentArticleId = libraryRepository.upsertHistory(finalTitle, text, sourceUrl, chunks)

            // Check saved status
            val article = libraryRepository.getArticleById(currentArticleId)
            _isSaved.value = article?.isSavedToLibrary == true

            if (chunks.isNotEmpty()) {
                processQueue()
            }
        }
    }

    /**
     * Resets player state.
     * @param clearContent If true, wipes the text chunks (New Song).
     *                     If false, keeps text but resets index (Finished Song / Replay).
     */
    private fun resetPlaybackState(clearContent: Boolean) {
        // Stop any active monitoring
        stopMonitoring()

        // Release player immediately
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null

        _isPlaying.value = false
        _isLoading.value = false
        _currentHighlight.value = null
        pendingSeekGlobalIndex = null

        // Cancel background fetches
        fetchingIndices.clear()

        // Reset Queue Position
        currentChunkIndex = 0

        if (clearContent) {
            // WIPE DATA (For new playback)
            chunks = emptyList()
            chunkOffsets.clear()
            cachedChunks.clear()
        }
        // If clearContent is false, we keep chunks/cache so we can Replay
    }

    private fun processQueue() {
        if (currentChunkIndex >= chunks.size) {
            stop(clearContent = false)
            return
        }

        scope.launch {
            _isLoading.value = true

            var chunkData: CachedChunk? = null
            // Skip bad chunks
            while (chunkData == null && currentChunkIndex < chunks.size) {
                chunkData = getOrFetchChunk(currentChunkIndex)
                if (chunkData == null) {
                    Log.e("TtsManager", "Skipping failed chunk $currentChunkIndex")
                    currentChunkIndex++
                }
            }

            _isLoading.value = false

            if (chunkData != null) {
                bufferNextChunks()

                // Determine start time if there is a pending seek
                var startMillis = 0L
                pendingSeekGlobalIndex?.let { targetIndex ->
                    val subtitle = chunkData.subtitles.find {
                        targetIndex >= it.globalRange.start && targetIndex < it.globalRange.end
                    } ?: chunkData.subtitles.firstOrNull() // Fallback to start

                    subtitle?.let { startMillis = it.startMillis }
                    pendingSeekGlobalIndex = null // Clear it
                }

                playFile(chunkData, startMillis)
            } else {
                Log.e("TtsManager", "No playable chunks found.")
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
        // Safety check: if chunks were cleared during fetch
        if (index >= chunks.size) return null

        if (cachedChunks.containsKey(index)) return cachedChunks[index]

        fetchingIndices.add(index)

        val text = chunks[index]
        val ttsText = TextChunker.sanitizeMarkdownForTts(text)

        // Persistent File Naming: article_{id}_v{voiceHash}_chunk_{index}.mp3
        // We include voice hash to ensure changing voice generates new audio.
        val audioDir = File(context.filesDir, "audio_cache").apply { mkdirs() }
        val voiceHash = voiceShortName.hashCode()

        val baseName = if (currentArticleId != -1L) {
            "article_${currentArticleId}_v${voiceHash}_chunk_$index"
        } else {
            "temp_chunk_${text.hashCode()}_v${voiceHash}"
        }

        val outputFile = File(audioDir, "$baseName.mp3")

        // If file exists, we skip generation (Offline Support)
        val result = if (outputFile.exists() && outputFile.length() > 0) {
            // Check for sidecar SRT
            val srtFile = File(audioDir, "$baseName.mp3.srt")
            if (srtFile.exists()) {
                Result.success(Pair(outputFile, srtFile))
            } else {
                // Audio exists but SRT missing? Regenerate to be safe or just generate SRT?
                // For simplicity, regenerate both if one is missing to ensure sync.
                repository.generateAudio(ttsText, voiceShortName, outputFile)
            }
        } else {
            repository.generateAudio(ttsText, voiceShortName, outputFile)
        }

        fetchingIndices.remove(index)

        return if (result.isSuccess) {
            val (audio, srt) = result.getOrNull() ?: return null

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
                val cleanText = rawText.replace(Regex("\\s+"), " ").trim()

                val startMillis = parseTimestamp(startTimeStr)
                val endMillis = parseTimestamp(endTimeStr)

                var startIndex = -1
                var endIndex = -1

                try {
                    val escapedText = Pattern.quote(cleanText)
                    val regexPattern = escapedText.replace(" ", "\\E\\s+\\Q")
                    val textMatcher = Pattern.compile(regexPattern).matcher(chunkText)
                    if (textMatcher.find(searchIndex)) {
                        startIndex = textMatcher.start()
                        endIndex = textMatcher.end()
                    }
                } catch (e: Exception) { }

                if (startIndex == -1) {
                    startIndex = chunkText.indexOf(cleanText, searchIndex)
                    if (startIndex != -1) endIndex = startIndex + cleanText.length
                }

                if (startIndex == -1 && cleanText.isNotEmpty()) {
                    val firstFewWords = cleanText.split(" ").take(3).joinToString(" ")
                    startIndex = chunkText.indexOf(firstFewWords, searchIndex)
                    if (startIndex != -1) endIndex = (startIndex + cleanText.length).coerceAtMost(chunkText.length)
                }

                if (startIndex == -1) continue

                while (endIndex < chunkText.length) {
                    val nextChar = chunkText[endIndex]
                    if (nextChar in listOf('.', ',', '?', '!', ';', ':')) endIndex++ else break
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
        } catch (e: Exception) { }
        return 0L
    }
    private fun playFile(chunkData: CachedChunk, startMillis: Long = 0L) {
        stopMonitoring()
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(chunkData.audioFile.absolutePath)
                prepare()

                try {
                    val params = playbackParams
                    params.speed = currentSpeed
                    playbackParams = params
                } catch (e: Exception) {
                    Log.w("TtsManager", "Failed to set speed", e)
                }

                if (startMillis > 0) {
                    seekTo(startMillis.toInt())
                }

                setOnCompletionListener {
                    // Save progress
                    if (currentArticleId != -1L) {
                        scope.launch(Dispatchers.IO) {
                            libraryRepository.updatePlaybackPosition(currentArticleId, currentChunkIndex)
                        }
                    }
                    currentChunkIndex++
                    processQueue()
                }

                start()
            }
            _isPlaying.value = true
            startMonitoring(chunkData.subtitles)
        } catch (e: Exception) {
            Log.e("TtsManager", "Playback failed for chunk $currentChunkIndex", e)
            currentChunkIndex++
            processQueue()
        }
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
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                stopMonitoring()
                _isPlaying.value = false
            } else {
                try {
                    val params = player.playbackParams
                    params.speed = currentSpeed
                    player.playbackParams = params

                    player.start()
                    _isPlaying.value = true

                    val currentChunk = cachedChunks[currentChunkIndex]
                    if (currentChunk != null) {
                        startMonitoring(currentChunk.subtitles)
                    }
                    bufferNextChunks()
                } catch (e: Exception) {
                    Log.e("TtsManager", "Error resuming playback", e)
                    processQueue()
                }
            }
        } ?: run {
            if (chunks.isNotEmpty()) {
                processQueue()
            }
        }
    }

    fun stop(clearContent: Boolean = true) {
        resetPlaybackState(clearContent)
        if (clearContent) {
            context.stopService(Intent(context, TtsMediaService::class.java))
        }
    }

    fun toggleLibrary() {
        if (currentArticleId == -1L) return

        scope.launch {
            val newState = !_isSaved.value
            libraryRepository.setSavedToLibrary(currentArticleId, newState)
            _isSaved.value = newState

            if (newState) {
                // Trigger background download
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(workDataOf(
                        "articleId" to currentArticleId,
                        "voiceName" to voiceShortName
                    ))
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }
}