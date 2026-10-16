package com.august.intervaltimer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.august.intervaltimer.design.AppStyle
import com.august.intervaltimer.design.CountdownFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What the phone's own cues do while the watch has joined the workout. */
enum class WhenWatchJoins { QUIET, SOUND_ONLY, BOTH }

data class AppSettings(
    val soundEnabled: Boolean = true,
    val soundVolume: Int = 85,
    val vibrate: Boolean = true,
    /** 1 (gentle) to 5 (strongest). */
    val vibrationLevel: Int = 3,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val style: AppStyle = AppStyle.DIAL,
    val countdownFormat: CountdownFormat = CountdownFormat.SECONDS,
    /** Phone workouts also run on the watch (the live link). */
    val useWatch: Boolean = true,
    /** Only applies while the watch is linked; otherwise the phone cues as usual. */
    val whenWatchJoins: WhenWatchJoins = WhenWatchJoins.QUIET,
)

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class AppSettingsRepository(private val context: Context) {

    private object Keys {
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val soundVolume = intPreferencesKey("sound_volume")
        val vibrate = booleanPreferencesKey("vibrate")
        val vibrationLevel = intPreferencesKey("vibration_level")
        val themeMode = stringPreferencesKey("theme_mode")
        val style = stringPreferencesKey("style")
        val countdownFormat = stringPreferencesKey("countdown_format")
        val useWatch = booleanPreferencesKey("use_watch")
        val whenWatchJoins = stringPreferencesKey("when_watch_joins")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            soundEnabled = prefs[Keys.soundEnabled] ?: true,
            soundVolume = prefs[Keys.soundVolume] ?: 85,
            vibrate = prefs[Keys.vibrate] ?: true,
            vibrationLevel = (prefs[Keys.vibrationLevel] ?: 3).coerceIn(1, 5),
            themeMode = prefs[Keys.themeMode]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            style = prefs[Keys.style]
                ?.let { runCatching { AppStyle.valueOf(it) }.getOrNull() }
                ?: AppStyle.DIAL,
            countdownFormat = prefs[Keys.countdownFormat]
                ?.let { runCatching { CountdownFormat.valueOf(it) }.getOrNull() }
                ?: CountdownFormat.SECONDS,
            useWatch = prefs[Keys.useWatch] ?: true,
            whenWatchJoins = prefs[Keys.whenWatchJoins]
                ?.let { runCatching { WhenWatchJoins.valueOf(it) }.getOrNull() }
                ?: WhenWatchJoins.QUIET,
        )
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.soundEnabled] = enabled }
    }

    suspend fun setSoundVolume(volume: Int) {
        context.dataStore.edit { it[Keys.soundVolume] = volume.coerceIn(0, 100) }
    }

    suspend fun setVibrate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.vibrate] = enabled }
    }

    suspend fun setVibrationLevel(level: Int) {
        context.dataStore.edit { it[Keys.vibrationLevel] = level.coerceIn(1, 5) }
    }

    suspend fun setStyle(style: AppStyle) {
        context.dataStore.edit { it[Keys.style] = style.name }
    }

    suspend fun setCountdownFormat(format: CountdownFormat) {
        context.dataStore.edit { it[Keys.countdownFormat] = format.name }
    }

    suspend fun setUseWatch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.useWatch] = enabled }
    }

    suspend fun setWhenWatchJoins(choice: WhenWatchJoins) {
        context.dataStore.edit { it[Keys.whenWatchJoins] = choice.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }
}
