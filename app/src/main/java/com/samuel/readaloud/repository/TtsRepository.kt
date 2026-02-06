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
            PreferenceManager.PROVIDER_SYSTEM -> getSystemVoices(PreferenceManager(context).systemTtsEngine)
            PreferenceManager.PROVIDER_EDGE -> getEdgeVoices()
            PreferenceManager.PROVIDER_GOOGLE -> getGoogleVoices()
            else -> emptyList()
        }
    }

    private suspend fun getEdgeVoices(): List<Voice> {
        Log.d("TtsRepository", "Fetching Edge voices...")
        return try {
            val voices = Communicate.listVoices().map { voice ->
                Voice(
                    name = voice.FriendlyName ?: voice.ShortName,
                    shortName = voice.ShortName,
                    gender = voice.Gender,
                    locale = voice.Locale
                )
            }.sortedBy { it.locale }
            Log.d("TtsRepository", "Fetched ${voices.size} Edge voices")
            voices
        } catch (e: Exception) {
            Log.e("TtsRepository", "Error fetching Edge voices", e)
            emptyList()
        }
    }

    fun getGoogleVoices(): List<Voice> {
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

    suspend fun getSystemEngines(): List<Pair<String, String>> = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val engines = tts?.engines?.map { engine ->
                        engine.label to engine.name
                    } ?: emptyList()
                    if (cont.isActive) cont.resume(engines)
                } catch (e: Exception) {
                    Log.e("TtsRepository", "Error fetching system engines", e)
                    if (cont.isActive) cont.resume(emptyList())
                } finally {
                    tts?.shutdown()
                }
            } else {
                Log.e("TtsRepository", "Failed to initialize TTS for engine discovery")
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }

    private suspend fun getSystemVoices(enginePackage: String?): List<Voice> = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context, { status ->
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
        }, enginePackage)
    }

    suspend fun generateAudio(
        text: String,
        voiceShortName: String,
        outputFile: File,
        provider: String
    ): Result<Pair<File, File>> = withContext(Dispatchers.IO) {
        Log.d("TtsRepository", "Generating audio for provider: $provider, voice: $voiceShortName")
        try {
            when (provider) {
                PreferenceManager.PROVIDER_EDGE -> {
                    val subtitleFile = File(outputFile.absolutePath.replace(".mp3", ".srt"))
                    val communicate = Communicate(text, voice = voiceShortName)
                    Log.d("TtsRepository", "Starting Edge TTS stream to: ${outputFile.name}")
                    communicate.save(outputFile.absolutePath, subtitleFile.absolutePath)
                    Log.d("TtsRepository", "Edge TTS generation complete: ${outputFile.length()} bytes")
                    if (outputFile.length() == 0L) {
                        Result.failure(Exception("Edge TTS generated an empty file"))
                    } else {
                        Result.success(outputFile to subtitleFile)
                    }
                }
                PreferenceManager.PROVIDER_GOOGLE -> {
                    val googleTTS = GoogleTTS(text, lang = voiceShortName)
                    Log.d("TtsRepository", "Starting Google TTS request to: ${outputFile.name}")
                    googleTTS.save(outputFile.absolutePath)
                    Log.d("TtsRepository", "Google TTS generation complete: ${outputFile.length()} bytes")
                    if (outputFile.length() == 0L) {
                        Result.failure(Exception("Google TTS generated an empty file"))
                    } else {
                        // Google doesn't provide boundaries, so no subtitle file
                        Result.success(outputFile to outputFile) // Pair with same if no sub
                    }
                }
                else -> {
                    Log.e("TtsRepository", "Unsupported provider: $provider")
                    Result.failure(Exception("Unsupported provider: $provider"))
                }
            }
        } catch (e: Exception) {
            Log.e("TtsRepository", "Global error during audio generation", e)
            Result.failure(e)
        }
    }
}
