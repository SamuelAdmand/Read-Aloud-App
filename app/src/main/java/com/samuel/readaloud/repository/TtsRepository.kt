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
                voiceList.add(
                    Voice(
                        name = obj.optString("FriendlyName", "Unknown"),
                        shortName = obj.getString("ShortName"),
                        gender = obj.optString("Gender", "Unknown"),
                        locale = obj.optString("Locale", "Unknown")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return voiceList
    }
}