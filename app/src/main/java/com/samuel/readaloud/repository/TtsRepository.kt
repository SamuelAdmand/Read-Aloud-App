package com.samuel.readaloud.repository

import android.util.Log
import com.chaquo.python.Python
import com.samuel.readaloud.model.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

data class WordTimestamp(
    val word: String,
    val start: Float,
    val end: Float,
    val textOffset: Int,
    val wordLen: Int
)

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
    ): Result<Pair<File, List<WordTimestamp>>> = withContext(Dispatchers.IO) {
        try {
            Log.d("TtsRepository", "Generating audio for: ${outputFile.name} (Text len: ${text.length})")

            // Generate audio via Python
            pythonModule.callAttr("tts", text, voiceShortName, outputFile.absolutePath)

            // Check sidecar JSON
            val metaFile = File(outputFile.absolutePath + ".json")
            val timestamps = if (metaFile.exists()) {
                val jsonContent = metaFile.readText()
                Log.d("TtsRepository", "JSON File found. Size: ${jsonContent.length} chars")
                // Log first 100 chars to debug
                Log.d("TtsRepository", "JSON Preview: ${jsonContent.take(100)}")

                parseTimestamps(jsonContent)
            } else {
                Log.e("TtsRepository", "Meta file NOT found: ${metaFile.absolutePath}")
                emptyList()
            }

            Log.d("TtsRepository", "Parsed ${timestamps.size} timestamps.")

            Result.success(Pair(outputFile, timestamps))
        } catch (e: Exception) {
            Log.e("TtsRepository", "Error generation", e)
            Result.failure(e)
        }
    }

    private fun parseTimestamps(jsonString: String): List<WordTimestamp> {
        val list = mutableListOf<WordTimestamp>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    WordTimestamp(
                        word = obj.optString("word"),
                        start = obj.optDouble("start").toFloat(),
                        end = obj.optDouble("end").toFloat(),
                        textOffset = obj.optInt("text_offset"),
                        wordLen = obj.optInt("word_len")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("TtsRepository", "JSON Parse Error", e)
            e.printStackTrace()
        }
        return list
    }

    private fun parseVoices(jsonString: String): List<Voice> {
        val voiceList = mutableListOf<Voice>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                val shortName = obj.optString("ShortName").takeIf { it.isNotBlank() }
                    ?: obj.optString("Name").takeIf { it.isNotBlank() }
                    ?: ""

                val gender = obj.optString("Gender").ifBlank { "Unknown" }
                val locale = obj.optString("Locale").ifBlank { "Unknown" }

                if (shortName.isNotBlank()) {
                    val namePart = shortName.split("-").lastOrNull() ?: shortName
                    val cleanName = if (namePart.endsWith("Neural")) {
                        namePart.replace("Neural", " (Neural)")
                    } else {
                        namePart
                    }

                    voiceList.add(
                        Voice(
                            name = cleanName,
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