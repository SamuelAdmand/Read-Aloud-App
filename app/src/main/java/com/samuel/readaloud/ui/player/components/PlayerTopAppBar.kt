package com.samuel.readaloud.ui.player.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Polished, modern top app bar for the Player screen.
 * Provides controls for minimizing to the mini-player, customizing typography appearance,
 * opening original web source, and editing content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTopAppBar(
    onBackClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    sourceUrl: String? = null,
    domainName: String? = null
) {
    val context = LocalContext.current

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Text(
                text = domainName ?: "Article Reader",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            FilledTonalIconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Minimize Reader"
                )
            }
        },
        actions = {
            Row {
                // Typography & Reader Appearance
                FilledTonalIconButton(
                    onClick = onAppearanceClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TextFormat,
                        contentDescription = "Reader Appearance"
                    )
                }

                // Open source URL in external browser if present
                if (!sourceUrl.isNullOrBlank()) {
                    FilledTonalIconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "Open Original Web Page"
                        )
                    }
                }

                // Edit Content
                FilledTonalIconButton(
                    onClick = onEditClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Text"
                    )
                }
            }
        }
    )
}
