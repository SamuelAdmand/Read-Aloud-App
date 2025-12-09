package com.samuel.readaloud.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SystemTtsEngine(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    // Callbacks
    private var onHighlightListener: ((Int, Int) -> Unit)? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: (() -> Unit)? = null

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady = _isEngineReady.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                _isEngineReady.value = true
                setupListener()
            } else {
                Log.e("SystemTtsEngine", "Failed to initialize System TTS")
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Playback started
            }

            override fun onDone(utteranceId: String?) {
                onCompletionListener?.invoke()
            }

            override fun onError(utteranceId: String?) {
                Log.e("SystemTtsEngine", "Error during playback: $utteranceId")
                onErrorListener?.invoke()
            }

            // This is the magic method for word-by-word highlighting (API 26+)
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                // 'start' and 'end' are relative to the chunk text
                onHighlightListener?.invoke(start, end)
            }
        })
    }

    fun setListeners(
        onHighlight: (Int, Int) -> Unit,
        onComplete: () -> Unit,
        onError: () -> Unit
    ) {
        this.onHighlightListener = onHighlight
        this.onCompletionListener = onComplete
        this.onErrorListener = onError
    }

    fun speak(text: String, voiceShortName: String, speed: Float) {
        if (!isInitialized || tts == null) return

        // Set Voice
        val voice = tts?.voices?.find { it.name == voiceShortName }
        if (voice != null) {
            tts?.voice = voice
        } else {
            // Fallback to default locale if voice not found (prevent silence)
            tts?.language = Locale.getDefault()
        }

        // Set Speed
        tts?.setSpeechRate(speed)

        // Speak
        // QUEUE_FLUSH drops all pending entries. Important for immediate playback.
        val params = android.os.Bundle()
        // Request range events for highlighting
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "id_${System.currentTimeMillis()}")

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "id_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun setSpeed(speed: Float) {
        if (isInitialized) {
            tts?.setSpeechRate(speed)
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}