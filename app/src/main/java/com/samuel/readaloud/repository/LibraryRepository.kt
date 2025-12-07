package com.samuel.readaloud.repository

import android.content.Context
import com.samuel.readaloud.data.local.AppDatabase
import com.samuel.readaloud.data.local.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LibraryRepository(context: Context) {

    private val articleDao = AppDatabase.getDatabase(context).articleDao()

    val history: Flow<List<ArticleEntity>> = articleDao.getHistory()
    val library: Flow<List<ArticleEntity>> = articleDao.getSavedArticles()

    /**
     * Inserts or updates an article in the history.
     * Checks for duplicates by URL first, then by exact content match.
     */
    suspend fun upsertHistory(
        title: String,
        text: String,
        sourceUrl: String?,
        chunks: List<String> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        // 1. Try finding by URL
        var existing = if (!sourceUrl.isNullOrBlank()) {
            articleDao.getArticleByUrl(sourceUrl)
        } else {
            null
        }

        // 2. Fallback: Try finding by exact content match (Title + Text)
        // This prevents duplicates for Clipboard/Typed text
        if (existing == null) {
            existing = articleDao.findArticleByContent(title, text)
        }

        val entity = existing?.copy(
            lastPlayedAt = System.currentTimeMillis(),
            title = title,
            text = text,
            // Only update chunks if new ones are provided, otherwise keep existing
            chunks = if (chunks.isNotEmpty()) chunks else existing.chunks,
            // If we found a match, we keep its ID. If not, ID is 0 (auto-generate)
            id = existing.id
        ) ?: ArticleEntity(
            title = title,
            text = text,
            sourceUrl = sourceUrl,
            lastPlayedAt = System.currentTimeMillis(),
            chunks = chunks,
            isSavedToLibrary = false
        )

        val id = articleDao.insertArticle(entity)

        // Cleanup old history (Keep top 10)
        val oldIds = articleDao.getOldHistoryIds()
        if (oldIds.isNotEmpty()) {
            articleDao.deleteArticlesByIds(oldIds)
        }

        id
    }

    suspend fun updatePlaybackPosition(id: Long, position: Int) = withContext(Dispatchers.IO) {
        val article = articleDao.getArticleById(id)
        article?.let {
            articleDao.updateArticle(it.copy(playbackPosition = position))
        }
    }

    suspend fun setSavedToLibrary(id: Long, isSaved: Boolean) = withContext(Dispatchers.IO) {
        val article = articleDao.getArticleById(id)
        article?.let {
            articleDao.updateArticle(it.copy(
                isSavedToLibrary = isSaved,
                savedAt = if (isSaved) System.currentTimeMillis() else it.savedAt
            ))
        }
    }

    suspend fun getArticleById(id: Long): ArticleEntity? = withContext(Dispatchers.IO) {
        articleDao.getArticleById(id)
    }

    suspend fun deleteArticle(id: Long) = withContext(Dispatchers.IO) {
        articleDao.deleteArticleById(id)
    }

    suspend fun updateChunks(id: Long, chunks: List<String>) = withContext(Dispatchers.IO) {
        val article = articleDao.getArticleById(id)
        article?.let {
            articleDao.updateArticle(it.copy(chunks = chunks))
        }
    }
}