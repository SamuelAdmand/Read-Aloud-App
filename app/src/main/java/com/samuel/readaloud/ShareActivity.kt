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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.domain.TextChunker
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.repository.LibraryRepository
import com.samuel.readaloud.repository.UrlRepository
import com.samuel.readaloud.ui.theme.ReadAloudTheme
import com.samuel.readaloud.worker.DownloadWorker
import java.util.regex.Pattern
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                // State to control flow
                var action by remember { mutableStateOf(ShareAction.NONE) }
                var showDialog by remember { mutableStateOf(true) }

                val urlRepository = remember { UrlRepository(applicationContext) }
                val libraryRepository = remember { LibraryRepository(applicationContext) }
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
                                    showDialog = false
                                    action = ShareAction.PLAY
                                }
                            ) {
                                Text("Play")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showDialog = false
                                    action = ShareAction.SAVE
                                }
                            ) {
                                Text("Save to Library")
                            }
                        }
                    )
                }

                if (action != ShareAction.NONE) {
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

                    // Perform extraction and action
                    LaunchedEffect(action) {
                        val result = urlRepository.extractArticle(sharedUrl)
                        result.fold(
                            onSuccess = { article ->
                                if (action == ShareAction.PLAY) {
                                    ttsManager.playText(
                                        text = article.text,
                                        voice = preferenceManager.voiceId,
                                        title = article.title,
                                        sourceUrl = sharedUrl
                                    )
                                } else if (action == ShareAction.SAVE) {
                                    // 1. Chunk the text for offline storage
                                    val chunks = TextChunker.chunkText(article.text)

                                    // 2. Save to database
                                    val id = libraryRepository.upsertHistory(
                                        title = article.title,
                                        text = article.text,
                                        sourceUrl = sharedUrl,
                                        chunks = chunks
                                    )

                                    // 3. Mark as saved
                                    libraryRepository.setSavedToLibrary(id, true)

                                    // 4. Trigger Background Download
                                    val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                                        .setInputData(
                                            workDataOf(
                                                "articleId" to id,
                                                "voiceName" to preferenceManager.voiceId
                                            )
                                        )
                                        .build()
                                    WorkManager.getInstance(applicationContext).enqueue(workRequest)

                                    Toast.makeText(
                                        this@ShareActivity,
                                        "Saved to Library",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                finish()
                            },
                            onFailure = { e ->
                                Toast.makeText(
                                    this@ShareActivity,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private enum class ShareAction { NONE, PLAY, SAVE }
}