package com.samuel.readaloud.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.repository.ContentRepository
import com.samuel.readaloud.repository.TtsRepository
import com.samuel.readaloud.ui.components.MarkdownTextPlayer
import com.samuel.readaloud.ui.components.PlayerControls
import com.samuel.readaloud.ui.components.VoiceSelectionSheetContent
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder

private enum class PlayerSheetType { NONE, SPEED, VOICE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val context = LocalContext.current
    val ttsManager = remember { TtsManager.getInstance(context) }
    val repository = remember { TtsRepository() }

    val isPlaying by ttsManager.isPlaying.collectAsState()
    val isLoading by ttsManager.isLoading.collectAsState()
    val currentTitle by ttsManager.currentTitle.collectAsState()
    val currentHighlight by ttsManager.currentHighlight.collectAsState()
    val isSaved by ttsManager.isSaved.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeSheet by remember { mutableStateOf(PlayerSheetType.NONE) }
    val scope = rememberCoroutineScope()

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var allVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var voiceSearchQuery by remember { mutableStateOf("") }
    val sourceText by ContentRepository.text.collectAsState()
    val preferenceManager = remember { PreferenceManager(context) }

    LaunchedEffect(Unit) {
        try {
            allVoices = repository.getVoices()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize")
                    }
                },
                actions = {
                    IconButton(onClick = { ttsManager.toggleLibrary() }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from Library" else "Save to Library"
                        )
                    }
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Text")
                    }
                }
            )
        },
        bottomBar = {
            PlayerControls(
                title = currentTitle,
                isPlaying = isPlaying,
                isLoading = isLoading,
                playbackSpeed = playbackSpeed,
                onPlayPause = { ttsManager.togglePlayPause() },
                onNextSection = { /* TODO */ },
                onPrevSection = { /* TODO */ },
                onSpeedClick = { activeSheet = PlayerSheetType.SPEED },
                onVoiceClick = { activeSheet = PlayerSheetType.VOICE },
                modifier = Modifier.padding(12.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // MarkdownTextPlayer now handles its own scrolling via LazyColumn
            MarkdownTextPlayer(
                rawText = sourceText,
                currentHighlight = currentHighlight,
                modifier = Modifier
                    .weight(1f)
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
                    SpeedBottomSheetContent(
                        currentSpeed = playbackSpeed,
                        onSpeedSelected = { speed ->
                            playbackSpeed = speed
                            ttsManager.setPlaybackSpeed(speed)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = PlayerSheetType.NONE }
                        }
                    )
                }
                PlayerSheetType.VOICE -> {
                    VoiceSelectionSheetContent(
                        allVoices = allVoices,
                        currentVoiceId = preferenceManager.voiceId,
                        onVoiceSelected = { voice ->
                            // Update preference
                            preferenceManager.voiceId = voice.shortName
                            preferenceManager.voiceName = voice.name

                            // Play with new voice
                            ttsManager.playText(
                                text = sourceText,
                                voice = voice.shortName,
                                title = ContentRepository.getCurrentTitle(),
                                sourceUrl = ContentRepository.getCurrentUrl()
                            )
                            scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = PlayerSheetType.NONE }
                        },
                        onDismiss = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = PlayerSheetType.NONE }
                        }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun SpeedBottomSheetContent(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.10f, 1.25f, 1.5f, 2.0f)

    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            text = "Playback Speed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        speeds.forEach { speed ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSpeedSelected(speed) }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${speed}x",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (speed == currentSpeed) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

