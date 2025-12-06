package com.samuel.readaloud.ui.type

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.repository.ContentRepository
import com.samuel.readaloud.repository.TtsRepository
import kotlinx.coroutines.launch

class TypeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TtsRepository()
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

    // Exposed for UI
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

    fun loadContentForEdit() {
        textInput = ContentRepository.getCurrentText()
    }

    fun onTextChanged(newText: String) {
        textInput = newText
    }

    fun onPlayClicked() {
        if (textInput.isBlank()) return
        ttsManager.playText(textInput, selectedVoiceId)
        ttsManager.setPlaybackSpeed(playbackSpeed)
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
}