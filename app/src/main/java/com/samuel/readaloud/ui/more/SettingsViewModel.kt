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

    private val repository = TtsRepository(application)
    private val preferenceManager = PreferenceManager(application)
    var currentProvider by mutableStateOf(preferenceManager.ttsProvider)
        private set
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
                // Pass current provider
                voices = repository.getVoices(currentProvider)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Add new function
    fun onProviderChanged(provider: String) {
        if (currentProvider == provider) return

        currentProvider = provider
        preferenceManager.ttsProvider = provider

        viewModelScope.launch {
            // 1. Load new voices
            try {
                voices = repository.getVoices(provider)
            } catch (e: Exception) {
                voices = emptyList()
            }

            // 2. Select a default voice for the new provider
            val default = when (provider) {
                PreferenceManager.PROVIDER_GOOGLE -> {
                    voices.find { it.shortName == "en" } ?: voices.firstOrNull()
                }
                PreferenceManager.PROVIDER_SYSTEM -> {
                    // Try to find a voice matching the system locale or just pick the first one
                    val sysLocale = java.util.Locale.getDefault().toLanguageTag()
                    voices.find { it.locale == sysLocale } ?: voices.firstOrNull()
                }
                else -> { // Edge
                    voices.find { it.shortName == "en-US-AriaNeural" } ?: voices.firstOrNull()
                }
            }
            default?.let { onVoiceSelected(it) }
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