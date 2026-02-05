package com.samuel.readaloud.repository

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.model.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class TtsRepository(private val context: Context) {

    suspend fun getVoices(provider: String): List<Voice> {
        return if (provider == PreferenceManager.PROVIDER_SYSTEM) {
            getSystemVoices()
        } else {
            // Python backend removed. Return empty list for now.
            emptyList()
        }
    }

    private suspend fun getSystemVoices(): List<Voice> = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val voices = tts?.voices?.map { voice ->
                        Voice(
                            name = "${voice.locale.displayLanguage} (${voice.name})",
                            shortName = voice.name,
                            gender = "Unknown",
                            locale = voice.locale.toLanguageTag()
                        )
                    }?.sortedBy { it.locale } ?: emptyList()

                    if (cont.isActive) cont.resume(voices)
                } catch (e: Exception) {
                    Log.e("TtsRepository", "Error fetching system voices", e)
                    if (cont.isActive) cont.resume(emptyList())
                } finally {
                    tts?.shutdown()
                }
            } else {
                Log.e("TtsRepository", "Failed to initialize System TTS")
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }

    suspend fun generateAudio(
        text: String,
        voiceShortName: String,
        outputFile: File,
        provider: String
    ): Result<Pair<File, File>> = withContext(Dispatchers.IO) {
        // System TTS does not use this generation pipeline
        // Python backend removed.
        Result.failure(Exception("Python backend removed. Generation disabled until Native TTS is implemented."))
    }
}
