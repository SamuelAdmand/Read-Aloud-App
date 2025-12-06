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
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TtsRepository()
    private val preferenceManager = PreferenceManager(application)

    // --- State ---
    var defaultVoiceName by mutableStateOf(preferenceManager.voiceName)
        private set

    // We need to expose the ID to pre-select correctly in the new UI
    var defaultVoiceId by mutableStateOf(preferenceManager.voiceId)
        private set

    var defaultSpeed by mutableStateOf(preferenceManager.playbackSpeed)
        private set

    // --- Voice Selection Data ---
    // Change: Expose raw list of voices for the new UI component
    var voices by mutableStateOf<List<Voice>>(emptyList())
        private set

    init {
        loadVoices()
    }

    private fun loadVoices() {
        viewModelScope.launch {
            try {
                voices = repository.getVoices()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Actions ---

    fun onVoiceSelected(voice: Voice) {
        // Update State
        defaultVoiceName = voice.name
        defaultVoiceId = voice.shortName
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
}