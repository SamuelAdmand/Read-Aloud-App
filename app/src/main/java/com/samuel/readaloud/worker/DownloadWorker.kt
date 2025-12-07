package com.samuel.readaloud.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.samuel.readaloud.domain.TextChunker
import com.samuel.readaloud.repository.LibraryRepository
import com.samuel.readaloud.repository.TtsRepository
import java.io.File

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val libraryRepository = LibraryRepository(context)
    private val ttsRepository = TtsRepository()

    override suspend fun doWork(): Result {
        val articleId = inputData.getLong("articleId", -1L)
        val voiceName = inputData.getString("voiceName") ?: "en-US-AriaNeural"

        if (articleId == -1L) {
            return Result.failure()
        }

        val article = libraryRepository.getArticleById(articleId)
        if (article == null) {
            return Result.failure()
        }

        val chunks = article.chunks
        if (chunks.isEmpty()) {
            return Result.success()
        }

        val audioDir = File(context.filesDir, "audio_cache").apply { mkdirs() }
        var successCount = 0

        try {
            for ((index, text) in chunks.withIndex()) {
                if (isStopped) break

                val baseName = "article_${articleId}_chunk_$index"
                val outputFile = File(audioDir, "$baseName.mp3")
                val srtFile = File(audioDir, "$baseName.mp3.srt")

                // Skip if already exists
                if (outputFile.exists() && outputFile.length() > 0 && srtFile.exists()) {
                    successCount++
                    continue
                }

                val ttsText = TextChunker.sanitizeMarkdownForTts(text)

                // Update progress
                setProgress(workDataOf("progress" to (index.toFloat() / chunks.size) * 100))

                val result = ttsRepository.generateAudio(ttsText, voiceName, outputFile)

                if (result.isSuccess) {
                    successCount++
                } else {
                    Log.e("DownloadWorker", "Failed to generate chunk $index for article $articleId")
                    // We continue trying other chunks, or we could return Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Error during download", e)
            return Result.retry()
        }

        return if (successCount == chunks.size) {
            Result.success()
        } else {
            // If some chunks failed, we might want to retry later
            Result.retry()
        }
    }
}