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

class TypeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TtsRepository()
    // In a real app, inject this as a Singleton. Here we create it.
    private val ttsManager = TtsManager(application, repository)

    // UI State
    var textInput by mutableStateOf("")
        private set

    // Expose TtsManager state to UI
    val isPlaying: StateFlow<Boolean> = ttsManager.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val progress: StateFlow<Float> = ttsManager.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // We keep track of the selected voice (defaulting for now)
    var selectedVoiceName by mutableStateOf("Aria (US)")
    private var selectedVoiceId = "en-US-AriaNeural"

    fun onTextChanged(newText: String) {
        textInput = newText
    }

    fun onPlayPauseClicked() {
        if (isPlaying.value) {
            ttsManager.togglePlayPause()
        } else {
            // If starting fresh
            if (textInput.isNotBlank()) {
                ttsManager.playText(textInput, selectedVoiceId)
            } else {
                ttsManager.togglePlayPause() // Resume if possible
            }
        }
    }

    fun stopPlayback() {
        ttsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}