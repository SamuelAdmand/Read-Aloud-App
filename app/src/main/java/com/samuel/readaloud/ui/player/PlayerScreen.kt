package com.samuel.readaloud.ui.player

import android.R.attr.fontWeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuel.readaloud.domain.TtsManager
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.repository.TtsRepository
import com.samuel.readaloud.ui.components.PlayerControls
import kotlinx.coroutines.launch
import java.util.Locale

private enum class PlayerSheetType { NONE, SPEED, VOICE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val context = LocalContext.current
    val ttsManager = remember { TtsManager.getInstance(context) }
    // Repository for fetching voices locally in Player
    val repository = remember { TtsRepository() }

    val isPlaying by ttsManager.isPlaying.collectAsState()
    val isLoading by ttsManager.isLoading.collectAsState()
    val currentTitle by ttsManager.currentTitle.collectAsState()

    // Bottom Sheet State
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeSheet by remember { mutableStateOf(PlayerSheetType.NONE) }
    val scope = rememberCoroutineScope()

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Voice State
    var allVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var voiceSearchQuery by remember { mutableStateOf("") }

    // Load voices once
    LaunchedEffect(Unit) {
        try {
            allVoices = repository.getVoices()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize")
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Text")
                    }
                }
            )
        },
        bottomBar = {
            PlayerControls(
                title = currentTitle,
                isPlaying = isPlaying,
                isLoading = isLoading,
                playbackSpeed = playbackSpeed,
                onPlayPause = { ttsManager.togglePlayPause() },
                onNextSection = { /* TODO */ },
                onPrevSection = { /* TODO */ },
                onSpeedClick = { activeSheet = PlayerSheetType.SPEED },
                onVoiceClick = { activeSheet = PlayerSheetType.VOICE },
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Scrollable Text Display
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = ttsManager.sourceText.ifBlank { "No text content available." },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }

    // --- Bottom Sheet Handling ---
    if (activeSheet != PlayerSheetType.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = PlayerSheetType.NONE },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            when (activeSheet) {
                PlayerSheetType.SPEED -> {
                    SpeedBottomSheetContent(
                        currentSpeed = playbackSpeed,
                        onSpeedSelected = { speed ->
                            playbackSpeed = speed
                            ttsManager.setPlaybackSpeed(speed)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = PlayerSheetType.NONE }
                        }
                    )
                }
                PlayerSheetType.VOICE -> {
                    VoiceBottomSheetContent(
                        voices = allVoices,
                        searchQuery = voiceSearchQuery,
                        onSearchQueryChange = { voiceSearchQuery = it },
                        onVoiceSelected = { voice ->
                            // For now, we assume simple restarting or parameter change
                            // Ideally TtsManager should handle voice change on the fly or next chunk
                            // This is a placeholder for the actual voice switch logic in TtsManager
                            ttsManager.playText(ttsManager.sourceText, voice.shortName)

                            scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = PlayerSheetType.NONE }
                        }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun SpeedBottomSheetContent(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f,1.10f, 1.25f, 1.5f, 2.0f)

    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            text = "Playback Speed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        speeds.forEach { speed ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSpeedSelected(speed) }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${speed}x",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (speed == currentSpeed) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceBottomSheetContent(
    voices: List<Voice>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onVoiceSelected: (Voice) -> Unit
) {
    // --- Helpers ---
    fun getLanguageName(locale: String): String = try {
        Locale.forLanguageTag(locale).displayLanguage.ifBlank { "Unknown" }
    } catch (e: Exception) { "Unknown" }

    fun getLanguageCode(locale: String): String = try {
        Locale.forLanguageTag(locale).language
    } catch (e: Exception) { "" }

    fun getRegionName(locale: String): String = try {
        Locale.forLanguageTag(locale).displayCountry.ifBlank { "Standard" }
    } catch (e: Exception) { "Standard" }

    val priorityCodes = listOf(
        "en", "hi", "es", "zh", "fr", "ar", "bn", "pt", "ru", "ur", "de", "ja"
    )

    // --- Processing: Group by Language -> Then by Region ---
    // Returns List<Pair<LanguageName, Map<RegionName, List<Voice>>>>
    val processedVoices = remember(voices, searchQuery) {
        val filtered = if (searchQuery.isBlank()) voices else voices.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    getLanguageName(it.locale).contains(searchQuery, ignoreCase = true) ||
                    getRegionName(it.locale).contains(searchQuery, ignoreCase = true)
        }

        // 1. Group by Language
        val byLanguage = filtered.groupBy { getLanguageName(it.locale) }
            .toList()
            .sortedWith(Comparator { (langNameA, voicesA), (langNameB, voicesB) ->
                val codeA = voicesA.firstOrNull()?.let { getLanguageCode(it.locale) } ?: ""
                val codeB = voicesB.firstOrNull()?.let { getLanguageCode(it.locale) } ?: ""
                val p1 = priorityCodes.indexOf(codeA)
                val p2 = priorityCodes.indexOf(codeB)
                when {
                    p1 != -1 && p2 != -1 -> p1.compareTo(p2)
                    p1 != -1 -> -1
                    p2 != -1 -> 1
                    else -> langNameA.compareTo(langNameB)
                }
            })

        // 2. Map values to Sub-Groups (Region)
        byLanguage.map { (lang, langVoices) ->
            val byRegion = langVoices.groupBy { getRegionName(it.locale) }
                .toSortedMap() // Sort regions alphabetically
            lang to byRegion
        }
    }

    // --- UI ---
    Column(modifier = Modifier.fillMaxSize()) {
        // Reduced top padding (from 16.dp to 0.dp or minimal)
        Text(
            text = "Select Voice",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            // Removed top padding here because ModalBottomSheet usually has some built-in
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search voices...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )

        HorizontalDivider()

        if (voices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                processedVoices.forEach { (language, regionMap) ->
                    // Level 1: Language Header
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = language,
                                fontSize = 25.sp,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Iterate Regions
                    regionMap.forEach { (region, regionVoices) ->
                        // Level 2: Region Sub-Header (Only show if there are multiple regions or meaningful distinction)
                        item {
                            Text(
                                text = region,
                                fontSize = 18.sp,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }

                        // Level 3: Voices
                        items(regionVoices) { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVoiceSelected(voice) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voice.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    // Removed redundant locale text since we have headers
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}