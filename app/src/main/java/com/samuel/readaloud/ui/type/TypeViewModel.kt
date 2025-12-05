package com.samuel.readaloud.ui.type

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.repository.TtsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.filter
import java.util.Locale
import com.samuel.readaloud.model.Voice

class TypeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TtsRepository()
    private val ttsManager = TtsManager(application, repository)

    // UI State
    var textInput by mutableStateOf("")
        private set

    // NEW: Controls visibility of the player. False = Editing, True = Playing
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

    // Map of Country Name -> List of Voices
    var groupedVoices by mutableStateOf<Map<String, List<Voice>>>(emptyMap())
        private set

    var searchQuery by mutableStateOf("")
        private set

    // List of pinned country names
    var pinnedCountries by mutableStateOf<Set<String>>(emptySet())
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
                        getCountryName(it.locale).contains(searchQuery, ignoreCase = true)
            }
        }

        // Group by Country
        val grouped = filtered.groupBy { getCountryName(it.locale) }

        // Sort: Pinned first (Alphabetical), then others (Alphabetical)
        groupedVoices = grouped.toSortedMap(compareBy { country ->
            val isPinned = pinnedCountries.contains(country)
            if (isPinned) "0_$country" else "1_$country"
        })
    }

    // --- Helper Functions ---

    private fun getCountryName(localeString: String): String {
        return try {
            val locale = Locale.forLanguageTag(localeString)
            locale.displayCountry.ifBlank { "Unknown Region" }
        } catch (e: Exception) {
            "Unknown Region"
        }
    }
    fun onTextChanged(newText: String) {
        textInput = newText
    }
    // Called when the "Tick" FAB is clicked
    fun onConfirmText() {
        if (textInput.isBlank()) return

        isPlayerVisible = true
        // Start playback immediately from the beginning
        ttsManager.playText(textInput, selectedVoiceId)
        // Ensure speed is applied
        ttsManager.setPlaybackSpeed(playbackSpeed)
    }

    fun onEditClicked() {
        // Stop playback because editing text invalidates the current audio
        ttsManager.stop()
        isPlayerVisible = false
    }

    fun onPlayPauseClicked() {
        ttsManager.togglePlayPause()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        updateGroupedVoices()
    }

    fun toggleCountryPin(country: String) {
        pinnedCountries = if (pinnedCountries.contains(country)) {
            pinnedCountries - country
        } else {
            pinnedCountries + country
        }
        updateGroupedVoices()
    }

    fun onVoiceSelected(voice: Voice) {
        selectedVoiceId = voice.shortName
        selectedVoiceName = voice.name // The name is now clean from Repository

        // Instant restart if player is visible/active
        if (isPlayerVisible) {
            // Stop current playback
            ttsManager.stop()
            // Restart immediately with new voice
            ttsManager.playText(textInput, selectedVoiceId)
            // Ensure speed is maintained
            ttsManager.setPlaybackSpeed(playbackSpeed)
        }
    }

    fun onSpeedChanged(newSpeed: Float) {
        playbackSpeed = newSpeed
        ttsManager.setPlaybackSpeed(newSpeed)
    }
}