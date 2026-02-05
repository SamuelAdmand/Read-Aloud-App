package com.samuel.readaloud.edgetts

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object Util {
    fun removeIncompatibleCharacters(text: String): String {
        return text.map { char ->
            val code = char.code
            if ((code in 0..8) || (code in 11..12) || (code in 14..31)) {
                ' '
            } else {
                char
            }
        }.joinToString("")
    }

    fun connectId(): String {
        return UUID.randomUUID().toString().replace("-", "").lowercase()
    }

    fun splitTextByByteLength(text: String, byteLength: Int): List<String> {
        val result = mutableListOf<String>()
        var currentText = text.trim()
        val utf8Text = currentText.toByteArray(Charsets.UTF_8)

        if (utf8Text.size <= byteLength) {
            if (currentText.isNotEmpty()) result.add(currentText)
            return result
        }

        while (currentText.toByteArray(Charsets.UTF_8).size > byteLength) {
            var splitAt = findLastNewlineOrSpaceWithinLimit(currentText, byteLength)
            if (splitAt < 0) {
                splitAt = findSafeUtf8SplitPoint(currentText, byteLength)
            }
            splitAt = adjustSplitPointForXmlEntity(currentText, splitAt)

            if (splitAt <= 0) break

            val chunk = currentText.substring(0, splitAt).trim()
            if (chunk.isNotEmpty()) result.add(chunk)
            currentText = currentText.substring(splitAt).trim()
        }

        if (currentText.isNotEmpty()) result.add(currentText)
        return result
    }

    private fun findLastNewlineOrSpaceWithinLimit(text: String, byteLimit: Int): Int {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val limit = minOf(bytes.size, byteLimit)
        val subText = String(bytes, 0, limit, Charsets.UTF_8)
        
        var splitAt = subText.lastIndexOf('\n')
        if (splitAt < 0) {
            splitAt = subText.lastIndexOf(' ')
        }
        return splitAt
    }

    private fun findSafeUtf8SplitPoint(text: String, byteLimit: Int): Int {
        val bytes = text.toByteArray(Charsets.UTF_8)
        var splitAt = minOf(bytes.size, byteLimit)
        while (splitAt > 0) {
            try {
                String(bytes, 0, splitAt, Charsets.UTF_8)
                return splitAt
            } catch (e: Exception) {
                splitAt--
            }
        }
        return splitAt
    }

    private fun adjustSplitPointForXmlEntity(text: String, splitAt: Int): Int {
        var adjustedSplitAt = splitAt
        val sub = text.substring(0, adjustedSplitAt)
        if ("&" in sub) {
            val ampersandIndex = sub.lastIndexOf('&')
            val semicolonIndex = sub.indexOf(';', ampersandIndex)
            if (semicolonIndex == -1 || semicolonIndex >= adjustedSplitAt) {
                adjustedSplitAt = ampersandIndex
            }
        }
        return adjustedSplitAt
    }

    fun mkssml(config: TTSConfig, escapedText: String): String {
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice name='${config.voice}'>" +
                "<prosody pitch='${config.pitch}' rate='${config.rate}' volume='${config.volume}'>" +
                escapedText +
                "</prosody>" +
                "</voice>" +
                "</speak>"
    }

    fun dateToString(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun ssmlHeadersPlusData(requestId: String, timestamp: String, ssml: String): String {
        return "X-RequestId:$requestId\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
                "X-Timestamp:${timestamp}Z\r\n" +
                "Path:ssml\r\n\r\n" +
                ssml
    }
    
    fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
