package com.samuel.readaloud.ui.more

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.repository.TtsRepository
import com.samuel.readaloud.ui.components.VoiceGroup
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TtsRepository()
    private val preferenceManager = PreferenceManager(application)

    // --- State ---
    var defaultVoiceName by mutableStateOf(preferenceManager.voiceName)
        private set

    var defaultSpeed by mutableStateOf(preferenceManager.playbackSpeed)
        private set

    // --- Voice Selection Data ---
    private val _allVoices = mutableListOf<Voice>()
    var groupedVoices by mutableStateOf<Map<String, VoiceGroup>>(emptyMap())
        private set

    var searchQuery by mutableStateOf("")
        private set

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

    // Duplicate grouping logic from TypeViewModel to ensure consistent UI
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
        // Update State
        defaultVoiceName = voice.name
        // Save to Prefs
        preferenceManager.voiceId = voice.shortName
        preferenceManager.voiceName = voice.name
    }

    fun onSpeedChanged(newSpeed: Float) {
        // Update State
        defaultSpeed = newSpeed
        // Save to Prefs
        preferenceManager.playbackSpeed = newSpeed
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