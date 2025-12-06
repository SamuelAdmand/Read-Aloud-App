package com.samuel.readaloud.ui.home

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.repository.TtsRepository
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(
    private val repository: TtsRepository = TtsRepository()
) : ViewModel() {

    // State for the text input box
    var textInput by mutableStateOf("")
        private set

    // State to show a loading indicator or disable button
    var isGenerating by mutableStateOf(false)
        private set

    // New state for voices
    var voices by mutableStateOf<List<Voice>>(emptyList())
        private set

    var selectedVoice by mutableStateOf<Voice?>(null)
        private set

    var isLoadingVoices by mutableStateOf(true)
        private set

    private var mediaPlayer: MediaPlayer? = null

    init {
        loadVoices()
    }

    private fun loadVoices() {
        viewModelScope.launch {
            isLoadingVoices = true
            try {
                val fetchedVoices = repository.getVoices()
                voices = fetchedVoices
                // Default to Aria (US) if available, otherwise the first one
                selectedVoice = fetchedVoices.find { it.shortName == "en-US-AriaNeural" }
                    ?: fetchedVoices.firstOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isLoadingVoices = false
        }
    }

    fun onTextChanged(newText: String) {
        textInput = newText
    }

    fun onVoiceSelected(voice: Voice) {
        selectedVoice = voice
    }

    fun speak(cacheDir: File) {
        val voice = selectedVoice ?: return
        if (textInput.isBlank()) return

        viewModelScope.launch {
            isGenerating = true
            val outputFile = File(cacheDir, "tts_test.mp3")

            // Use the selected voice's shortName
            val result = repository.generateAudio(textInput, voice.shortName, outputFile)

            result.onSuccess { (file, _) ->
                // We destructured the Pair to get just the file, ignoring timestamps for this screen
                playAudio(file)
            }.onFailure {
                it.printStackTrace()
            }
            isGenerating = false
        }
    }

    private fun playAudio(file: File) {
        // Always release the previous player to free resources
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}