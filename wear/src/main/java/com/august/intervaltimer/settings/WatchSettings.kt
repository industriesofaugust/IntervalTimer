package com.august.intervaltimer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.august.intervaltimer.design.AppStyle
import com.august.intervaltimer.sync.RecentTemplate
import com.august.intervaltimer.sync.RecentTemplatesCodec
import com.august.intervaltimer.sync.WatchCueSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WatchSettings(
    val cues: WatchCueSettings = WatchCueSettings(),
    val keepScreenOn: Boolean = false,
    val style: AppStyle = AppStyle.DIAL,
    val recents: List<RecentTemplate> = emptyList(),
)

private val Context.dataStore by preferencesDataStore(name = "watch_settings")

/** Stores the watch settings on the watch itself, surviving restarts. */
class WatchSettingsRepository(private val context: Context) {

    private object Keys {
        val vibEnabled = booleanPreferencesKey("vibration_enabled")
        val vibLevel = intPreferencesKey("vibration_level_5")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val soundLevel = intPreferencesKey("sound_level_5")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val style = stringPreferencesKey("style")
        val recents = stringPreferencesKey("recent_templates")

        // Old v0.7 keys, read once so upgrades keep the user's choices.
        val oldVibration = stringPreferencesKey("vibration_level")
        val oldVolume = intPreferencesKey("sound_volume")
    }

    val settings: Flow<WatchSettings> = context.dataStore.data.map { prefs ->
        WatchSettings(
            cues = WatchCueSettings(
                vibrationEnabled = prefs[Keys.vibEnabled] ?: true,
                vibrationLevel = prefs[Keys.vibLevel]
                    ?: when (prefs[Keys.oldVibration]) {
                        "GENTLE" -> 2
                        "NORMAL" -> 3
                        "STRONG" -> 5
                        else -> 4
                    },
                soundEnabled = prefs[Keys.soundEnabled] ?: false,
                soundLevel = prefs[Keys.soundLevel]
                    ?: when {
                        (prefs[Keys.oldVolume] ?: -1) < 0 -> 3
                        prefs[Keys.oldVolume]!! <= 40 -> 2
                        prefs[Keys.oldVolume]!! <= 70 -> 3
                        else -> 5
                    },
            ),
            keepScreenOn = prefs[Keys.keepScreenOn] ?: false,
            style = prefs[Keys.style]
                ?.let { runCatching { AppStyle.valueOf(it) }.getOrNull() }
                ?: AppStyle.DIAL,
            recents = prefs[Keys.recents]?.let { RecentTemplatesCodec.decode(it) } ?: emptyList(),
        )
    }

    suspend fun setCues(cues: WatchCueSettings) {
        context.dataStore.edit {
            it[Keys.vibEnabled] = cues.vibrationEnabled
            it[Keys.vibLevel] = cues.vibrationLevel.coerceIn(1, 5)
            it[Keys.soundEnabled] = cues.soundEnabled
            it[Keys.soundLevel] = cues.soundLevel.coerceIn(1, 5)
        }
    }

    suspend fun setStyle(style: AppStyle) {
        context.dataStore.edit { it[Keys.style] = style.name }
    }

    suspend fun setKeepScreenOn(keepOn: Boolean) {
        context.dataStore.edit { it[Keys.keepScreenOn] = keepOn }
    }

    /** Remembers the two most recently started workouts, newest first. */
    suspend fun addRecent(recent: RecentTemplate) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.recents]?.let { RecentTemplatesCodec.decode(it) } ?: emptyList()
            val updated = (listOf(recent) + current.filter { it.name != recent.name }).take(2)
            prefs[Keys.recents] = RecentTemplatesCodec.encode(updated)
        }
    }
}
