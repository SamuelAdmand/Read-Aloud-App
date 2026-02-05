package com.samuel.readaloud.googletts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

class GoogleTTS(
    private val text: String,
    private val lang: String = "en",
    private val slow: Boolean = false
) {
    private val client = OkHttpClient()

    suspend fun save(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        val out = FileOutputStream(file)
        Log.d("GoogleTTS", "Saving audio to $path using translate_tts API")
        try {
            val tokens = tokenize(text)
            Log.d("GoogleTTS", "Split text into ${tokens.size} tokens")
            for (token in tokens) {
                val audioData = fetchAudio(token)
                if (audioData != null) {
                    Log.v("GoogleTTS", "Fetched chunk: ${audioData.size} bytes")
                    out.write(audioData)
                } else {
                    Log.e("GoogleTTS", "Failed to fetch audio for token: $token")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleTTS", "Error during save", e)
        } finally {
            out.close()
        }
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= 200) {
                tokens.add(remaining)
                break
            }
            var splitIndex = remaining.lastIndexOf('.', 200)
            if (splitIndex == -1) splitIndex = remaining.lastIndexOf(' ', 200)
            if (splitIndex == -1) splitIndex = 200
            tokens.add(remaining.substring(0, splitIndex + 1).trim())
            remaining = remaining.substring(splitIndex + 1).trim()
        }
        return tokens
    }

    private suspend fun fetchAudio(part: String): ByteArray? {
        val encodedText = URLEncoder.encode(part, "UTF-8")
        val speed = if (slow) "0.3" else "1"
        
        // Extract language code (e.g., "en-US-AriaNeural" -> "en-US" or "en")
        val langParts = lang.split("-")
        val langCode = if (langParts.size >= 2) "${langParts[0]}-${langParts[1]}" else langParts[0]
        
        Log.d("GoogleTTS", "Fetching audio for lang: $langCode, text snippet: ${part.take(20)}")
        
        // Using client=tw-ob which is often more stable for this endpoint
        val url = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=$langCode&total=1&idx=0&textlen=${part.length}&client=tw-ob&ttsspeed=$speed"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                Log.d("GoogleTTS", "HTTP response: ${response.code}")
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    Log.e("GoogleTTS", "Request failed: ${response.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleTTS", "Network error", e)
            null
        }
    }
}