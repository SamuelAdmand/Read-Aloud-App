package com.samuel.readaloud.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.model.Voice

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Read Aloud Test",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Voice Selection UI
        if (viewModel.isLoadingVoices) {
            CircularProgressIndicator()
            Text("Loading voices...", style = MaterialTheme.typography.bodySmall)
        } else {
            VoiceDropdown(
                voices = viewModel.voices,
                selectedVoice = viewModel.selectedVoice,
                onVoiceSelected = { viewModel.onVoiceSelected(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.textInput,
            onValueChange = { viewModel.onTextChanged(it) },
            label = { Text("Enter text to read") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.speak(context.cacheDir) },
            enabled = !viewModel.isGenerating && viewModel.selectedVoice != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (viewModel.isGenerating) "Generating..." else "Play Audio")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDropdown(
    voices: List<Voice>,
    selectedVoice: Voice?,
    onVoiceSelected: (Voice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // We filter the list to show mostly English voices for this test to avoid a massive list
    // You can remove the .take(50) later to show all
    val displayVoices = remember(voices) {
        voices.filter { it.locale.startsWith("en") }.take(50)
            .ifEmpty { voices.take(50) } // Fallback if no English voices
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedVoice?.shortName ?: "Select Voice",
            onValueChange = {},
            readOnly = true,
            label = { Text("Voice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            displayVoices.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(text = voice.shortName, style = MaterialTheme.typography.bodyMedium)
                            Text(text = "${voice.gender} - ${voice.locale}", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = {
                        onVoiceSelected(voice)
                        expanded = false
                    }
                )
            }
        }
    }
}