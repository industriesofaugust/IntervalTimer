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

/**
 * How phone workouts link to the watch. AUTO: the watch joins every workout.
 * MANUAL: workouts start alone and the tag offers LINK WATCH. OFF: never.
 */
enum class WatchLinkMode { AUTO, MANUAL, OFF }

/** What the phone's own cues do while the watch has joined the workout. */
enum class WhenWatchJoins { QUIET, SOUND_ONLY, BOTH }

data class AppSettings(
    val soundEnabled: Boolean = true,
    /** 1 (quiet) to 5 (loudest). */
    val soundLevel: Int = 3,
    val vibrate: Boolean = true,
    /** 1 (gentle) to 5 (strongest). */
    val vibrationLevel: Int = 3,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val style: AppStyle = AppStyle.DIAL,
    val countdownFormat: CountdownFormat = CountdownFormat.SECONDS,
    /** Whether phone workouts also run on the watch (the live link). */
    val watchLink: WatchLinkMode = WatchLinkMode.AUTO,
    /** Only applies while the watch is linked; otherwise the phone cues as usual. */
    val whenWatchJoins: WhenWatchJoins = WhenWatchJoins.QUIET,
) {
    /** What the phone SoundPlayer takes, 0 to 100. */
    val soundVolume: Int get() = soundVolumeFor(soundLevel)
}

// Steps 4 and 5 match the old MEDIUM (85) and HIGH (100), so nobody's cues get quieter.
private val SOUND_VOLUMES = listOf(30, 50, 70, 85, 100)

fun soundVolumeFor(level: Int): Int = SOUND_VOLUMES[level.coerceIn(1, 5) - 1]

// LOW (50) becomes 2, MEDIUM (85) 4, HIGH (100) 5.
internal fun levelFromOldVolume(volume: Int): Int = when {
    volume <= 50 -> 2
    volume <= 85 -> 4
    else -> 5
}

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class AppSettingsRepository(private val context: Context) {

    private object Keys {
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val soundLevel = intPreferencesKey("sound_level")
        // The old LOW / MEDIUM / HIGH volume (0-100); only read when the level has not been saved yet.
        val soundVolume = intPreferencesKey("sound_volume")
        val vibrate = booleanPreferencesKey("vibrate")
        val vibrationLevel = intPreferencesKey("vibration_level")
        val themeMode = stringPreferencesKey("theme_mode")
        val style = stringPreferencesKey("style")
        val countdownFormat = stringPreferencesKey("countdown_format")
        // The old on/off setting; only read when the new one has not been saved yet.
        val useWatch = booleanPreferencesKey("use_watch")
        val watchLink = stringPreferencesKey("watch_link")
        val whenWatchJoins = stringPreferencesKey("when_watch_joins")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            soundEnabled = prefs[Keys.soundEnabled] ?: true,
            soundLevel = (prefs[Keys.soundLevel] ?: prefs[Keys.soundVolume]?.let(::levelFromOldVolume) ?: 3)
                .coerceIn(1, 5),
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
            watchLink = prefs[Keys.watchLink]
                ?.let { runCatching { WatchLinkMode.valueOf(it) }.getOrNull() }
                ?: if (prefs[Keys.useWatch] == false) WatchLinkMode.OFF else WatchLinkMode.AUTO,
            whenWatchJoins = prefs[Keys.whenWatchJoins]
                ?.let { runCatching { WhenWatchJoins.valueOf(it) }.getOrNull() }
                ?: WhenWatchJoins.QUIET,
        )
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.soundEnabled] = enabled }
    }

    suspend fun setSoundLevel(level: Int) {
        context.dataStore.edit {
            it[Keys.soundLevel] = level.coerceIn(1, 5)
            it.remove(Keys.soundVolume)
        }
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

    suspend fun setWatchLink(mode: WatchLinkMode) {
        context.dataStore.edit {
            it[Keys.watchLink] = mode.name
            it.remove(Keys.useWatch)
        }
    }

    suspend fun setWhenWatchJoins(choice: WhenWatchJoins) {
        context.dataStore.edit { it[Keys.whenWatchJoins] = choice.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }
}
