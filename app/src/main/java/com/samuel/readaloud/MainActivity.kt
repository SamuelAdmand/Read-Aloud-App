package com.samuel.readaloud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.ui.MainScreen
import com.samuel.readaloud.ui.theme.ReadAloudTheme
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Restore Python Initialization ---
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Handle "Read Aloud" from system text selection menu
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!text.isNullOrBlank()) {
                val ttsManager = TtsManager.getInstance(applicationContext)
                val prefs = PreferenceManager(applicationContext)

                // Start playback immediately in background
                ttsManager.playText(
                    text = text,
                    voice = prefs.voiceId,
                    title = text.take(50).replace("\n", " ") + "..."
                )
            }
            // Close activity immediately to keep user in their current app
            finish()
            return
        }


        enableEdgeToEdge()
        setContent {
            ReadAloudTheme {
                MainScreen()
            }
        }
    }
}