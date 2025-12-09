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

            // 2. Check if we have a saved preference for this provider
            val savedVoice = preferenceManager.getVoiceForProvider(provider)

            if (savedVoice != null) {
                // Restore saved voice
                val (id, name) = savedVoice
                defaultVoiceId = id
                defaultVoiceName = name
                // Ensure global prefs are synced (though getVoiceForProvider logic might handle this, explicit is safe)
                preferenceManager.voiceId = id
                preferenceManager.voiceName = name
            } else {
                // Fallback: Pick a smart default
                val default = when (provider) {
                    PreferenceManager.PROVIDER_GOOGLE -> {
                        voices.find { it.shortName == "en" } ?: voices.firstOrNull()
                    }
                    PreferenceManager.PROVIDER_SYSTEM -> {
                        val sysLocale = java.util.Locale.getDefault().toLanguageTag()
                        voices.find { it.locale == sysLocale } ?: voices.firstOrNull()
                    }
                    else -> { // Edge
                        voices.find { it.shortName == "en-US-AriaNeural" } ?: voices.firstOrNull()
                    }
                }

                default?.let {
                    // Save this new default for the provider
                    onVoiceSelected(it)
                }
            }
        }
    }
    // --- Actions ---

    fun onVoiceSelected(voice: Voice) {
        // Update State
        defaultVoiceName = voice.name
        defaultVoiceId = voice.shortName

        // Save using the new method (persists for this specific provider)
        preferenceManager.saveVoiceForProvider(currentProvider, voice.shortName, voice.name)
    }

    fun onSpeedChanged(newSpeed: Float) {
        // Update State
        defaultSpeed = newSpeed
        // Save to Prefs
        preferenceManager.playbackSpeed = newSpeed
    }
}