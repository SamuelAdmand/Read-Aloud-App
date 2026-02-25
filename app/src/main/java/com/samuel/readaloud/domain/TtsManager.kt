package com.samuel.readaloud.domain

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.domain.player.AudioPlayer
import com.samuel.readaloud.domain.player.AudioSource
import com.samuel.readaloud.domain.player.MediaPlayerAudioPlayer
import com.samuel.readaloud.domain.player.SubtitleData
import com.samuel.readaloud.domain.player.SystemTtsAudioPlayer
import com.samuel.readaloud.repository.ContentRepository
import com.samuel.readaloud.repository.TtsRepository
import com.samuel.readaloud.service.TtsMediaService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val subtitles: List<Subtitle>,
    val voiceId: String
)

class TtsManager private constructor(
    private val context: Context,
    private val repository: TtsRepository
) {
    private val preferenceManager = PreferenceManager(context)
    private val systemTtsEngine = SystemTtsEngine(context).apply {
        reInitialize(preferenceManager.systemTtsEngine)
    }

    // Players
    private val filePlayer = MediaPlayerAudioPlayer()
    private val systemPlayer = SystemTtsAudioPlayer(systemTtsEngine)
    private var activePlayer: AudioPlayer = filePlayer

    private var activeProvider = PreferenceManager.PROVIDER_EDGE
    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    companion object {
        @Volatile
        private var instance: TtsManager? = null
        private const val BUFFER_SIZE = 3

        fun getInstance(context: Context): TtsManager {
            return instance ?: synchronized(this) {
                instance ?: TtsManager(
                    context.applicationContext,
                    TtsRepository(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Queue State
    private var chunks: List<String> = emptyList()
    private var chunkOffsets: MutableList<Int> = mutableListOf()
    private var currentChunkIndex = 0
    private var voiceShortName: String = "en-US-AriaNeural"
    private var currentSanitizedChunk: String = ""
    private var lastSystemTtsSearchIndex: Int = 0

    // Buffering State
    private val cachedChunks = ConcurrentHashMap<Int, CachedChunk>()
    private val fetchingIndices = Collections.synchronizedSet(mutableSetOf<Int>())

    // UI State (Now delegated/combined from players)
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle = _currentTitle.asStateFlow()

    private val _currentHighlight = MutableStateFlow<HighlightRange?>(null)
    val currentHighlight = _currentHighlight.asStateFlow()

    private var pendingSeekGlobalIndex: Int? = null

    private val _currentSpeed = MutableStateFlow(1.0f)
    val currentSpeed = _currentSpeed.asStateFlow()

    private val _currentVoiceId = MutableStateFlow("en-US-AriaNeural")
    val currentVoiceId = _currentVoiceId.asStateFlow()

    init {
        setupPlayerStates()
        setupListeners()
        restoreState()
    }

    private fun restoreState() {
        val savedText = preferenceManager.savedText
        val savedTitle = preferenceManager.savedTitle
        val savedSourceUrl = preferenceManager.savedSourceUrl
        val savedVoice = preferenceManager.savedVoiceId
        val savedGlobalIndex = preferenceManager.savedGlobalIndex

        if (savedText != null && savedVoice != null) {
            ContentRepository.updateContent(savedText, savedTitle ?: "", savedSourceUrl)
            _currentTitle.value = ContentRepository.getCurrentTitle()
            voiceShortName = savedVoice
            _currentVoiceId.value = savedVoice
            
            chunks = TextChunker.chunkText(savedText)
            chunkOffsets.clear()
            var runningOffset = 0
            chunks.forEach { chunk ->
                chunkOffsets.add(runningOffset)
                runningOffset += chunk.length
            }

            val targetChunkIndex = chunkOffsets.indexOfLast { it <= savedGlobalIndex }
            currentChunkIndex = if (targetChunkIndex >= 0) targetChunkIndex else 0
            pendingSeekGlobalIndex = savedGlobalIndex
            
            // We just restore state, we don't automatically play. The user has to hit play.
            // When play is hit, togglePlayPause() or processQueue() will handle the pendingSeekGlobalIndex
        }
    }

    private fun saveState() {
        if (chunks.isNotEmpty()) {
            val text = ContentRepository.getCurrentText()
            val title = ContentRepository.getCurrentTitle()
            val sourceUrl = ContentRepository.getCurrentUrl()
            val currentGlobalIndex = _currentHighlight.value?.start ?: chunkOffsets.getOrElse(currentChunkIndex) { 0 }
            
            preferenceManager.savePlaybackState(text, title, sourceUrl, voiceShortName, currentGlobalIndex)
        } else {
            preferenceManager.clearPlaybackState()
        }
    }

    private fun setupPlayerStates() {
        scope.launch {
            // Collect from both players and update unified state based on which one is active
            launch {
                filePlayer.isPlaying.collectLatest {
                    if (activePlayer == filePlayer) _isPlaying.value = it
                }
            }
            launch {
                systemPlayer.isPlaying.collectLatest {
                    if (activePlayer == systemPlayer) _isPlaying.value = it
                }
            }
            launch {
                filePlayer.isLoading.collectLatest {
                    if (activePlayer == filePlayer) _isLoading.value = it
                }
            }
            launch {
                systemPlayer.isLoading.collectLatest {
                    if (activePlayer == systemPlayer) _isLoading.value = it
                }
            }
            launch {
                filePlayer.currentHighlight.collectLatest {
                    if (activePlayer == filePlayer) _currentHighlight.value = it
                }
            }
            launch {
                systemPlayer.currentHighlight.collectLatest {
                    if (activePlayer == systemPlayer) _currentHighlight.value = it
                }
            }
        }
    }

    private fun setupListeners() {
        filePlayer.setOnCompletionListener { onChunkComplete() }
        filePlayer.setOnErrorListener { msg -> scope.launch { _errorEvents.emit(msg); stop() } }

        systemPlayer.setOnCompletionListener { onChunkComplete() }
        systemPlayer.setOnErrorListener { msg -> scope.launch { _errorEvents.emit(msg); stop() } }

        systemPlayer.setHighlightMapper { start, end ->
            if (start < currentSanitizedChunk.length && end <= currentSanitizedChunk.length) {
                val word = currentSanitizedChunk.substring(start, end).trim()
                if (word.isNotEmpty()) {
                    val rawChunk = chunks.getOrElse(currentChunkIndex) { "" }
                    val indexInRaw = rawChunk.indexOf(word, lastSystemTtsSearchIndex)
                    if (indexInRaw != -1) {
                        lastSystemTtsSearchIndex = indexInRaw
                        val globalOffset = chunkOffsets.getOrElse(currentChunkIndex) { 0 }
                        systemPlayer.updateHighlight(
                            HighlightRange(
                                globalOffset + indexInRaw,
                                globalOffset + indexInRaw + word.length
                            )
                        )
                    }
                }
            }
        }
    }

    private fun onChunkComplete() {
        currentChunkIndex++
        saveState()
        processQueue()
    }

    fun setPlaybackSpeed(speed: Float) {
        if (_currentSpeed.value == speed) return
        _currentSpeed.value = speed
        activePlayer.setSpeed(speed)

        if (activeProvider == PreferenceManager.PROVIDER_SYSTEM && activePlayer.isPlaying.value) {
            val currentGlobalIndex = _currentHighlight.value?.start
                ?: chunkOffsets.getOrElse(currentChunkIndex) { 0 }
            seekToLocation(currentGlobalIndex)
        }
    }

    fun seekToLocation(globalIndex: Int) {
        if (chunks.isEmpty()) return
        val targetChunkIndex = chunkOffsets.indexOfLast { it <= globalIndex }
        if (targetChunkIndex == -1) return

        if (activeProvider == PreferenceManager.PROVIDER_SYSTEM) {
            activePlayer.stop()
            currentChunkIndex = targetChunkIndex
            processQueue()
        } else {
            if (targetChunkIndex == currentChunkIndex) {
                val chunk = cachedChunks[currentChunkIndex] ?: return
                val subtitle = chunk.subtitles.find {
                    globalIndex >= it.globalRange.start && globalIndex < it.globalRange.end
                }
                    ?: chunk.subtitles.minByOrNull { kotlin.math.abs(it.globalRange.start - globalIndex) }
                subtitle?.let { activePlayer.seekTo(it.startMillis) }
                return
            }
            activePlayer.stop()
            currentChunkIndex = targetChunkIndex
            pendingSeekGlobalIndex = globalIndex
            processQueue()
        }
    }

    fun skipNext() {
        if (activeProvider == PreferenceManager.PROVIDER_SYSTEM) {
            onChunkComplete()
            return
        }

        val currentChunk = cachedChunks[currentChunkIndex] ?: return
        // This logic is slightly complex to move into player without more refactoring,
        // so we'll keep the skip logic here using the player's internal knowledge if possible.
        // For now, simple chunk skipping or restarting.
        onChunkComplete()
    }

    fun skipPrevious() {
        if (currentChunkIndex > 0) {
            currentChunkIndex--
            processQueue()
        } else {
            activePlayer.seekTo(0)
        }
    }

    fun playText(text: String, voice: String, title: String = "", sourceUrl: String? = null) {
        resetPlaybackState(clearContent = true)
        val defaultSpeed = preferenceManager.playbackSpeed
        _currentSpeed.value = defaultSpeed

        activeProvider = preferenceManager.ttsProvider
        activePlayer =
            if (activeProvider == PreferenceManager.PROVIDER_SYSTEM) systemPlayer else filePlayer

        ContentRepository.updateContent(text, title)
        _currentTitle.value = ContentRepository.getCurrentTitle()

        ContextCompat.startForegroundService(context, Intent(context, TtsMediaService::class.java))

        voiceShortName = voice
        _currentVoiceId.value = voice
        chunks = TextChunker.chunkText(text)
        chunkOffsets.clear()
        var runningOffset = 0
        chunks.forEach { chunk ->
            chunkOffsets.add(runningOffset)
            runningOffset += chunk.length
        }

        currentChunkIndex = 0
        scope.launch {
            if (chunks.isNotEmpty()) {
                processQueue()
            }
        }
    }

    fun updateVoice(newVoice: String) {
        if (voiceShortName == newVoice) return

        val currentGlobalIndex = _currentHighlight.value?.start
            ?: chunkOffsets.getOrElse(currentChunkIndex) { 0 }

        voiceShortName = newVoice
        _currentVoiceId.value = newVoice

        activePlayer.stop()
        cachedChunks.clear()
        fetchingIndices.clear()

        val newChunkIndex = chunkOffsets.indexOfLast { it <= currentGlobalIndex }
        currentChunkIndex = if (newChunkIndex >= 0) newChunkIndex else 0
        pendingSeekGlobalIndex = currentGlobalIndex

        processQueue()
    }

    fun updateSystemEngine(enginePackage: String?) {
        systemTtsEngine.reInitialize(enginePackage)
        if (activeProvider == PreferenceManager.PROVIDER_SYSTEM) {
            // Restart playback or just clear cache if multiple system engines are used
            activePlayer.stop()
            processQueue()
        }
    }

    private fun resetPlaybackState(clearContent: Boolean) {
        filePlayer.stop()
        systemPlayer.stop()

        _isPlaying.value = false
        _isLoading.value = false
        _currentHighlight.value = null
        pendingSeekGlobalIndex = null
        fetchingIndices.clear()

        if (clearContent) {
            currentChunkIndex = 0
            chunks = emptyList()
            chunkOffsets.clear()
            cachedChunks.clear()
        }
        saveState()
    }

    private fun processQueue() {
        if (currentChunkIndex >= chunks.size) {
            stop(clearContent = false)
            return
        }

        if (activeProvider == PreferenceManager.PROVIDER_SYSTEM) {
            processSystemQueue()
        } else {
            processFileQueue()
        }
    }

    private fun processSystemQueue() {
        val rawText = chunks.getOrNull(currentChunkIndex) ?: return
        currentSanitizedChunk = TextChunker.sanitizeMarkdownForTts(rawText)
        lastSystemTtsSearchIndex = 0

        activePlayer.play(
            AudioSource.SystemText(currentSanitizedChunk, voiceShortName),
            _currentSpeed.value
        )
    }

    private fun processFileQueue() {
        scope.launch {
            _isLoading.value = true
            var chunkData: CachedChunk? = null
            while (chunkData == null && currentChunkIndex < chunks.size) {
                chunkData = getOrFetchChunk(currentChunkIndex)
                if (chunkData != null && chunkData.voiceId != voiceShortName) {
                    cachedChunks.remove(currentChunkIndex)
                    chunkData = null
                }
                if (chunkData == null) {
                    currentChunkIndex++
                }
            }
            _isLoading.value = false

            if (chunkData != null) {
                bufferNextChunks()
                var startMillis = 0L
                pendingSeekGlobalIndex?.let { targetIndex ->
                    val subtitle = chunkData.subtitles.find {
                        targetIndex >= it.globalRange.start && targetIndex < it.globalRange.end
                    } ?: chunkData.subtitles.firstOrNull()
                    subtitle?.let { startMillis = it.startMillis }
                    pendingSeekGlobalIndex = null
                }

                val subtitleData = chunkData.subtitles.map {
                    SubtitleData(it.startMillis, it.endMillis, it.globalRange)
                }
                activePlayer.play(
                    AudioSource.LocalFile(
                        chunkData.audioFile,
                        subtitleData,
                        startMillis
                    ), _currentSpeed.value
                )
            } else {
                stop(clearContent = false)
            }
        }
    }

    private fun bufferNextChunks() {
        val start = currentChunkIndex + 1
        val end = (currentChunkIndex + 1 + BUFFER_SIZE).coerceAtMost(chunks.size)
        // Launch a single coroutine to manage the concurrent fetches for this batch
        scope.launch(Dispatchers.IO) {
            val fetchJobs = (start until end)
                .filter { i -> !cachedChunks.containsKey(i) && !fetchingIndices.contains(i) }
                .map { i ->
                    async { getOrFetchChunk(i) }
                }
            fetchJobs.awaitAll()
        }
    }

    private suspend fun getOrFetchChunk(index: Int): CachedChunk? {
        if (index >= chunks.size) return null
        if (cachedChunks.containsKey(index)) {
            val chunk = cachedChunks[index]
            if (chunk?.voiceId == voiceShortName) return chunk
            else cachedChunks.remove(index)
        }
        fetchingIndices.add(index)
        val targetVoice = voiceShortName
        val text = chunks[index]
        val ttsText = TextChunker.sanitizeMarkdownForTts(text)
        val audioDir = File(context.filesDir, "audio_cache").apply { mkdirs() }
        val voiceHash = targetVoice.hashCode()
        val baseName = "temp_chunk_${text.hashCode()}_v${voiceHash}"
        val currentProvider = preferenceManager.ttsProvider
        val outputFile = File(audioDir, "$baseName.mp3")
        val srtFile = File(audioDir, "$baseName.srt")

        val result = if (outputFile.exists() && outputFile.length() > 0) {
            Log.d("TtsManager", "Using cached audio: ${outputFile.name}")
            if (srtFile.exists()) Result.success(Pair(outputFile, srtFile))
            else repository.generateAudio(ttsText, targetVoice, outputFile, currentProvider)
        } else {
            Log.d("TtsManager", "Fetching new audio: ${outputFile.name} from $currentProvider")
            repository.generateAudio(ttsText, targetVoice, outputFile, currentProvider)
        }

        if (result.isFailure && currentProvider == PreferenceManager.PROVIDER_EDGE) {
            Log.e("TtsManager", "Edge TTS generation failed", result.exceptionOrNull())
            scope.launch { _errorEvents.emit("Edge TTS failed. Please switch to Google TTS in Settings.") }
        } else if (result.isFailure) {
            Log.e("TtsManager", "$currentProvider TTS generation failed", result.exceptionOrNull())
        }

        fetchingIndices.remove(index)

        return if (result.isSuccess) {
            val (audio, srt) = result.getOrNull() ?: return null
            val globalOffset = chunkOffsets.getOrElse(index) { 0 }
            val parsedSubtitles = parseSrt(srt, text, globalOffset)
            val subtitles = parsedSubtitles.ifEmpty {
                listOf(
                    Subtitle(
                        0L,
                        Long.MAX_VALUE,
                        text,
                        HighlightRange(globalOffset, globalOffset + text.length)
                    )
                )
            }
            val cachedChunk = CachedChunk(audio, subtitles, targetVoice)
            if (targetVoice == voiceShortName) cachedChunks[index] = cachedChunk
            cachedChunk
        } else null
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
                } catch (e: Exception) {
                }
                if (startIndex == -1) {
                    startIndex = chunkText.indexOf(cleanText, searchIndex)
                    if (startIndex != -1) endIndex = startIndex + cleanText.length
                }
                if (startIndex == -1 && cleanText.isNotEmpty()) {
                    val firstFewWords = cleanText.split(" ").take(3).joinToString(" ")
                    startIndex = chunkText.indexOf(firstFewWords, searchIndex)
                    if (startIndex != -1) endIndex =
                        (startIndex + cleanText.length).coerceAtMost(chunkText.length)
                }
                if (startIndex == -1) continue
                while (endIndex < chunkText.length) {
                    val nextChar = chunkText[endIndex]
                    if (nextChar in listOf('.', ',', '?', '!', ';', ':')) endIndex++ else break
                }
                searchIndex = endIndex
                subtitles.add(
                    Subtitle(
                        startMillis,
                        endMillis,
                        cleanText,
                        HighlightRange(chunkGlobalOffset + startIndex, chunkGlobalOffset + endIndex)
                    )
                )
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
        }
        return 0L
    }

    fun togglePlayPause() {
        val currentlyPlaying = activePlayer.isPlaying.value
        _isPlaying.value = !currentlyPlaying // Immediate UI feedback

        if (currentlyPlaying) {
            activePlayer.pause()
            saveState()
        } else {
            if (chunks.isEmpty()) {
                _isPlaying.value = false
                return
            }

            // If we've reached the end, restart from beginning
            if (currentChunkIndex >= chunks.size) {
                currentChunkIndex = 0
            }

            // Attempt to resume
            activePlayer.resume(_currentSpeed.value)

            // If it's System TTS, it doesn't support resume, so we must restart.
            // For other players, if they failed to resume (e.g. source cleared), we process queue.
            if (activeProvider == PreferenceManager.PROVIDER_SYSTEM || !activePlayer.isPlaying.value) {
                processQueue()
            }
        }
    }

    fun stop(clearContent: Boolean = true) {
        if (!clearContent) {
           saveState() 
        }
        resetPlaybackState(clearContent)
        if (clearContent) {
            context.stopService(Intent(context, TtsMediaService::class.java))
        }
    }

}

