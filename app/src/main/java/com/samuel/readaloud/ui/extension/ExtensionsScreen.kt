package com.samuel.readaloud.ui.extension

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuel.readaloud.domain.extension.ExtensionManager
import com.samuel.readaloud.domain.extension.SupportedSiteInfo
import com.samuel.readaloud.ui.extension.components.AddRepositoryDialog
import com.samuel.readaloud.ui.extension.components.ExtensionSourceCard
import com.samuel.readaloud.ui.extension.components.ExtensionWebsitesView
import com.samuel.readaloud.ui.extension.components.SiteDetailsSheet
import com.samuel.readaloud.ui.extension.viewmodel.ExtensionsViewModel

/**
 * 3-Tier Extensions management screen:
 * 1. Root page: Displays the Extension Repository Source URL & bundle status card.
 * 2. On tapping the source card: Opens the full directory of supported websites with search.
 * 3. On tapping any website: Opens the site improvement notes sheet detailing changes made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExtensionsViewModel = viewModel()
) {
    val context = LocalContext.current
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var selectedSiteForDetails by remember { mutableStateOf<SupportedSiteInfo?>(null) }
    var isViewingWebsites by remember { mutableStateOf(false) }

    val installed by viewModel.installedExtensions.collectAsState()
    val storeItems by viewModel.storeExtensions.collectAsState()
    val repositoryUrls by viewModel.repositoryUrls.collectAsState()
    val isLoadingStore by viewModel.isLoadingStore.collectAsState()

    val currentRepoUrl = remember(repositoryUrls) {
        repositoryUrls.firstOrNull() ?: ExtensionManager.DEFAULT_REPO_URL
    }

    // Active primary extension: prefer highest priority installed extension
    val activeExtension = remember(installed) {
        installed.filter { !it.isBuiltIn }.maxByOrNull { it.manifest.priority }
            ?: installed.firstOrNull { !it.isBuiltIn }
            ?: installed.firstOrNull()
    }

    // File picker launcher for local .readaloud-ext / .zip files in debug mode
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.installFromUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val isDebug = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // Handle back button when viewing websites sub-view
    BackHandler(enabled = isViewingWebsites) {
        isViewingWebsites = false
    }

    if (isViewingWebsites && activeExtension != null) {
        // LEVEL 2: All websites offered by this extension
        ExtensionWebsitesView(
            extension = activeExtension,
            onBackClick = { isViewingWebsites = false },
            onSiteClick = { site -> selectedSiteForDetails = site },
            modifier = modifier
        )
    } else {
        // LEVEL 1: Root page showing Extension URL & Source
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text("Extensions", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddRepoDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Repository Settings"
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                if (isDebug) {
                    ExtendedFloatingActionButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        icon = { Icon(Icons.Rounded.FileOpen, contentDescription = null) },
                        text = { Text("Import (Debug)") }
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Extension Source URL & Details Card (Tappable to explore websites)
                item {
                    ExtensionSourceCard(
                        repositoryUrl = currentRepoUrl,
                        extension = activeExtension,
                        isUpdating = isLoadingStore,
                        onCardClick = {
                            if (activeExtension != null) {
                                isViewingWebsites = true
                            } else {
                                Toast.makeText(context, "No extension installed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCheckUpdate = {
                            viewModel.refreshCatalog()
                            val remoteMatch = storeItems.firstOrNull { it.id == activeExtension?.manifest?.id }
                            if (remoteMatch != null && remoteMatch.version != activeExtension?.manifest?.version) {
                                viewModel.installFromStore(remoteMatch)
                            }
                        },
                        onToggleExtension = { enabled ->
                            activeExtension?.manifest?.id?.let { id ->
                                viewModel.toggleExtension(id, enabled)
                            }
                        },
                        onEditUrl = { showAddRepoDialog = true }
                    )
                }

                // 2. Info / Guidance Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Dynamic Website Rules",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Website extraction logic, paywall bypasses, and speech cleanup are dynamically fetched from the repository above. Tap the card to inspect individual site rules and improvements.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // LEVEL 3: Modal Sheet showing site improvements and change notes
    selectedSiteForDetails?.let { site ->
        val isSiteEnabled = activeExtension?.disabledSites?.contains(site.domain) != true
        SiteDetailsSheet(
            site = site,
            isEnabled = isSiteEnabled,
            onToggle = { enabled ->
                activeExtension?.manifest?.id?.let { extId ->
                    viewModel.toggleSite(extId, site.domain, enabled)
                }
            },
            onDismissRequest = { selectedSiteForDetails = null }
        )
    }

    if (showAddRepoDialog) {
        AddRepositoryDialog(
            onDismissRequest = { showAddRepoDialog = false },
            onConfirm = { url ->
                showAddRepoDialog = false
                viewModel.addRepository(url)
            }
        )
    }
}
