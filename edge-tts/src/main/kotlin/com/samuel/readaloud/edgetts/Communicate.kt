package com.samuel.readaloud.edgetts

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.util.*

class Communicate(
    private val text: String,
    private val voice: String = Constants.DEFAULT_VOICE,
    private val rate: String = "+0%",
    private val volume: String = "+0%",
    private val pitch: String = "+0Hz",
    private val boundary: String = "SentenceBoundary"
) {
    companion object {
        private val client = OkHttpClient()
        private val gson = Gson()

        suspend fun listVoices(): List<EdgeVoice> = withContext(Dispatchers.IO) {
            val url = "${Constants.VOICE_LIST_URL}&Sec-MS-GEC=${DRM.generateSecMsGec()}&Sec-MS-GEC-Version=${Constants.SEC_MS_GEC_VERSION}"
            val request = Request.Builder()
                .url(url)
                .apply {
                    DRM.headersWithMuid(Constants.VOICE_HEADERS).forEach { (k, v) -> header(k, v) }
                }
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList<EdgeVoice>()
                    val body = response.body?.string() ?: return@withContext emptyList<EdgeVoice>()
                    val type = object : com.google.gson.reflect.TypeToken<List<EdgeVoice>>() {}.type
                    gson.fromJson<List<EdgeVoice>>(body, type)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private val ttsConfig = TTSConfig(voice, rate, volume, pitch, boundary)
    private val gson = Gson()

    fun stream(): Flow<TTSChunk> = flow {
        val connectId = Util.connectId()
        val url = "${Constants.WSS_URL}&ConnectionId=$connectId&Sec-MS-GEC=${DRM.generateSecMsGec()}&Sec-MS-GEC-Version=${Constants.SEC_MS_GEC_VERSION}"
        
        val headers = DRM.headersWithMuid(Constants.WSS_HEADERS)
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
            }
            .build()

        val state = CommunicateState()
        val textChunks = Util.splitTextByByteLength(Util.removeIncompatibleCharacters(text), 4096)

        for (chunkText in textChunks) {
            val escapedText = Util.escapeXml(chunkText)
            val ssml = Util.mkssml(ttsConfig, escapedText)
            
            // For each text chunk, we open a new websocket or reuse?
            // Python version opens and closes? 
            // In Python's __stream: async with session.ws_connect(...) as websocket:
            // It calls __stream() for each text chunk in self.texts.
            
            streamChunk(request, ssml, state).collect { 
                emit(it) 
            }
        }
    }

    private fun streamChunk(request: Request, ssml: String, state: CommunicateState): Flow<TTSChunk> = callbackFlow {
        val requestId = Util.connectId()
        val timestamp = Util.dateToString()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send command
                val wd = if (ttsConfig.boundary == "WordBoundary") "true" else "false"
                val sq = if (ttsConfig.boundary != "WordBoundary") "true" else "false"
                
                val configMsg = "X-Timestamp:${Util.dateToString()}\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                        "\"sentenceBoundaryEnabled\":\"$sq\",\"wordBoundaryEnabled\":\"$wd\"" +
                        "},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                
                webSocket.send(configMsg)
                
                // Send SSML
                val ssmlMsg = Util.ssmlHeadersPlusData(requestId, timestamp, ssml)
                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val lines = text.split("\r\n")
                val headers = mutableMapOf<String, String>()
                var bodyStartIndex = 0
                for ((index, line) in lines.withIndex()) {
                    if (line.isEmpty()) {
                        bodyStartIndex = index + 1
                        break
                    }
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        headers[parts[0].trim()] = parts[1].trim()
                    }
                }

                val path = headers["Path"]
                if (path == "audio.metadata") {
                    val body = lines.subList(bodyStartIndex, lines.size).joinToString("\r\n")
                    try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val metadataArray = json.getAsJsonArray("Metadata")
                        for (meta in metadataArray) {
                            val metaObj = meta.asJsonObject
                            val type = metaObj.get("Type").asString
                            if (type == "WordBoundary" || type == "SentenceBoundary") {
                                val data = metaObj.getAsJsonObject("Data")
                                val textVal = data.getAsJsonObject("text").get("Text").asString
                                val offset = data.get("Offset").asLong + state.offsetCompensation
                                val duration = data.get("Duration").asLong
                                
                                val chunk = if (type == "WordBoundary") {
                                    TTSChunk.WordBoundary(textVal, offset, duration)
                                } else {
                                    TTSChunk.SentenceBoundary(textVal, offset, duration)
                                }
                                trySend(chunk)
                                state.lastDurationOffset = offset + duration
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore or log
                    }
                } else if (path == "turn.end") {
                    state.offsetCompensation = state.lastDurationOffset + 8_750_000
                    webSocket.close(1000, "Done")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size < 2) return
                
                val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                if (headerLength > data.size) return
                
                val headerBytes = data.sliceArray(2 until 2 + headerLength)
                val headerText = String(headerBytes, Charsets.UTF_8)
                val lines = headerText.split("\r\n")
                val headers = mutableMapOf<String, String>()
                for (line in lines) {
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        headers[parts[0].trim()] = parts[1].trim()
                    }
                }

                if (headers["Path"] == "audio") {
                    val audioData = data.sliceArray(2 + headerLength + 2 until data.size) // +2 for \r\n
                    if (audioData.isNotEmpty()) {
                        trySend(TTSChunk.Audio(audioData))
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        awaitClose { webSocket.cancel() }
    }

    suspend fun save(audioPath: String, subtitlePath: String? = null) {
        val audioFile = File(audioPath)
        val audioOut = FileOutputStream(audioFile)
        val subMaker = if (subtitlePath != null) SubMaker() else null
        
        try {
            stream().collect { chunk ->
                when (chunk) {
                    is TTSChunk.Audio -> audioOut.write(chunk.data)
                    is TTSChunk.WordBoundary, is TTSChunk.SentenceBoundary -> {
                        subMaker?.feed(chunk)
                    }
                }
            }
            
            if (subtitlePath != null) {
                File(subtitlePath).writeText(subMaker?.getSrt() ?: "")
            }
        } finally {
            audioOut.close()
        }
    }
}
