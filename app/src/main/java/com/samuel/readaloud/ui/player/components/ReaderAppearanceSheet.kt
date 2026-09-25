package com.samuel.readaloud.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet allowing readers to customize font size, typography style, line spacing,
 * and automatic voice-following scrolling.
 */
@Composable
fun ReaderAppearanceSheet(
    fontSize: Float,
    isSerif: Boolean,
    lineSpacing: Float,
    isAutoScroll: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .navigationBarsPadding()
    ) {
        // Sheet Title
        Text(
            text = "Reader Appearance",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Font Size Control
        Text(
            text = "Text Size",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onFontSizeChange((fontSize - 1f).coerceAtLeast(14f)) },
                        enabled = fontSize > 14f
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease Font Size")
                    }

                    Text(
                        text = "${fontSize.toInt()} sp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { onFontSizeChange((fontSize + 1f).coerceAtMost(28f)) },
                        enabled = fontSize < 28f
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase Font Size")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Typeface Selection (Sans vs Serif)
        Text(
            text = "Font Family",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !isSerif,
                onClick = { onSerifChange(false) },
                label = { Text("Modern Sans", fontFamily = FontFamily.Default) },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = isSerif,
                onClick = { onSerifChange(true) },
                label = { Text("Editorial Serif", fontFamily = FontFamily.Serif) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Line Spacing Selection
        Text(
            text = "Line Spacing",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                "Compact" to 1.35f,
                "Comfortable" to 1.55f,
                "Spacious" to 1.80f
            )

            presets.forEach { (label, value) ->
                val selected = kotlin.math.abs(lineSpacing - value) < 0.05f
                FilterChip(
                    selected = selected,
                    onClick = { onLineSpacingChange(value) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        // 4. Auto-Scroll Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Follow Voice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Automatically scroll to keep spoken text in view",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = isAutoScroll,
                onCheckedChange = onAutoScrollChange
            )
        }
    }
}
