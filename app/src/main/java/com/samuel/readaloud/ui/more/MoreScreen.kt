package com.samuel.readaloud.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.ui.components.SpeedSelectionDialog
import com.samuel.readaloud.ui.components.VoiceSelectionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

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
            SettingsSectionTitle("Default Playback")

            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "Default Voice",
                subtitle = viewModel.defaultVoiceName,
                onClick = { showVoiceDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsItem(
                icon = Icons.Default.Speed,
                title = "Default Speed",
                subtitle = String.format("%.1fx", viewModel.defaultSpeed),
                onClick = { showSpeedDialog = true }
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
            currentSpeed = viewModel.defaultSpeed,
            onSpeedChange = viewModel::onSpeedChanged,
            onDismiss = { showSpeedDialog = false }
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