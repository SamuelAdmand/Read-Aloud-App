package com.samuel.readaloud.repository

import com.chaquo.python.Python
import com.samuel.readaloud.model.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class TtsRepository {

    private val pythonModule by lazy {
        Python.getInstance().getModule("tts_engine")
    }

    /**
     * Fetches the list of available voices from the Python engine.
     */
    suspend fun getVoices(): List<Voice> = withContext(Dispatchers.IO) {
        val jsonString = pythonModule.callAttr("get_voices_json").toString()
        parseVoices(jsonString)
    }

    /**
     * Generates audio from text using the specified voice.
     */
    suspend fun generateAudio(text: String, voiceShortName: String, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Call the python function: tts(text, voice, output_file)
            pythonModule.callAttr("tts", text, voiceShortName, outputFile.absolutePath)

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseVoices(jsonString: String): List<Voice> {
        val voiceList = mutableListOf<Voice>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                // 1. Extract ShortName (e.g., "en-US-AriaNeural")
                val shortName = obj.optString("ShortName").takeIf { it.isNotBlank() }
                    ?: obj.optString("Name").takeIf { it.isNotBlank() }
                    ?: ""

                val gender = obj.optString("Gender").ifBlank { "Unknown" }
                val locale = obj.optString("Locale").ifBlank { "Unknown" }

                if (shortName.isNotBlank()) {
                    // 2. Generate a clean name (e.g., "Aria (Neural)")
                    val namePart = shortName.split("-").lastOrNull() ?: shortName
                    val cleanName = if (namePart.endsWith("Neural")) {
                        namePart.replace("Neural", " (Neural)")
                    } else {
                        namePart
                    }

                    voiceList.add(
                        Voice(
                            name = cleanName, // Clean name for UI
                            shortName = shortName,
                            gender = gender,
                            locale = locale
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return voiceList
    }
}