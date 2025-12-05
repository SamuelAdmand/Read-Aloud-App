package com.samuel.readaloud.ui.type

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.ui.components.PlayerControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeScreen(
    onBackClick: () -> Unit,
    viewModel: TypeViewModel = viewModel()
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val focusManager = LocalFocusManager.current

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

@Composable
fun VoiceSelectionDialog(
    onDismiss: () -> Unit,
    groupedVoices: Map<String, VoiceGroup>, // Updated Type
    pinnedRegions: Set<String>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onVoiceSelected: (Voice) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Voice",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search Language, Region or Voice...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedVoices.forEach { (language, group) ->
                        item {
                            Text(
                                text = language,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, top = 4.dp)
                            )
                        }

                        when (group) {
                            is VoiceGroup.SingleRegion -> {
                                // Case A: Single Region -> List voices directly (Slim rows)
                                items(group.voices) { voice ->
                                    VoiceRow(
                                        voice = voice,
                                        onClick = {
                                            onVoiceSelected(voice)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                            is VoiceGroup.MultiRegion -> {
                                // Case B: Multi Region -> Show Region Cards
                                items(group.regions.keys.toList()) { regionName ->
                                    val voices = group.regions[regionName] ?: emptyList()
                                    val locale = voices.firstOrNull()?.locale ?: ""

                                    RegionGroupCard(
                                        regionName = regionName,
                                        voices = voices,
                                        isPinned = pinnedRegions.contains(locale),
                                        onTogglePin = { onTogglePin(locale) },
                                        onVoiceSelected = {
                                            onVoiceSelected(it)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun RegionGroupCard(
    regionName: String,
    voices: List<Voice>,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onVoiceSelected: (Voice) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column {
            // Header Row (Slimmer Padding)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 12.dp), // Reduced padding
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = regionName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(24.dp) // Smaller icon button area
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Region",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    voices.forEach { voice ->
                        VoiceRow(
                            voice = voice,
                            onClick = { onVoiceSelected(voice) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceRow(
    voice: Voice,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp), // Slim padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = voice.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = voice.gender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.1fx", currentSpeed),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = currentSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..3.0f,
                    steps = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}