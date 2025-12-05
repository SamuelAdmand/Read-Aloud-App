package com.samuel.readaloud.ui.type

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.ui.components.PlayerControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeScreen(
    onBackClick: () -> Unit,
    viewModel: TypeViewModel = viewModel()
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val focusManager = LocalFocusManager.current

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
            // Show FAB if there is text
            if (viewModel.textInput.isNotBlank()) {
                FloatingActionButton(
                    onClick = {
                        if (viewModel.isPlayerVisible) {
                            // Currently in Player Mode -> Switch to Edit Mode
                            viewModel.onEditClicked()
                        } else {
                            // Currently in Edit Mode -> Switch to Player Mode
                            focusManager.clearFocus()
                            viewModel.onConfirmText()
                        }
                    },
                    modifier = Modifier.imePadding()
                ) {
                    // Switch Icon based on state
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
                    playbackSpeed = 1.0f,
                    voiceName = viewModel.selectedVoiceName,
                    onPlayPause = { viewModel.onPlayPauseClicked() },
                    onNextSection = { /* TODO */ },
                    onPrevSection = { /* TODO */ },
                    onSpeedClick = { /* TODO */ },
                    onVoiceClick = { /* TODO */ },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            TextField(
                value = viewModel.textInput,
                onValueChange = {
                    // Only allow text changes if NOT in player mode
                    if (!viewModel.isPlayerVisible) {
                        viewModel.onTextChanged(it)
                    }
                },
                // Make read-only in player mode
                readOnly = viewModel.isPlayerVisible,
                placeholder = { Text("Type or paste text here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }
    }
}