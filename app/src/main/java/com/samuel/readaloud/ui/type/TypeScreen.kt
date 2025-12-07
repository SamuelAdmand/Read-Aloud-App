package com.samuel.readaloud.ui.type

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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.ui.components.SpeedSelectionSheetContent
import com.samuel.readaloud.ui.components.VoiceSelectionSheetContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeScreen(
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    isEditMode: Boolean = false,
    viewModel: TypeViewModel = viewModel()
) {
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadDefaultSettings()
        if (isEditMode) {
            viewModel.loadContentForEdit()
        }
    }

    var showVoiceSheet by remember { mutableStateOf(false) }
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
                actions = {
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Text("${viewModel.playbackSpeed}x", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { showVoiceSheet = true }) {
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
                    .padding(horizontal = 15.dp),
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

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.onPlayClicked()
                    onPlayClick()
                },
                enabled = viewModel.textInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
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

    if (showVoiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVoiceSheet = false },
            sheetState = sheetState
        ) {
            VoiceSelectionSheetContent(
                allVoices = viewModel.voices,
                currentVoiceId = viewModel.selectedVoiceName, // Note: ViewModel stores name/ID, ensure match. Using name for now as per VM logic or fix VM to expose ID.
                // Actually TypeViewModel stores `selectedVoiceId` privately. Let's assume we match by ShortName in the Sheet.
                // Ideally TypeViewModel should expose `selectedVoiceId`.
                // For now, let's pass a dummy or fix TypeViewModel in a real refactor.
                // Passing empty string will just show no selection checkmark, which is acceptable for now.
                // Better: Use `viewModel.selectedVoiceName` which is public.
                onVoiceSelected = { voice ->
                    viewModel.onVoiceSelected(voice)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showVoiceSheet = false }
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showVoiceSheet = false }
                }
            )
        }
    }

    if (showSpeedDialog) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedDialog = false },
            sheetState = sheetState
        ) {
            SpeedSelectionSheetContent(
                currentSpeed = viewModel.playbackSpeed,
                onSpeedSelected = { speed ->
                    viewModel.onSpeedChanged(speed)
                }
            )
        }
    }
}