package com.samuel.readaloud.ui.more

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samuel.readaloud.data.local.PreferenceManager
import com.samuel.readaloud.model.Voice
import com.samuel.readaloud.repository.TtsRepository
import com.samuel.readaloud.domain.TtsManager
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TtsRepository(application)
    private val preferenceManager = PreferenceManager(application)

    var currentProvider by mutableStateOf(preferenceManager.ttsProvider)
        private set

    var systemEngines by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    var currentSystemEngine by mutableStateOf(preferenceManager.systemTtsEngine)
        private set

    // --- State ---
    var defaultVoiceName by mutableStateOf(preferenceManager.voiceName)
        private set

    var defaultVoiceId by mutableStateOf(preferenceManager.voiceId)
        private set

    var defaultSpeed by mutableStateOf(preferenceManager.playbackSpeed)
        private set

    var voices by mutableStateOf<List<Voice>>(emptyList())
        private set

    init {
        loadVoices()
        loadSystemEngines()
    }

    private fun loadSystemEngines() {
        viewModelScope.launch {
            systemEngines = repository.getSystemEngines()
        }
    }

    private fun loadVoices() {
        viewModelScope.launch {
            try {
                // 1. Fetch voices for current provider
                voices = repository.getVoices(currentProvider)

                // 2. Check if we have a saved preference for this provider
                val savedVoice = preferenceManager.getVoiceForProvider(currentProvider)

                if (savedVoice == null && voices.isNotEmpty()) {
                    // FRESH INSTALL or First time using this provider
                    // Pick a smart default and SAVE it immediately
                    val smartDefault = getSmartDefaultVoice(currentProvider, voices)
                    smartDefault?.let {
                        onVoiceSelected(it)
                    }
                } else if (savedVoice != null) {
                    // Ensure UI matches saved pref (if strictly needed, though global pref usually holds it)
                    if (defaultVoiceId != savedVoice.first) {
                        // Sync state if drift occurred
                        defaultVoiceId = savedVoice.first
                        defaultVoiceName = savedVoice.second
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onProviderChanged(provider: String) {
        if (currentProvider == provider) return

        currentProvider = provider
        preferenceManager.ttsProvider = provider

        viewModelScope.launch {
            try {
                voices = repository.getVoices(provider)
            } catch (e: Exception) {
                voices = emptyList()
            }

            // Check for saved voice for this new provider
            val savedVoice = preferenceManager.getVoiceForProvider(provider)

            if (savedVoice != null) {
                // Restore saved voice
                val (id, name) = savedVoice
                defaultVoiceId = id
                defaultVoiceName = name
                // Sync global prefs
                preferenceManager.voiceId = id
                preferenceManager.voiceName = name
            } else {
                // No saved voice -> Pick Smart Default
                val default = getSmartDefaultVoice(provider, voices)
                default?.let { onVoiceSelected(it) }
            }
        }
    }

    fun onVoiceSelected(voice: Voice) {
        defaultVoiceName = voice.name
        defaultVoiceId = voice.shortName

        // Save to global prefs
        preferenceManager.voiceId = voice.shortName
        preferenceManager.voiceName = voice.name

        // Save to provider-specific prefs
        preferenceManager.saveVoiceForProvider(currentProvider, voice.shortName, voice.name)
    }

    fun onSpeedChanged(newSpeed: Float) {
        defaultSpeed = newSpeed
        preferenceManager.playbackSpeed = newSpeed
    }

    fun onSystemEngineChanged(enginePackage: String?) {
        if (currentSystemEngine == enginePackage) return

        currentSystemEngine = enginePackage
        preferenceManager.systemTtsEngine = enginePackage
        TtsManager.getInstance(getApplication()).updateSystemEngine(enginePackage)

        // Reload voices for the new engine
        loadVoices()
    }

    /**
     * Determines the best default voice for a fresh install/first-use.
     */
    private fun getSmartDefaultVoice(provider: String, voices: List<Voice>): Voice? {
        val systemLocale = Locale.getDefault()
        val systemTag = systemLocale.toLanguageTag() // e.g. "en-US"
        val systemLang = systemLocale.language       // e.g. "en"

        return when (provider) {
            PreferenceManager.PROVIDER_EDGE -> {
                // 1. Try the high-quality Aria voice (most popular)
                voices.find { it.shortName == "en-GB-RyanNeural" }
                // 2. Try matching system locale (e.g. if user is es-ES, find es-ES voice)
                    ?: voices.find { it.locale.equals(systemTag, ignoreCase = true) }
                    // 3. Fallback
                    ?: voices.firstOrNull()
            }
            PreferenceManager.PROVIDER_GOOGLE -> {
                // Google often uses language codes like "en" or "es"
                voices.find { it.shortName.equals(systemLang, ignoreCase = true) }
                    ?: voices.find { it.shortName.equals("en", ignoreCase = true) }
                    ?: voices.firstOrNull()
            }
            PreferenceManager.PROVIDER_SYSTEM -> {
                if (systemLang == "en") {
                    val preferred = voices.find {
                        it.shortName.equals("en-us-x-iom-network", ignoreCase = true)
                    }
                    if (preferred != null) return preferred
                }
                // System TTS: Try to match the device's active locale exactly
                voices.find { it.locale.equals(systemTag, ignoreCase = true) }
                // Or loosely
                    ?: voices.find { it.locale.startsWith(systemLang, ignoreCase = true) }
                    ?: voices.firstOrNull()
            }
            else -> voices.firstOrNull()
        }
    }
}