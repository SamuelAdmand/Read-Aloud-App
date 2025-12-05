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

        // Defaults
        private const val DEFAULT_VOICE_ID = "en-US-AriaNeural"
        private const val DEFAULT_VOICE_NAME = "Aria (US)"
        private const val DEFAULT_SPEED = 1.0f
    }

    var voiceId: String
        get() = prefs.getString(KEY_VOICE_ID, DEFAULT_VOICE_ID) ?: DEFAULT_VOICE_ID
        set(value) = prefs.edit().putString(KEY_VOICE_ID, value).apply()

    var voiceName: String
        get() = prefs.getString(KEY_VOICE_NAME, DEFAULT_VOICE_NAME) ?: DEFAULT_VOICE_NAME
        set(value) = prefs.edit().putString(KEY_VOICE_NAME, value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_SPEED)
        set(value) = prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
}