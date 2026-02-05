package com.samuel.readaloud.edgetts

import java.util.Locale
import java.util.concurrent.TimeUnit

data class Subtitle(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val content: String
) {
    fun toSrt(): String {
        return String.format(
            Locale.US,
            "%d\n%s --> %s\n%s\n\n",
            index,
            formatTimestamp(startMs),
            formatTimestamp(endMs),
            content
        )
    }

    private fun formatTimestamp(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        val msecs = millis % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, msecs)
    }
}

object SrtComposer {
    fun compose(subtitles: List<Subtitle>): String {
        return subtitles.sortedBy { it.startMs }
            .mapIndexed { index, subtitle ->
                subtitle.copy(index = index + 1).toSrt()
            }.joinToString("")
    }
}
