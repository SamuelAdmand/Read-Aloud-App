package com.samuel.readaloud.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.repository.ContentRepository
import com.samuel.readaloud.repository.TtsRepository
import com.samuel.readaloud.ui.components.SpeedSelectionSheetContent
import com.samuel.readaloud.ui.components.VoiceSelectionSheetContent
import com.samuel.readaloud.ui.player.components.ArticleHeaderSection
import com.samuel.readaloud.ui.player.components.MarkdownArticleReader
import com.samuel.readaloud.ui.player.components.PlayerBottomControls
import com.samuel.readaloud.ui.player.components.PlayerTopAppBar
import com.samuel.readaloud.ui.player.components.ReaderAppearanceSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class PlayerSheetType { NONE, SPEED, VOICE, APPEARANCE }

/**
 * Editorial Player UI for reading and listening to extracted Markdown articles.
 * Orchestrates typography adjustments, synchronized sentence speech highlighting,
 * and high-fidelity playback controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsManager = remember { TtsManager.getInstance(context) }
    val repository = remember { TtsRepository(context) }
    val preferenceManager = remember { PreferenceManager(context) }
    val scope = rememberCoroutineScope()

    // Playback state
    val isPlaying by ttsManager.isPlaying.collectAsState()
    val isLoading by ttsManager.isLoading.collectAsState()
    val currentTitle by ttsManager.currentTitle.collectAsState()
    val currentHighlight by ttsManager.currentHighlight.collectAsState()
    val playbackSpeed by ttsManager.currentSpeed.collectAsState()
    val currentVoiceId by ttsManager.currentVoiceId.collectAsState()

    // Content state
    val sourceText by ContentRepository.text.collectAsState()
    val sourceUrl by ContentRepository.url.collectAsState()

    // Reader Appearance state backed by preferences
    var fontSize by remember { mutableFloatStateOf(preferenceManager.readerFontSize) }
    var isSerif by remember { mutableStateOf(preferenceManager.readerSerif) }
    var lineSpacing by remember { mutableFloatStateOf(preferenceManager.readerLineSpacing) }
    var isAutoScroll by remember { mutableStateOf(preferenceManager.readerAutoScroll) }

    // Bottom Sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeSheet by remember { mutableStateOf(PlayerSheetType.NONE) }
    var allVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }

    // Reading statistics
    val wordCount = remember(sourceText) {
        if (sourceText.isBlank()) 0
        else sourceText.split(Regex("\\s+")).count { it.isNotBlank() }
    }
    val estimatedMinutes = remember(wordCount) {
        maxOf(1, (wordCount / 180.0).roundToInt())
    }
    // Voice name resolution
    val currentVoiceName = remember(allVoices, currentVoiceId) {
        allVoices.find { it.shortName == currentVoiceId }?.name ?: preferenceManager.voiceName
    }

    LaunchedEffect(Unit) {
        try {
            val provider = preferenceManager.ttsProvider
            allVoices = repository.getVoices(provider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PlayerTopAppBar(
                onBackClick = onBackClick,
                onAppearanceClick = { activeSheet = PlayerSheetType.APPEARANCE },
                onEditClick = onEditClick,
                sourceUrl = sourceUrl
            )
        },
        bottomBar = {
            PlayerBottomControls(
                isPlaying = isPlaying,
                isLoading = isLoading,
                playbackSpeed = playbackSpeed,
                voiceName = currentVoiceName,
                onPlayPause = { ttsManager.togglePlayPause() },
                onNextSection = { ttsManager.skipNext() },
                onPrevSection = { ttsManager.skipPrevious() },
                onSpeedClick = { activeSheet = PlayerSheetType.SPEED },
                onVoiceClick = { activeSheet = PlayerSheetType.VOICE },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            MarkdownArticleReader(
                rawText = sourceText,
                currentHighlight = currentHighlight,
                onTextClick = { index -> ttsManager.seekToLocation(index) },
                fontSize = fontSize,
                isSerif = isSerif,
                lineHeightMultiplier = lineSpacing,
                isAutoScrollEnabled = isAutoScroll,
                headerContent = {
                    ArticleHeaderSection(
                        title = currentTitle,
                        sourceUrl = sourceUrl,
                        wordCount = wordCount,
                        estimatedMinutes = estimatedMinutes,
                        isSerif = isSerif
                    )
                }
            )
        }
    }

    if (activeSheet != PlayerSheetType.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = PlayerSheetType.NONE },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            when (activeSheet) {
                PlayerSheetType.SPEED -> {
                    SpeedSelectionSheetContent(
                        currentSpeed = playbackSpeed,
                        onSpeedSelected = { speed ->
                            ttsManager.setPlaybackSpeed(speed)
                        }
                    )
                }
                PlayerSheetType.VOICE -> {
                    VoiceSelectionSheetContent(
                        allVoices = allVoices,
                        currentVoiceId = currentVoiceId,
                        onVoiceSelected = { voice ->
                            ttsManager.updateVoice(voice.shortName)
                            preferenceManager.saveVoiceForProvider(
                                preferenceManager.ttsProvider,
                                voice.shortName,
                                voice.name
                            )
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                activeSheet = PlayerSheetType.NONE
                            }
                        },
                        onDismiss = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                activeSheet = PlayerSheetType.NONE
                            }
                        }
                    )
                }
                PlayerSheetType.APPEARANCE -> {
                    ReaderAppearanceSheet(
                        fontSize = fontSize,
                        isSerif = isSerif,
                        lineSpacing = lineSpacing,
                        isAutoScroll = isAutoScroll,
                        onFontSizeChange = { newSize ->
                            fontSize = newSize
                            preferenceManager.readerFontSize = newSize
                        },
                        onSerifChange = { newSerif ->
                            isSerif = newSerif
                            preferenceManager.readerSerif = newSerif
                        },
                        onLineSpacingChange = { newSpacing ->
                            lineSpacing = newSpacing
                            preferenceManager.readerLineSpacing = newSpacing
                        },
                        onAutoScrollChange = { newAutoScroll ->
                            isAutoScroll = newAutoScroll
                            preferenceManager.readerAutoScroll = newAutoScroll
                        }
                    )
                }
                else -> {}
            }
        }
    }
}