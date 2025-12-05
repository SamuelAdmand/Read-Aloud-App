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
    private val ttsManager = TtsManager(application, repository)

    // UI State
    var textInput by mutableStateOf("")
        private set

    // NEW: Controls visibility of the player. False = Editing, True = Playing
    var isPlayerVisible by mutableStateOf(false)
        private set

    val isPlaying: StateFlow<Boolean> = ttsManager.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val progress: StateFlow<Float> = ttsManager.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    var selectedVoiceName by mutableStateOf("Aria (US)")
    private var selectedVoiceId = "en-US-AriaNeural"

    fun onTextChanged(newText: String) {
        textInput = newText
    }

    // Called when the "Tick" FAB is clicked
    fun onConfirmText() {
        if (textInput.isBlank()) return

        isPlayerVisible = true
        // Start playback immediately from the beginning
        ttsManager.playText(textInput, selectedVoiceId)
    }

    fun onEditClicked() {
        // Stop playback because editing text invalidates the current audio
        ttsManager.stop()
        isPlayerVisible = false
    }

    fun onPlayPauseClicked() {
        ttsManager.togglePlayPause()
    }

    fun stopPlayback() {
        ttsManager.stop()
        isPlayerVisible = false
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}