package com.samuel.readaloud.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = isFabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = isFabMenuExpanded,
                        onCheckedChange = { isFabMenuExpanded = it },
                        containerSize = ToggleFloatingActionButtonDefaults.containerSize(65.dp),
                        containerColor = if (isFabMenuExpanded) {
                            ToggleFloatingActionButtonDefaults.containerColor(
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            ToggleFloatingActionButtonDefaults.containerColor(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    ) {
                        val icon = if (isFabMenuExpanded) Icons.Default.Close else Icons.Default.Add
                        val iconColor = if (isFabMenuExpanded) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = "Toggle Actions",
                            tint = iconColor
                        )
                    }
                }
            ) {
                // Option 1: Type Text
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabMenuExpanded = false
                        // TODO: Open Type Text Dialog
                    },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text("Type Text") }
                )

                // Option 2: Import File
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabMenuExpanded = false
                        // TODO: Open File Picker
                    },
                    icon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                    text = { Text("Import File") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // App Header
            Text(
                text = "Read Aloud",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp)
            )

            // Recently Played Section
            Text(
                text = "Recently Played",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Placeholder List for Recent Items
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(3) { index ->
                    RecentItemCard(index)
                }
            }
        }
    }
}

@Composable
fun RecentItemCard(index: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for icon/image
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .height(48.dp)
                    .width(48.dp)
            ) {}

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Article Title ${index + 1}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "5 min read • Just now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}