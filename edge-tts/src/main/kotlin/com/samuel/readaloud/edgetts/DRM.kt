package com.samuel.readaloud.edgetts

import okhttp3.Headers
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object DRM {
    private const val WIN_EPOCH = 11644473600L
    private var clockSkewSeconds: Double = 0.0

    fun adjustClockSkew(skewSeconds: Double) {
        clockSkewSeconds += skewSeconds
    }

    fun getUnixTimestamp(): Double {
        return Instant.now().epochSecond + (Instant.now().nano / 1_000_000_000.0) + clockSkewSeconds
    }

    fun generateSecMsGec(): String {
        var ticks = getUnixTimestamp()
        ticks += WIN_EPOCH
        ticks -= ticks % 300
        ticks *= 10_000_000.0 // 100-nanosecond intervals

        val strToHash = String.format(Locale.US, "%.0f%s", ticks, Constants.TRUSTED_CLIENT_TOKEN)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(strToHash.toByteArray(Charsets.US_ASCII))
        return hash.joinToString("") { "%02x".format(it) }.uppercase()
    }

    fun generateMuid(): String {
        return UUID.randomUUID().toString().replace("-", "").uppercase()
    }

    fun headersWithMuid(headers: Map<String, String>): Map<String, String> {
        val muid = generateMuid()
        return headers + mapOf("Cookie" to "muid=$muid;")
    }

    fun handleClientResponseError(headers: Headers?) {
        val serverDate = headers?.get("Date") ?: throw Exception("No server date in headers")
        val serverDateParsed = parseRfc2616Date(serverDate) ?: throw Exception("Failed to parse server date: $serverDate")
        val clientDate = getUnixTimestamp()
        adjustClockSkew(serverDateParsed - clientDate)
    }

    private fun parseRfc2616Date(date: String): Double? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                .withZone(ZoneId.of("UTC"))
            val instant = Instant.from(formatter.parse(date))
            instant.epochSecond + (instant.nano / 1_000_000_000.0)
        } catch (e: Exception) {
            null
        }
    }
}
