package com.samuel.readaloud.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SpeedSelectionSheetContent(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    // Local state to handle slider dragging smoothly
    var sliderValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp, top = 16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Playback Speed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Large Speed Display
        Text(
            text = String.format("%.2fx", sliderValue),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Horizontal Slider
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                // Snap to nearest 0.05
                val snapped = (sliderValue * 20).roundToInt() / 20f
                sliderValue = snapped
                onSpeedSelected(snapped)
            },
            valueRange = 0.25f..3.0f,
            steps = 0, // Continuous sliding
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Fine Control Buttons (+/- 0.05)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    // Decrease by 0.05
                    val newSpeed = ((sliderValue - 0.05f) * 100).roundToInt() / 100f
                    val finalSpeed = newSpeed.coerceAtLeast(0.25f)
                    sliderValue = finalSpeed
                    onSpeedSelected(finalSpeed)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease Speed")
            }

            FilledTonalButton(
                onClick = {
                    // Increase by 0.05
                    val newSpeed = ((sliderValue + 0.05f) * 100).roundToInt() / 100f
                    val finalSpeed = newSpeed.coerceAtMost(3.0f)
                    sliderValue = finalSpeed
                    onSpeedSelected(finalSpeed)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase Speed")
            }
        }
    }
}