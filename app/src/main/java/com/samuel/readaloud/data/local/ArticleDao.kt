package com.samuel.readaloud.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    // --- Library (Saved) ---
    @Query("SELECT * FROM articles WHERE isSavedToLibrary = 1 ORDER BY savedAt DESC")
    fun getSavedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isSavedToLibrary = 1 AND sourceUrl = :url LIMIT 1")
    suspend fun getSavedArticleByUrl(url: String): ArticleEntity?

    // --- History (Recent) ---
    @Query("SELECT * FROM articles ORDER BY lastPlayedAt DESC LIMIT 10")
    fun getHistory(): Flow<List<ArticleEntity>>

    @Query("SELECT id FROM articles WHERE isSavedToLibrary = 0 AND id NOT IN (SELECT id FROM articles ORDER BY lastPlayedAt DESC LIMIT 10)")
    suspend fun getOldHistoryIds(): List<Long>

    // --- General ---
    @Query("SELECT * FROM articles WHERE title = :title AND text = :text LIMIT 1")
    suspend fun findArticleByContent(title: String, text: String): ArticleEntity?
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE sourceUrl = :url LIMIT 1")
    suspend fun getArticleByUrl(url: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity): Long

    // CHANGED: Return Int (rows affected) to fix KSP error
    @Update
    suspend fun updateArticle(article: ArticleEntity): Int

    // CHANGED: Return Int (rows affected) to fix KSP error
    @Delete
    suspend fun deleteArticle(article: ArticleEntity): Int

    // CHANGED: Return Int (rows affected) to fix KSP error
    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteArticleById(id: Long): Int

    // CHANGED: Return Int (rows affected) to fix KSP error
    @Query("DELETE FROM articles WHERE id IN (:ids)")
    suspend fun deleteArticlesByIds(ids: List<Long>): Int
}