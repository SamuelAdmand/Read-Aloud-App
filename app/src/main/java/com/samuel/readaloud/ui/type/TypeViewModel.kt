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
import com.samuel.readaloud.ui.components.VoiceGroup
import kotlinx.coroutines.launch
import java.util.Locale

class TypeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TtsRepository()
    // Use Singleton Instance
    private val ttsManager = TtsManager.getInstance(application)
    private val preferenceManager = com.samuel.readaloud.data.local.PreferenceManager(application)

    // UI State
    var textInput by mutableStateOf("")
        private set

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

        val byLanguage = filtered.groupBy { getLanguageName(it.locale) }

        val processedGroups = byLanguage.mapValues { (_, voicesInLang) ->
            val byRegion = voicesInLang.groupBy { getRegionName(it.locale) }

            if (byRegion.size == 1) {
                VoiceGroup.SingleRegion(voicesInLang)
            } else {
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

    /**
     * Initializes playback in the TtsManager.
     * UI should observe this completion to navigate to PlayerScreen.
     */
    fun onPlayClicked() {
        if (textInput.isBlank()) return
        ttsManager.playText(textInput, selectedVoiceId)
        ttsManager.setPlaybackSpeed(playbackSpeed)
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
    }

    fun onSpeedChanged(newSpeed: Float) {
        playbackSpeed = newSpeed
    }

    fun loadDefaultSettings() {
        selectedVoiceName = preferenceManager.voiceName
        selectedVoiceId = preferenceManager.voiceId
        playbackSpeed = preferenceManager.playbackSpeed
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