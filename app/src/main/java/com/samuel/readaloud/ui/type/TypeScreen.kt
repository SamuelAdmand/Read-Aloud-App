package com.samuel.readaloud.ui.type

import android.R.attr.end
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.ui.components.SpeedSelectionDialog
import com.samuel.readaloud.ui.components.VoiceSelectionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeScreen(
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit, // New navigation callback
    viewModel: TypeViewModel = viewModel()
) {
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
                },
                // Add settings buttons here for easy access while typing
                actions = {
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Text("${viewModel.playbackSpeed}x", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { showVoiceDialog = true }) {
                        // Placeholder icon or text for voice
                        Text("Voice", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Text Input Area
            TextField(
                value = viewModel.textInput,
                onValueChange = { viewModel.onTextChanged(it) },
                placeholder = {
                    Text(
                        "Type or paste text here...",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
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

            // Large Bottom Play Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.onPlayClicked()
                    onPlayClick()
                },
                enabled = viewModel.textInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(start = 8.dp, end = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            ) {
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (viewModel.textInput.isNotBlank())
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
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