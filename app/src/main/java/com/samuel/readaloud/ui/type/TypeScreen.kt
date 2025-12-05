package com.samuel.readaloud.ui.type

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.ui.components.PlayerControls
import com.samuel.readaloud.ui.components.SpeedSelectionDialog
import com.samuel.readaloud.ui.components.VoiceSelectionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeScreen(
    onBackClick: () -> Unit,
    viewModel: TypeViewModel = viewModel()
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.loadDefaultSettings()
    }
    // Dialog States
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Type to Speak") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewModel.textInput.isNotBlank()) {
                FloatingActionButton(
                    onClick = {
                        if (viewModel.isPlayerVisible) {
                            viewModel.onEditClicked()
                        } else {
                            focusManager.clearFocus()
                            viewModel.onConfirmText()
                        }
                    },
                    modifier = Modifier.imePadding()
                ) {
                    Icon(
                        imageVector = if (viewModel.isPlayerVisible) Icons.Default.Edit else Icons.Default.Check,
                        contentDescription = if (viewModel.isPlayerVisible) "Edit Text" else "Play Audio"
                    )
                }
            }
        },
        bottomBar = {
            if (viewModel.isPlayerVisible) {
                PlayerControls(
                    title = "Typed Text",
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    playbackSpeed = viewModel.playbackSpeed,
                    voiceName = viewModel.selectedVoiceName,
                    onPlayPause = { viewModel.onPlayPauseClicked() },
                    onNextSection = { /* TODO */ },
                    onPrevSection = { /* TODO */ },
                    onSpeedClick = { showSpeedDialog = true },
                    onVoiceClick = { showVoiceDialog = true },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            TextField(
                value = viewModel.textInput,
                onValueChange = {
                    if (!viewModel.isPlayerVisible) {
                        viewModel.onTextChanged(it)
                    }
                },
                readOnly = viewModel.isPlayerVisible,
                placeholder = {
                    Text(
                        "Type or paste text here...",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 28.sp
                )
            )
        }
    }

    // --- Dialogs ---

    if (showVoiceDialog) {
        VoiceSelectionDialog(
            onDismiss = { showVoiceDialog = false },
            groupedVoices = viewModel.groupedVoices,
            pinnedRegions = viewModel.pinnedRegions,
            searchQuery = viewModel.searchQuery,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onTogglePin = viewModel::toggleRegionPin,
            onVoiceSelected = viewModel::onVoiceSelected
        )
    }

    if (showSpeedDialog) {
        SpeedSelectionDialog(
            currentSpeed = viewModel.playbackSpeed,
            onSpeedChange = viewModel::onSpeedChanged,
            onDismiss = { showSpeedDialog = false }
        )
    }
}