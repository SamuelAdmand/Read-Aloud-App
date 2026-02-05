package com.samuel.readaloud.repository

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.edgetts.Communicate
import com.samuel.readaloud.googletts.GoogleTTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class TtsRepository(private val context: Context) {

    suspend fun getVoices(provider: String): List<Voice> {
        return when (provider) {
            PreferenceManager.PROVIDER_SYSTEM -> getSystemVoices()
            PreferenceManager.PROVIDER_EDGE -> getEdgeVoices()
            PreferenceManager.PROVIDER_GOOGLE -> getGoogleVoices()
            else -> emptyList()
        }
    }

    private suspend fun getEdgeVoices(): List<Voice> {
        return Communicate.listVoices().map { voice ->
            Voice(
                name = voice.FriendlyName ?: voice.ShortName,
                shortName = voice.ShortName,
                gender = voice.Gender,
                locale = voice.Locale
            )
        }.sortedBy { it.locale }
    }

    private fun getGoogleVoices(): List<Voice> {
        // Simplified: return common languages as Google TTS voices
        val languages = listOf(
            "en" to "English",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "hi" to "Hindi",
            "ja" to "Japanese",
            "ko" to "Korean",
            "zh-CN" to "Chinese (Simplified)"
        )
        return languages.map { (code, name) ->
            Voice(name = name, shortName = code, gender = "Unknown", locale = code)
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
        try {
            when (provider) {
                PreferenceManager.PROVIDER_EDGE -> {
                    val subtitleFile = File(outputFile.absolutePath.replace(".mp3", ".srt"))
                    val communicate = Communicate(text, voice = voiceShortName)
                    communicate.save(outputFile.absolutePath, subtitleFile.absolutePath)
                    Result.success(outputFile to subtitleFile)
                }
                PreferenceManager.PROVIDER_GOOGLE -> {
                    val googleTTS = GoogleTTS(text, lang = voiceShortName)
                    googleTTS.save(outputFile.absolutePath)
                    // Google doesn't provide boundaries, so no subtitle file
                    Result.success(outputFile to outputFile) // Pair with same if no sub
                }
                else -> Result.failure(Exception("Unsupported provider: $provider"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
