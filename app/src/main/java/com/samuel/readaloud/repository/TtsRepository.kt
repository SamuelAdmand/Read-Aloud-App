package com.samuel.readaloud.repository

import android.util.Log
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

    suspend fun getVoices(): List<Voice> = withContext(Dispatchers.IO) {
        val jsonString = pythonModule.callAttr("get_voices_json").toString()
        parseVoices(jsonString)
    }

    suspend fun generateAudio(
        text: String,
        voiceShortName: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d("TtsRepository", "Generating audio... (Length: ${text.length})")
            pythonModule.callAttr("tts", text, voiceShortName, outputFile.absolutePath)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e("TtsRepository", "Generation Failed", e)
            Result.failure(e)
        }
    }

    private fun parseVoices(jsonString: String): List<Voice> {
        val voiceList = mutableListOf<Voice>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val shortName = obj.optString("ShortName").takeIf { it.isNotBlank() } ?: ""
                val friendlyName = obj.optString("FriendlyName").takeIf { it.isNotBlank() } ?: shortName
                val locale = obj.optString("Locale").ifBlank { "Unknown" }

                if (shortName.isNotBlank()) {
                    voiceList.add(Voice(friendlyName, shortName, "Unknown", locale))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return voiceList
    }
}