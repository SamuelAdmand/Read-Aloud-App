package com.samuel.readaloud.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app preferences with strict type safety.
 * Instruction #11: Never change the data type of an existing key.
 */
class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "read_aloud_prefs"

        // Keys - never change these strings or their expected types
        private const val KEY_VOICE_ID = "default_voice_id"
        private const val KEY_VOICE_NAME = "default_voice_name"
        private const val KEY_PLAYBACK_SPEED = "default_playback_speed"
        private const val KEY_RECENT_VOICES = "recent_voices_list"
        private const val KEY_SYSTEM_TTS_ENGINE = "system_tts_engine"

        // Defaults
        private const val DEFAULT_VOICE_ID = "en-US-AriaNeural"
        private const val DEFAULT_VOICE_NAME = "Aria (US)"
        private const val DEFAULT_SPEED = 1.0f
        private const val KEY_TTS_PROVIDER = "tts_provider"
        const val PROVIDER_EDGE = "edge"
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_SYSTEM = "system"
    }
    var ttsProvider: String
        get() = prefs.getString(KEY_TTS_PROVIDER, PROVIDER_EDGE) ?: PROVIDER_EDGE
        set(value) = prefs.edit().putString(KEY_TTS_PROVIDER, value).apply()

    var systemTtsEngine: String?
        get() = prefs.getString(KEY_SYSTEM_TTS_ENGINE, null)
        set(value) = prefs.edit().putString(KEY_SYSTEM_TTS_ENGINE, value).apply()

    var voiceId: String
        get() = prefs.getString(KEY_VOICE_ID, DEFAULT_VOICE_ID) ?: DEFAULT_VOICE_ID
        set(value) = prefs.edit().putString(KEY_VOICE_ID, value).apply()

    var voiceName: String
        get() = prefs.getString(KEY_VOICE_NAME, DEFAULT_VOICE_NAME) ?: DEFAULT_VOICE_NAME
        set(value) = prefs.edit().putString(KEY_VOICE_NAME, value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_SPEED)
        set(value) = prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()

    // --- Recents Logic ---
    fun getRecentVoiceIds(): List<String> {
        val string = prefs.getString(KEY_RECENT_VOICES, "") ?: ""
        return if (string.isBlank()) emptyList() else string.split(",")
    }

    fun addRecentVoice(shortName: String) {
        val current = getRecentVoiceIds().toMutableList()
        // Remove if exists to move to top (LRU)
        current.remove(shortName)
        current.add(0, shortName)
        // Keep max 5
        if (current.size > 5) {
            val subList = current.take(5)
            prefs.edit().putString(KEY_RECENT_VOICES, subList.joinToString(",")).apply()
        } else {
            prefs.edit().putString(KEY_RECENT_VOICES, current.joinToString(",")).apply()
        }
    }

    // Helper to get keys dynamic based on provider
    private fun getProviderVoiceIdKey(provider: String) = "voice_id_$provider"
    private fun getProviderVoiceNameKey(provider: String) = "voice_name_$provider"

    fun getVoiceForProvider(provider: String): Pair<String, String>? {
        val id = prefs.getString(getProviderVoiceIdKey(provider), null)
        val name = prefs.getString(getProviderVoiceNameKey(provider), null)
        return if (id != null && name != null) id to name else null
    }

    fun saveVoiceForProvider(provider: String, id: String, name: String) {
        prefs.edit()
            .putString(getProviderVoiceIdKey(provider), id)
            .putString(getProviderVoiceNameKey(provider), name)
            .apply()

        // Also update the global "current" voice so the rest of the app sees it immediately
        voiceId = id
        voiceName = name
    }
}