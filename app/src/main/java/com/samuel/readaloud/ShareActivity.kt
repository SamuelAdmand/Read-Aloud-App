package com.samuel.readaloud

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.repository.UrlRepository
import com.samuel.readaloud.ui.theme.ReadAloudTheme
import java.util.regex.Pattern

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure Python is started (needed if app was killed)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        // Extract URL
        val matcher = Pattern.compile("https?://\\S+").matcher(sharedText)
        val sharedUrl = if (matcher.find()) matcher.group() else sharedText

        if (sharedUrl.isBlank()) {
            finish()
            return
        }

        setContent {
            ReadAloudTheme {
                // State to control dialog visibility
                var showDialog by remember { mutableStateOf(true) }
                var isExtracting by remember { mutableStateOf(false) }

                val urlRepository = remember { UrlRepository() }
                val ttsManager = remember { TtsManager.getInstance(applicationContext) }
                val preferenceManager = remember { PreferenceManager(applicationContext) }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showDialog = false
                            finish()
                        },
                        title = { Text("Import Article") },
                        text = { Text("What would you like to do with this link?\n\n$sharedUrl") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    isExtracting = true
                                    showDialog = false
                                }
                            ) {
                                Text("Play")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    Toast.makeText(this@ShareActivity, "Saved to Library (Coming Soon)", Toast.LENGTH_SHORT).show()
                                    showDialog = false
                                    finish()
                                }
                            ) {
                                Text("Save to Library")
                            }
                        }
                    )
                }

                if (isExtracting) {
                    // Show a simple loading spinner
                    Dialog(onDismissRequest = { }) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(120.dp)
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    // Perform extraction
                    LaunchedEffect(Unit) {
                        val result = urlRepository.extractArticle(sharedUrl)
                        result.fold(
                            onSuccess = { article ->
                                ttsManager.playText(
                                    text = article.text,
                                    voice = preferenceManager.voiceId,
                                    title = article.title
                                )
                                finish() // Close dialog, audio continues in background
                            },
                            onFailure = { e ->
                                Toast.makeText(this@ShareActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}