package com.samuel.readaloud.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the content currently being read or prepared for reading.
 * Separates content storage from UI screens (TypeScreen) and Playback logic (TtsManager).
 */
object ContentRepository {
    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    fun updateContent(newText: String, newTitle: String = "") {
        _text.value = newText
        // If no title provided, generate a snippet from the text
        _title.value = newTitle.ifBlank {
            newText.take(50).replace("\n", " ").trim() + "..."
        }
    }

    fun getCurrentText(): String = _text.value
    fun getCurrentTitle(): String = _title.value
}