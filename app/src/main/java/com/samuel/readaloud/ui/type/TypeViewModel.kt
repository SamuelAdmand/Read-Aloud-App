package com.samuel.readaloud.ui.type

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.repository.TtsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

// Sealed interface for grouping logic
sealed interface VoiceGroup {
    data class SingleRegion(val voices: List<Voice>) : VoiceGroup
    data class MultiRegion(val regions: Map<String, List<Voice>>) : VoiceGroup
}

class TypeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TtsRepository()
    private val ttsManager = TtsManager(application, repository)

    // UI State
    var textInput by mutableStateOf("")
        private set

    var isPlayerVisible by mutableStateOf(false)
        private set

    val isPlaying: StateFlow<Boolean> = ttsManager.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isLoading: StateFlow<Boolean> = ttsManager.isLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Voice & Playback State ---
    var selectedVoiceName by mutableStateOf("Aria (US)")
    private var selectedVoiceId = "en-US-AriaNeural"

    var playbackSpeed by mutableStateOf(1.0f)
        private set

    // --- Voice Selection UI State ---
    private val _allVoices = mutableListOf<Voice>()

    // Map: Language Name -> VoiceGroup (either Single or Multi Region)
    var groupedVoices by mutableStateOf<Map<String, VoiceGroup>>(emptyMap())
        private set

    var searchQuery by mutableStateOf("")
        private set

    // Set of pinned Locale Strings
    var pinnedRegions by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        loadVoices()
    }

    private fun loadVoices() {
        viewModelScope.launch {
            try {
                val voices = repository.getVoices()
                _allVoices.clear()
                _allVoices.addAll(voices)
                updateGroupedVoices()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateGroupedVoices() {
        val filtered = if (searchQuery.isBlank()) {
            _allVoices
        } else {
            _allVoices.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        getLanguageName(it.locale).contains(searchQuery, ignoreCase = true) ||
                        getRegionName(it.locale).contains(searchQuery, ignoreCase = true)
            }
        }

        // 1. Group by Language
        val byLanguage = filtered.groupBy { getLanguageName(it.locale) }

        // 2. Process each language group
        val processedGroups = byLanguage.mapValues { (_, voicesInLang) ->
            // Group by Region within this language
            val byRegion = voicesInLang.groupBy { getRegionName(it.locale) }

            if (byRegion.size == 1) {
                // Case A: Only one region (e.g., Hindi -> India)
                // Return just the list of voices directly
                VoiceGroup.SingleRegion(voicesInLang)
            } else {
                // Case B: Multiple regions (e.g., English -> US, UK, India)
                // Sort regions (Pinned first, then Alphabetical)
                val sortedRegions = byRegion.toSortedMap(compareBy { regionName ->
                    val sampleVoice = byRegion[regionName]?.firstOrNull()
                    val localeCode = sampleVoice?.locale ?: ""
                    val isPinned = pinnedRegions.contains(localeCode)
                    if (isPinned) "0_$regionName" else "1_$regionName"
                })
                VoiceGroup.MultiRegion(sortedRegions)
            }
        }.toSortedMap()

        groupedVoices = processedGroups
    }

    // --- Actions ---

    fun onTextChanged(newText: String) {
        textInput = newText
    }

    fun onConfirmText() {
        if (textInput.isBlank()) return
        isPlayerVisible = true
        ttsManager.playText(textInput, selectedVoiceId)
        ttsManager.setPlaybackSpeed(playbackSpeed)
    }

    fun onEditClicked() {
        ttsManager.stop()
        isPlayerVisible = false
    }

    fun onPlayPauseClicked() {
        ttsManager.togglePlayPause()
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        updateGroupedVoices()
    }

    fun toggleRegionPin(locale: String) {
        pinnedRegions = if (pinnedRegions.contains(locale)) {
            pinnedRegions - locale
        } else {
            pinnedRegions + locale
        }
        updateGroupedVoices()
    }

    fun onVoiceSelected(voice: Voice) {
        selectedVoiceId = voice.shortName
        selectedVoiceName = voice.name

        if (isPlayerVisible) {
            ttsManager.stop()
            ttsManager.playText(textInput, selectedVoiceId)
            ttsManager.setPlaybackSpeed(playbackSpeed)
        }
    }

    fun onSpeedChanged(newSpeed: Float) {
        playbackSpeed = newSpeed
        ttsManager.setPlaybackSpeed(newSpeed)
    }

    // --- Helpers ---

    private fun getLanguageName(localeString: String): String {
        return try {
            Locale.forLanguageTag(localeString).displayLanguage.ifBlank { "Unknown Language" }
        } catch (e: Exception) { "Unknown" }
    }

    private fun getRegionName(localeString: String): String {
        return try {
            Locale.forLanguageTag(localeString).displayCountry.ifBlank { "Global" }
        } catch (e: Exception) { "Global" }
    }
}