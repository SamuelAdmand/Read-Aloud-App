package com.samuel.readaloud.ui.extension.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samuel.readaloud.domain.extension.InstalledExtension
import com.samuel.readaloud.domain.extension.SupportedSiteInfo

/**
 * Searchable dialog displaying all websites with custom rules supported by an extension.
 *
 * Allows users to review all supported sites and selectively toggle individual sites on/off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportedSitesDialog(
    extension: InstalledExtension,
    onToggleSite: (siteDomain: String, enabled: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    SupportedSitesDialog(
        extensionName = extension.manifest.name,
        sites = extension.getEffectiveSites(),
        disabledSites = extension.disabledSites,
        onToggleSite = onToggleSite,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportedSitesDialog(
    extensionName: String,
    sites: List<SupportedSiteInfo>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    disabledSites: Set<String> = emptySet(),
    onToggleSite: ((siteDomain: String, enabled: Boolean) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val allSites = remember(sites) { sites }

    val filteredSites = remember(searchQuery, allSites) {
        if (searchQuery.isBlank()) {
            allSites
        } else {
            val q = searchQuery.trim().lowercase()
            allSites.filter { site ->
                site.name.lowercase().contains(q) ||
                        site.domain.lowercase().contains(q) ||
                        site.safeDomains.any { it.lowercase().contains(q) } ||
                        site.safeDescription.lowercase().contains(q) ||
                        site.safeImprovements.any { it.lowercase().contains(q) }
            }
        }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(
                    text = "Supported Websites (${allSites.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = extensionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Live Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search website or domain...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredSites.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No websites found matching '$searchQuery'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSites, key = { it.domain }) { site ->
                            val isSiteEnabled = !disabledSites.contains(site.domain)
                            SupportedSiteRow(
                                site = site,
                                isEnabled = isSiteEnabled,
                                onToggle = if (onToggleSite != null) {
                                    { enabled -> onToggleSite(site.domain, enabled) }
                                } else null
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportedSiteRow(
    site: SupportedSiteInfo,
    isEnabled: Boolean,
    onToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (site.safeDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = site.safeDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val displayDomains = if (site.safeDomains.isNotEmpty()) site.safeDomains else listOf(site.domain)
                    displayDomains.take(3).forEach { d ->
                        AssistChip(
                            onClick = {},
                            label = { Text(d) }
                        )
                    }
                }
            }

            if (onToggle != null) {
                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}
