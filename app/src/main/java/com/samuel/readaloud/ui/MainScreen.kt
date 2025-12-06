package com.samuel.readaloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.samuel.readaloud.ui.home.HomeScreen
import com.samuel.readaloud.ui.library.LibraryScreen
import com.samuel.readaloud.ui.more.MoreScreen
import com.samuel.readaloud.ui.player.PlayerScreen
import com.samuel.readaloud.ui.type.TypeScreen
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.repository.UrlRepository
import com.samuel.readaloud.ui.components.UrlInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val urlRepository = remember { UrlRepository() }
    val ttsManager = remember { TtsManager.getInstance(context) }

    var showUrlDialog by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    // Get current route to determine visibility of Bottom Bar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    Scaffold(
        bottomBar = {
            // HIDE Bottom Bar on 'type_text' and 'player' screens
            if (currentRoute != "type_text" && currentRoute != "player") {
                NavigationBar {
                    // Spacer to push items to center
                    Spacer(modifier = Modifier.weight(0.3f))

                    // 1. Home Item
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentDestination?.hierarchy?.any { it.route == "home" } == true,
                        onClick = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // 2. Center "Add" Button (Opens Bottom Sheet)
                    NavigationBarItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Create",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        },
                        label = { /* No label for the center button */ },
                        selected = false, // Never selected
                        onClick = { showBottomSheet = true },
                        modifier = Modifier.weight(1f)
                    )

                    // 3. Library Item
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Bookmarks, contentDescription = "Library") },
                        label = { Text("Library") },
                        selected = currentDestination?.hierarchy?.any { it.route == "library" } == true,
                        onClick = {
                            navController.navigate("library") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Spacer to push items to center
                    Spacer(modifier = Modifier.weight(0.3f))
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable("home") {
                HomeScreen(
                    onTypeTextClick = { navController.navigate("type_text") }
                )
            }
            composable("library") { LibraryScreen() }
            composable("more") { MoreScreen() }

            composable("type_text") {
                TypeScreen(
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = {
                        // Navigate to Player when play is clicked
                        navController.navigate("player")
                    }
                )
            }

            composable("player") {
                PlayerScreen(
                    onBackClick = {
                        // "Minimize" behavior: Navigate to Home to let user browse while listening
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onEditClick = {
                        // Go back to the Type screen to edit text
                        navController.popBackStack()
                    }
                )
            }
        }

        // Bottom Sheet for "Create" Options
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Create",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )

                    // Menu Options
                    CreateOptionItem(
                        icon = Icons.Filled.CameraAlt,
                        label = "Scan Text",
                        onClick = {
                            // Placeholder
                            scope.launch { sheetState.hide() }.invokeOnCompletion { showBottomSheet = false }
                        }
                    )

                    CreateOptionItem(
                        icon = Icons.Filled.Edit,
                        label = "Type or paste Text",
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                                navController.navigate("type_text")
                            }
                        }
                    )

                    CreateOptionItem(
                        icon = Icons.Filled.Link,
                        label = "Paste link",
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                                showUrlDialog = true
                            }
                        }
                    )

                    CreateOptionItem(
                        icon = Icons.Filled.FileOpen,
                        label = "Import File",
                        onClick = {
                            // Placeholder
                            scope.launch { sheetState.hide() }.invokeOnCompletion { showBottomSheet = false }
                        }
                    )

                    // Settings (Moved here)
                    CreateOptionItem(
                        icon = Icons.Filled.Settings,
                        label = "Settings",
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                                navController.navigate("more")
                            }
                        }
                    )
                }
            }
        }
    }
    if (showUrlDialog) {
        UrlInputDialog(
            onDismissRequest = { showUrlDialog = false },
            onConfirm = { url ->
                showUrlDialog = false
                isExtracting = true

                scope.launch {
                    val result = urlRepository.extractArticle(url)
                    isExtracting = false

                    result.fold(
                        onSuccess = { article ->
                            ttsManager.importText(article.text, article.title)
                            navController.navigate("type_text")
                        },
                        onFailure = { e ->
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        )
    }

    if (isExtracting) {
        // Simple overlay loading indicator
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(16.dp), // Adjust position using alignment in a real Box scope if needed,
            // but here we might need a Z-index wrapper.
            // simpler: show a Toast or just rely on the fact the dialog closed.
        ) {
            // ideally this should be a Dialog or centered overlay.
            // For now, let's use a non-blocking UI indication or a proper Dialog.
        }

        // Re-implementation as a blocking Dialog for safety
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.size(16.dp))
                    Text("Extracting article...")
                }
            }
        }
    }
}

@Composable
fun CreateOptionItem(
    icon: ImageVector,
    label: String,
    isNew: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Box
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        if (isNew) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "NEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}