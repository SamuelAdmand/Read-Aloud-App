package com.samuel.readaloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.model.Voice
import java.util.Locale

@Composable
fun VoiceSelectionSheetContent(
    allVoices: List<Voice>,
    currentVoiceId: String,
    onVoiceSelected: (Voice) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context) }

    // State
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") } // Default
    var showLanguageList by remember { mutableStateOf(false) }

    // Derived Data
    val languages = remember(allVoices) {
        // Priority list based on most spoken languages (ISO 639-1 codes)
        val priorityCodes = listOf(
            "en", // English
            "zh", // Chinese
            "es", // Spanish
            "hi", // Hindi
            "ar", // Arabic
            "bn", // Bengali
            "pt", // Portuguese
            "ru", // Russian
            "ja", // Japanese
            "de", // German
            "fr", // French
            "ko", // Korean
            "it", // Italian
            "tr", // Turkish
            "vi"  // Vietnamese
        )

        allVoices
            .map {
                val loc = try { Locale.forLanguageTag(it.locale) } catch (e: Exception) { Locale.getDefault() }
                val name = loc.displayLanguage.ifBlank { "Unknown" }
                val code = loc.language
                name to code
            }
            .distinctBy { it.first } // Unique by Display Name
            .sortedWith { (nameA, codeA), (nameB, codeB) ->
                val indexA = priorityCodes.indexOf(codeA)
                val indexB = priorityCodes.indexOf(codeB)

                when {
                    // Both in priority list: sort by index in priority list
                    indexA != -1 && indexB != -1 -> indexA.compareTo(indexB)
                    // Only A is in priority list: A comes first
                    indexA != -1 -> -1
                    // Only B is in priority list: B comes first
                    indexB != -1 -> 1
                    // Neither in priority list: sort alphabetically by name
                    else -> nameA.compareTo(nameB)
                }
            }
            .map { it.first }
    }

    val recentVoices = remember(allVoices, currentVoiceId) { // Re-calc if current changes to update list potentially
        val recentIds = prefs.getRecentVoiceIds()
        recentIds.mapNotNull { id -> allVoices.find { it.shortName == id } }
    }

    val filteredVoices = remember(allVoices, selectedLanguage, searchQuery) {
        if (searchQuery.isNotBlank()) {
            allVoices.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        getLanguageName(it.locale).contains(searchQuery, ignoreCase = true)
            }
        } else {
            allVoices.filter { getLanguageName(it.locale) == selectedLanguage }
        }
    }

    val groupedVoices = remember(filteredVoices) {
        filteredVoices.groupBy { getRegionName(it.locale) }.toSortedMap()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // --- Header ---
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search voices...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }

                // Language Selector Button
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .clickable { showLanguageList = !showLanguageList }
                        .height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Languages ($selectedLanguage)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(onClick = { isSearching = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        }

        // --- Language List (Horizontal) ---
        if (showLanguageList && !isSearching) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(languages) { language ->
                    FilterChip(
                        selected = language == selectedLanguage,
                        onClick = {
                            selectedLanguage = language
                            // Optional: auto-close list? Keep open for now as per "swipe right and left" feel
                        },
                        label = { Text(language) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- Main Content ---
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            // 1. Recents Section
            if (recentVoices.isNotEmpty() && searchQuery.isBlank()) {
                item {
                    Text(
                        text = "Recents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recentVoices) { voice ->
                            RecentVoiceItem(
                                voice = voice,
                                isSelected = voice.shortName == currentVoiceId,
                                onClick = {
                                    prefs.addRecentVoice(voice.shortName)
                                    onVoiceSelected(voice)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 2. Grouped Voices
            groupedVoices.forEach { (region, voices) ->
                item {
                    Text(
                        text = region,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
                items(voices) { voice ->
                    VoiceListItem(
                        voice = voice,
                        isSelected = voice.shortName == currentVoiceId,
                        onClick = {
                            prefs.addRecentVoice(voice.shortName)
                            onVoiceSelected(voice)
                        }
                    )
                }
            }

            if (filteredVoices.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No voices found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentVoiceItem(
    voice: Voice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(140.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = voice.name.take(1),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = voice.name.split(" ").firstOrNull() ?: voice.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = getRegionName(voice.locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun VoiceListItem(
    voice: Voice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            // Initials
            Text(
                text = voice.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = voice.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            // --- CHANGE STARTS HERE ---
            // Hide gender if it is "Unknown" (Common in System TTS)
            val subtitle = if (voice.gender == "Unknown") {
                getRegionName(voice.locale)
            } else {
                "${getRegionName(voice.locale)} • ${voice.gender}"
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // --- CHANGE ENDS HERE ---
        }

        // Checkmark moved to the end
        if (isSelected) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Helpers
private fun getLanguageName(localeStr: String): String = try {
    val locale = if (localeStr.contains("_")) {
        val parts = localeStr.split("_")
        Locale(parts[0], parts.getOrElse(1) { "" })
    } else {
        Locale.forLanguageTag(localeStr)
    }
    locale.displayLanguage.ifBlank { "Unknown" }
} catch (e: Exception) { "Unknown" }

private fun getRegionName(localeStr: String): String = try {
    val locale = if (localeStr.contains("_")) {
        val parts = localeStr.split("_")
        Locale(parts[0], parts.getOrElse(1) { "" })
    } else {
        Locale.forLanguageTag(localeStr)
    }
    locale.displayCountry.ifBlank { "Standard" }
} catch (e: Exception) { "Standard" }