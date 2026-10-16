package com.august.intervaltimer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What the Health card shows about saving workouts to Health Connect. */
data class HealthStatus(
    val lastSavedAt: Long? = null,
    /** Workouts whose latest version is in Health Connect. */
    val savedCount: Int = 0,
    val lastError: String? = null,
    /** Whether the one-time clean-up of records from older versions has run. */
    val cleanedUp: Boolean = false,
)

private val Context.healthDataStore by preferencesDataStore(name = "health_status")

/**
 * Remembers what was saved to Health Connect, so the Health card can say so
 * and a retry knows what is missing. Each saved workout is kept as
 * "key@version", so a newer version of the same workout counts as unsaved.
 */
class HealthPrefsRepository(private val context: Context) {

    private object Keys {
        val lastSavedAt = longPreferencesKey("last_saved_at")
        val lastError = stringPreferencesKey("last_error")
        val saved = stringSetPreferencesKey("saved")
        val cleanedUp = booleanPreferencesKey("cleaned_up")
        val age = intPreferencesKey("age")
    }

    val status: Flow<HealthStatus> = context.healthDataStore.data.map { prefs ->
        HealthStatus(
            lastSavedAt = prefs[Keys.lastSavedAt],
            savedCount = (prefs[Keys.saved] ?: emptySet()).size,
            lastError = prefs[Keys.lastError],
            cleanedUp = prefs[Keys.cleanedUp] ?: false,
        )
    }

    /** The person's age for heart-rate zones, or null if they did not enter one. */
    val age: Flow<Int?> = context.healthDataStore.data.map { it[Keys.age] }

    suspend fun setAge(age: Int?) {
        context.healthDataStore.edit {
            if (age == null) it.remove(Keys.age) else it[Keys.age] = age
        }
    }

    suspend fun savedEntries(): Set<String> =
        context.healthDataStore.data.first()[Keys.saved] ?: emptySet()

    /** [key] is in Health Connect at [version]. Clears the last error. */
    suspend fun markSaved(key: String, version: Long, now: Long) {
        context.healthDataStore.edit { prefs ->
            prefs[Keys.saved] = withoutKey(prefs, key) + entry(key, version)
            prefs[Keys.lastSavedAt] = now
            prefs.remove(Keys.lastError)
        }
    }

    suspend fun markFailed(message: String) {
        context.healthDataStore.edit { it[Keys.lastError] = message }
    }

    suspend fun clearError() {
        context.healthDataStore.edit { it.remove(Keys.lastError) }
    }

    /** The workout was deleted, so its records are gone from Health Connect too. */
    suspend fun forget(key: String) {
        context.healthDataStore.edit { prefs -> prefs[Keys.saved] = withoutKey(prefs, key) }
    }

    /** Health Connect no longer holds what was saved (after a clean-up). */
    suspend fun forgetAll() {
        context.healthDataStore.edit { it.remove(Keys.saved) }
    }

    suspend fun setCleanedUp() {
        context.healthDataStore.edit { it[Keys.cleanedUp] = true }
    }

    private fun withoutKey(prefs: Preferences, key: String): Set<String> =
        (prefs[Keys.saved] ?: emptySet()).filterNot { it.startsWith("$key@") }.toSet()

    companion object {
        fun entry(key: String, version: Long) = "$key@$version"
    }
}
