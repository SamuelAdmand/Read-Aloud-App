package com.samuel.readaloud.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val sourceUrl: String? = null,

    // true = Library (Saved), false = History (Recent)
    val isSavedToLibrary: Boolean = false,

    // Timestamps for sorting
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val savedAt: Long? = null,

    // Resume position (character index)
    val playbackPosition: Int = 0,

    // JSON list of text chunks.
    // Essential for mapping offline audio files to text segments consistently.
    val chunks: List<String> = emptyList()
)