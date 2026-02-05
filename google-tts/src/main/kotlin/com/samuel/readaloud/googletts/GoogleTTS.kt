package com.samuel.readaloud.googletts

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern

class GoogleTTS(
    private val text: String,
    private val lang: String = "en",
    private val slow: Boolean = false
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun save(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        val out = FileOutputStream(file)
        try {
            val tokens = tokenize(text)
            for (token in tokens) {
                val audioData = fetchAudio(token)
                if (audioData != null) {
                    out.write(audioData)
                }
            }
        } finally {
            out.close()
        }
    }

    private fun tokenize(text: String): List<String> {
        // Simplified tokenization: split by length (max 100 chars)
        // Python gTTS does more complex stuff, but this is a start.
        val tokens = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= 100) {
                tokens.add(remaining)
                break
            }
            var splitIndex = remaining.lastIndexOf(' ', 100)
            if (splitIndex == -1) splitIndex = 100
            tokens.add(remaining.substring(0, splitIndex).trim())
            remaining = remaining.substring(splitIndex).trim()
        }
        return tokens
    }

    private suspend fun fetchAudio(part: String): ByteArray? {
        val speed = if (slow) "true" else "null"
        val parameter = listOf(part, lang, speed, "null")
        val escapedParameter = gson.toJson(parameter)
        val rpc = listOf(listOf(listOf(Constants.RPC_ID, escapedParameter, null, "generic")))
        val escapedRpc = gson.toJson(rpc)
        
        val content = "f.req=" + URLEncoder.encode(escapedRpc, "UTF-8") + "&"
        val requestBody = content.toRequestBody("application/x-www-form-urlencoded;charset=utf-8".toMediaType())
        
        val request = Request.Builder()
            .url(Constants.TRANSLATE_URL)
            .apply {
                Constants.HEADERS.forEach { (k, v) -> header(k, v) }
            }
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            
            val body = response.body?.string() ?: return null
            val pattern = Pattern.compile("jQ1olc\",\"\\[\\\\\"(.*)\\\\\"\\]")
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val base64Data = matcher.group(1) ?: return null
                return Base64.getDecoder().decode(base64Data)
            }
        }
        return null
    }
}
