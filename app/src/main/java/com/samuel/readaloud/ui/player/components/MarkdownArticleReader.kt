package com.samuel.readaloud.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuel.readaloud.domain.HighlightRange
import kotlinx.coroutines.launch

/**
 * Publication-grade Markdown article reader with real-time synchronized speech highlighting,
 * tap-to-seek playback, and smooth auto-scrolling.
 */
@Composable
fun MarkdownArticleReader(
    rawText: String,
    currentHighlight: HighlightRange?,
    onTextClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Float = 18f,
    isSerif: Boolean = false,
    lineHeightMultiplier: Float = 1.55f,
    isAutoScrollEnabled: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null
) {
    val blocks = remember(rawText) {
        MarkdownParser.splitIntoBlocks(rawText)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var userScrolledAway by remember { mutableStateOf(false) }
    var activeBlockIndex by remember { mutableIntStateOf(-1) }

    val fontFamily = if (isSerif) FontFamily.Serif else FontFamily.Default
    val baseFontSize = fontSize.sp
    val baseLineHeight = (fontSize * lineHeightMultiplier).sp

    // Observe whether active reading item is currently on screen
    val isReadingItemVisibleOnScreen by remember {
        derivedStateOf {
            if (activeBlockIndex == -1) true
            else {
                val targetIndex = if (headerContent != null) activeBlockIndex + 1 else activeBlockIndex
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val item = visibleItems.find { it.index == targetIndex }
                if (item != null) {
                    val vpHeight = listState.layoutInfo.viewportSize.height
                    if (vpHeight > 0) {
                        val itemTop = item.offset
                        val itemBottom = item.offset + item.size
                        itemBottom > (vpHeight * 0.10f) && itemTop < (vpHeight * 0.85f)
                    } else true
                } else false
            }
        }
    }

    // Determine currently active block index from highlight range and center it
    LaunchedEffect(currentHighlight, blocks) {
        currentHighlight?.let { highlight ->
            val index = blocks.indexOfFirst { block ->
                val blockEnd = block.globalStartIndex + block.rawText.length + 1
                highlight.start in block.globalStartIndex..blockEnd
            }
            if (index != -1) {
                activeBlockIndex = index
                if (isAutoScrollEnabled && !userScrolledAway) {
                    val target = if (headerContent != null) index + 1 else index
                    val vpHeight = listState.layoutInfo.viewportSize.height
                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == target }
                    val isComfortablyCentered = itemInfo != null &&
                        itemInfo.offset >= (vpHeight * 0.15f).toInt() &&
                        (itemInfo.offset + itemInfo.size) <= (vpHeight * 0.80f).toInt()

                    if (!isComfortablyCentered && vpHeight > 0) {
                        val itemHeight = itemInfo?.size ?: 200
                        val centerOffset = -((vpHeight - itemHeight) / 2).coerceAtLeast(0)
                        listState.animateScrollToItem(target, centerOffset)
                    }
                }
            }
        }
    }

    // Update userScrolledAway dynamically as user scrolls
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val target = if (headerContent != null) activeBlockIndex + 1 else activeBlockIndex
            val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == target }
            val vpHeight = listState.layoutInfo.viewportSize.height
            val isTargetVisible = itemInfo != null && vpHeight > 0 &&
                (itemInfo.offset + itemInfo.size) > (vpHeight * 0.10f) &&
                itemInfo.offset < (vpHeight * 0.85f)

            if (!isTargetVisible && activeBlockIndex != -1) {
                userScrolledAway = true
            } else if (isTargetVisible) {
                userScrolledAway = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp, start = 20.dp, end = 20.dp)
        ) {
            if (headerContent != null) {
                item(key = "article_header") {
                    headerContent()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                val isBlockActive = index == activeBlockIndex

                MarkdownBlockRenderer(
                    block = block,
                    currentHighlight = currentHighlight,
                    baseFontSize = baseFontSize,
                    baseLineHeight = baseLineHeight,
                    fontFamily = fontFamily,
                    isBlockActive = isBlockActive,
                    onTextClick = onTextClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Floating "Resume Auto-Scroll" button when user scrolls away from active reading spot
        AnimatedVisibility(
            visible = userScrolledAway && !isReadingItemVisibleOnScreen && activeBlockIndex != -1,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 120.dp, end = 20.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    userScrolledAway = false
                    scope.launch {
                        val target = if (headerContent != null) activeBlockIndex + 1 else activeBlockIndex
                        val vpHeight = listState.layoutInfo.viewportSize.height
                        val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == target }
                        val itemHeight = itemInfo?.size ?: 200
                        val centerOffset = if (vpHeight > 0) -((vpHeight - itemHeight) / 2).coerceAtLeast(0) else 0
                        listState.animateScrollToItem(target, centerOffset)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = "Jump to reading",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Resume",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlockRenderer(
    block: MarkdownParsedBlock,
    currentHighlight: HighlightRange?,
    baseFontSize: TextUnit,
    baseLineHeight: TextUnit,
    fontFamily: FontFamily,
    isBlockActive: Boolean,
    onTextClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when (block.type) {
        BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4, BlockType.H5, BlockType.H6 -> {
            HeadingBlockView(
                block = block,
                currentHighlight = currentHighlight,
                baseFontSize = baseFontSize,
                fontFamily = fontFamily,
                onTextClick = onTextClick,
                modifier = modifier
            )
        }
        BlockType.BLOCKQUOTE -> {
            BlockquoteView(
                block = block,
                currentHighlight = currentHighlight,
                baseFontSize = baseFontSize,
                baseLineHeight = baseLineHeight,
                fontFamily = fontFamily,
                onTextClick = onTextClick,
                modifier = modifier
            )
        }
        BlockType.UNORDERED_LIST, BlockType.ORDERED_LIST -> {
            ListView(
                block = block,
                currentHighlight = currentHighlight,
                baseFontSize = baseFontSize,
                baseLineHeight = baseLineHeight,
                fontFamily = fontFamily,
                onTextClick = onTextClick,
                modifier = modifier
            )
        }
        BlockType.CODE_BLOCK -> {
            CodeBlockView(
                block = block,
                baseFontSize = baseFontSize,
                modifier = modifier
            )
        }
        BlockType.THEMATIC_BREAK -> {
            ThematicBreakView(modifier = modifier)
        }
        BlockType.PARAGRAPH -> {
            ParagraphView(
                block = block,
                currentHighlight = currentHighlight,
                baseFontSize = baseFontSize,
                baseLineHeight = baseLineHeight,
                fontFamily = fontFamily,
                onTextClick = onTextClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ParagraphView(
    block: MarkdownParsedBlock,
    currentHighlight: HighlightRange?,
    baseFontSize: TextUnit,
    baseLineHeight: TextUnit,
    fontFamily: FontFamily,
    onTextClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    val renderResult = remember(block.rawText, baseFontSize, linkColor) {
        MarkdownParser.parseInlineFormatting(
            rawText = block.rawText,
            baseFontSize = baseFontSize,
            linkColor = linkColor,
            codeBackgroundColor = codeBg,
            codeTextColor = codeTextColor
        )
    }

    val highlightedText by remember(renderResult, currentHighlight, highlightBg, highlightTextColor) {
        derivedStateOf {
            applyHighlightSpan(
                renderResult = renderResult,
                blockStart = block.globalStartIndex,
                blockLength = block.rawText.length,
                currentHighlight = currentHighlight,
                highlightBg = highlightBg,
                highlightTextColor = highlightTextColor
            )
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = highlightedText,
        fontSize = baseFontSize,
        lineHeight = baseLineHeight,
        fontFamily = fontFamily,
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { layoutResult = it },
        modifier = modifier
            .padding(vertical = 8.dp)
            .pointerInput(block.rawText) {
                detectTapGestures { pos ->
                    layoutResult?.let { layout ->
                        val renderedOffset = layout.getOffsetForPosition(pos)
                        val rawOffset = renderResult.mapRenderedToRaw(renderedOffset)
                        onTextClick(block.globalStartIndex + rawOffset)
                    }
                }
            }
    )
}

@Composable
private fun HeadingBlockView(
    block: MarkdownParsedBlock,
    currentHighlight: HighlightRange?,
    baseFontSize: TextUnit,
    fontFamily: FontFamily,
    onTextClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val (multiplier, weight, topPad, bottomPad) = when (block.type) {
        BlockType.H1 -> Quadruple(1.55f, FontWeight.ExtraBold, 24.dp, 10.dp)
        BlockType.H2 -> Quadruple(1.35f, FontWeight.Bold, 20.dp, 8.dp)
        BlockType.H3 -> Quadruple(1.20f, FontWeight.Bold, 16.dp, 6.dp)
        BlockType.H4 -> Quadruple(1.10f, FontWeight.SemiBold, 14.dp, 4.dp)
        else -> Quadruple(1.05f, FontWeight.SemiBold, 12.dp, 4.dp)
    }

    val headingSize = (baseFontSize.value * multiplier).sp
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    val renderResult = remember(block.contentText, headingSize) {
        MarkdownParser.parseInlineFormatting(
            rawText = block.contentText,
            baseFontSize = headingSize,
            linkColor = linkColor,
            codeBackgroundColor = codeBg,
            codeTextColor = codeTextColor
        )
    }

    val highlightedText by remember(renderResult, currentHighlight, highlightBg, highlightTextColor) {
        derivedStateOf {
            applyHighlightSpan(
                renderResult = renderResult,
                blockStart = block.globalStartIndex,
                blockLength = block.rawText.length,
                currentHighlight = currentHighlight,
                highlightBg = highlightBg,
                highlightTextColor = highlightTextColor
            )
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = highlightedText,
        fontSize = headingSize,
        fontWeight = weight,
        lineHeight = (headingSize.value * 1.3f).sp,
        fontFamily = fontFamily,
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { layoutResult = it },
        modifier = modifier
            .padding(top = topPad, bottom = bottomPad)
            .pointerInput(block.contentText) {
                detectTapGestures { pos ->
                    layoutResult?.let { layout ->
                        val renderedOffset = layout.getOffsetForPosition(pos)
                        val rawOffset = renderResult.mapRenderedToRaw(renderedOffset)
                        onTextClick(block.globalStartIndex + rawOffset)
                    }
                }
            }
    )
}

@Composable
private fun BlockquoteView(
    block: MarkdownParsedBlock,
    currentHighlight: HighlightRange?,
    baseFontSize: TextUnit,
    baseLineHeight: TextUnit,
    fontFamily: FontFamily,
    onTextClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val renderResult = remember(block.contentText, baseFontSize) {
        MarkdownParser.parseInlineFormatting(
            rawText = block.contentText,
            baseFontSize = baseFontSize,
            linkColor = linkColor,
            codeBackgroundColor = codeBg,
            codeTextColor = codeTextColor
        )
    }

    val highlightedText by remember(renderResult, currentHighlight, highlightBg, highlightTextColor) {
        derivedStateOf {
            applyHighlightSpan(
                renderResult = renderResult,
                blockStart = block.globalStartIndex,
                blockLength = block.rawText.length,
                currentHighlight = currentHighlight,
                highlightBg = highlightBg,
                highlightTextColor = highlightTextColor
            )
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Card(
        modifier = modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(baseLineHeight.value.dp * 1.5f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = highlightedText,
                fontSize = baseFontSize,
                lineHeight = baseLineHeight,
                fontFamily = fontFamily,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(block.contentText) {
                        detectTapGestures { pos ->
                            layoutResult?.let { layout ->
                                val renderedOffset = layout.getOffsetForPosition(pos)
                                val rawOffset = renderResult.mapRenderedToRaw(renderedOffset)
                                onTextClick(block.globalStartIndex + rawOffset)
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun ListView(
    block: MarkdownParsedBlock,
    currentHighlight: HighlightRange?,
    baseFontSize: TextUnit,
    baseLineHeight: TextUnit,
    fontFamily: FontFamily,
    onTextClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        block.listItems.forEachIndexed { itemIdx, itemText ->
            val renderResult = remember(itemText, baseFontSize, linkColor) {
                MarkdownParser.parseInlineFormatting(
                    rawText = itemText,
                    baseFontSize = baseFontSize,
                    linkColor = linkColor,
                    codeBackgroundColor = codeBg,
                    codeTextColor = codeTextColor
                )
            }

            val highlightedText by remember(renderResult, currentHighlight, highlightBg, highlightTextColor) {
                derivedStateOf {
                    applyHighlightSpan(
                        renderResult = renderResult,
                        blockStart = block.globalStartIndex,
                        blockLength = block.rawText.length,
                        currentHighlight = currentHighlight,
                        highlightBg = highlightBg,
                        highlightTextColor = highlightTextColor
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (block.type == BlockType.ORDERED_LIST) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(top = 2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${itemIdx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(top = 9.dp, start = 4.dp, end = 4.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = highlightedText,
                    fontSize = baseFontSize,
                    lineHeight = baseLineHeight,
                    fontFamily = fontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CodeBlockView(
    block: MarkdownParsedBlock,
    baseFontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        )
    ) {
        Box(
            modifier = Modifier
                .padding(14.dp)
                .horizontalScroll(scrollState)
        ) {
            Text(
                text = block.contentText,
                fontFamily = FontFamily.Monospace,
                fontSize = (baseFontSize.value * 0.88f).sp,
                lineHeight = (baseFontSize.value * 1.3f).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ThematicBreakView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.85f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

private fun applyHighlightSpan(
    renderResult: RenderedBlockResult,
    blockStart: Int,
    blockLength: Int,
    currentHighlight: HighlightRange?,
    highlightBg: Color,
    highlightTextColor: Color
): AnnotatedString {
    val base = renderResult.annotatedString
    if (currentHighlight == null) return base

    val hStart = currentHighlight.start
    val hEnd = currentHighlight.end
    val blockEnd = blockStart + blockLength

    if (hEnd > blockStart && hStart < blockEnd) {
        val localStart = (hStart - blockStart).coerceAtLeast(0)
        val localEnd = (hEnd - blockStart).coerceAtMost(blockLength)

        var mStart = renderResult.mapRawToRendered(localStart)
        var mEnd = renderResult.mapRawToRendered(localEnd)

        val text = base.text

        // Snap mStart backward to start of word
        while (mStart > 0 && !text[mStart - 1].isWhitespace()) {
            val prev = text[mStart - 1]
            if (prev in listOf('.', '!', '?')) break
            mStart--
        }

        // Snap mEnd forward to end of word
        while (mEnd < text.length && !text[mEnd].isWhitespace()) {
            mEnd++
        }

        // Expand mEnd to include immediately attached closing brackets, quotes, and punctuation
        val trailingPunct = setOf('.', ',', '!', '?', ';', ':', ')', ']', '}', '"', '”', '’', '\'', '—', '-')
        while (mEnd < text.length && text[mEnd] in trailingPunct) {
            mEnd++
        }

        if (mStart in 0 until mEnd && mEnd <= text.length) {
            val builder = AnnotatedString.Builder(base)
            builder.addStyle(
                SpanStyle(
                    background = highlightBg,
                    color = highlightTextColor,
                    fontWeight = FontWeight.SemiBold
                ),
                mStart,
                mEnd
            )
            return builder.toAnnotatedString()
        }
    }
    return base
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
