package com.samuel.readaloud.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.ui.components.SpeedSelectionSheetContent
import com.samuel.readaloud.ui.components.VoiceSelectionSheetContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            SettingsSectionTitle("Engine")

            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "TTS Provider",
                subtitle = when (viewModel.currentProvider) {
                    PreferenceManager.PROVIDER_EDGE -> "Edge TTS (High Quality)"
                    PreferenceManager.PROVIDER_GOOGLE -> "Google TTS (Standard)"
                    PreferenceManager.PROVIDER_SYSTEM -> "System TTS (Offline/Fast)"
                    else -> "Edge TTS"
                },
                onClick = { showProviderDialog = true }
            )

            if (viewModel.currentProvider == PreferenceManager.PROVIDER_SYSTEM) {
                val currentEngineName = viewModel.systemEngines.find { it.second == viewModel.currentSystemEngine }?.first ?: "System Default"
                SettingsItem(
                    icon = Icons.Default.GraphicEq, // Use a different icon if preferred
                    title = "System Engine",
                    subtitle = currentEngineName,
                    onClick = { showEngineDialog = true }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsSectionTitle("Default Playback")

            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "Default Voice",
                subtitle = viewModel.defaultVoiceName,
                onClick = { showVoiceSheet = true }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            val speed = viewModel.defaultSpeed
            SettingsItem(
                icon = Icons.Default.Speed,
                title = "Default Speed",
                subtitle = String.format("%sx", speed.toString().trimEnd('0').trimEnd('.')),
                onClick = { showSpeedDialog = true }
            )
        }
    }

    // --- Dialogs & Sheets ---

    if (showVoiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVoiceSheet = false },
            sheetState = sheetState
        ) {
            VoiceSelectionSheetContent(
                allVoices = viewModel.voices,
                currentVoiceId = viewModel.defaultVoiceId,
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
                currentSpeed = viewModel.defaultSpeed,
                onSpeedSelected = { speed ->
                    viewModel.onSpeedChanged(speed)
                }
            )
        }
    }
    if (showProviderDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("Select TTS Engine") },
            text = {
                Column {
                    ProviderOption(
                        title = "Edge TTS (High Quality)",
                        isSelected = viewModel.currentProvider == PreferenceManager.PROVIDER_EDGE,
                        onClick = {
                            viewModel.onProviderChanged(PreferenceManager.PROVIDER_EDGE)
                            showProviderDialog = false
                        }
                    )
                    ProviderOption(
                        title = "Google TTS (Standard)",
                        isSelected = viewModel.currentProvider == PreferenceManager.PROVIDER_GOOGLE,
                        onClick = {
                            viewModel.onProviderChanged(PreferenceManager.PROVIDER_GOOGLE)
                            showProviderDialog = false
                        }
                    )
                    ProviderOption(
                        title = "System TTS (Offline/Fast)",
                        isSelected = viewModel.currentProvider == PreferenceManager.PROVIDER_SYSTEM,
                        onClick = {
                            viewModel.onProviderChanged(PreferenceManager.PROVIDER_SYSTEM)
                            showProviderDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showProviderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEngineDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("Select System TTS Engine") },
            text = {
                Column {
                    ProviderOption(
                        title = "System Default",
                        isSelected = viewModel.currentSystemEngine == null,
                        onClick = {
                            viewModel.onSystemEngineChanged(null)
                            showEngineDialog = false
                        }
                    )
                    viewModel.systemEngines.forEach { (label, packageName) ->
                        ProviderOption(
                            title = label,
                            isSelected = viewModel.currentSystemEngine == packageName,
                            onClick = {
                                viewModel.onSystemEngineChanged(packageName)
                                showEngineDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showEngineDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProviderOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}