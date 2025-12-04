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
import androidx.compose.material3.ExperimentalMaterial3Api
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
    val progress by viewModel.progress.collectAsState()

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
        bottomBar = {
            // Show player controls if there is text or playing
            if (viewModel.textInput.isNotEmpty() || isPlaying) {
                PlayerControls(
                    title = "Typed Text",
                    isPlaying = isPlaying,
                    playbackSpeed = 1.0f, // TODO: Implement speed control
                    voiceName = viewModel.selectedVoiceName,
                    onPlayPause = { viewModel.onPlayPauseClicked() },
                    onNextSection = { /* TODO */ },
                    onPrevSection = { /* TODO */ },
                    onSpeedClick = { /* TODO */ },
                    onVoiceClick = { /* TODO */ },
                    modifier = Modifier
                        .padding(16.dp)
                        .imePadding() // Moves up when keyboard opens
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
                onValueChange = { viewModel.onTextChanged(it) },
                placeholder = { Text("Type or paste text here to listen...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Take up remaining space
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