package com.samuel.readaloud.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.samuel.readaloud.domain.HighlightRange
import com.samuel.readaloud.ui.player.components.MarkdownArticleReader

/**
 * Backward compatibility wrapper delegating to [MarkdownArticleReader].
 */
@Deprecated("Use MarkdownArticleReader in com.samuel.readaloud.ui.player.components instead")
@Composable
fun MarkdownTextPlayer(
    rawText: String,
    currentHighlight: HighlightRange?,
    onTextClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    MarkdownArticleReader(
        rawText = rawText,
        currentHighlight = currentHighlight,
        onTextClick = onTextClick,
        modifier = modifier
    )
}