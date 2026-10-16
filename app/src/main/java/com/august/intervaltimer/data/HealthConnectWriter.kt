package com.august.intervaltimer.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/** How many workouts a bulk save wrote and how many it could not. */
data class SaveSummary(val saved: Int = 0, val failed: Int = 0)

/**
 * Saves finished workouts into Health Connect, the shared on-device health
 * store that Fitbit, Google Fit, and other apps read from. Every save is
 * recorded in [HealthPrefsRepository], so a failure shows in Settings rather
 * than going unnoticed.
 */
class HealthConnectWriter(
    private val context: Context,
    private val prefs: HealthPrefsRepository = HealthPrefsRepository(context),
) {

    private val recordTypes: List<KClass<out Record>> = listOf(
        ExerciseSessionRecord::class,
        HeartRateRecord::class,
        StepsRecord::class,
        TotalCaloriesBurnedRecord::class,
    )

    /** Needed to save workouts. */
    val writePermissions: Set<String> = recordTypes.map { HealthPermission.getWritePermission(it) }.toSet()

    /** Only used to draw heart-rate zones; sharing works without it. */
    val readPermissions: Set<String> =
        setOf(HealthPermission.getReadPermission(RestingHeartRateRecord::class))

    /** Everything the link button asks for. */
    val permissions: Set<String> = writePermissions + readPermissions

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    private suspend fun granted(): Set<String> {
        val client = client() ?: return emptySet()
        return runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
    }

    /** Linked means we are allowed to save workouts. */
    suspend fun isLinked(): Boolean = granted().containsAll(writePermissions)

    suspend fun canReadRestingHeartRate(): Boolean = granted().containsAll(readPermissions)

    /** Saves the workout if Health Connect is linked; does nothing otherwise. */
    suspend fun saveIfLinked(
        result: WorkoutResultEntity,
        fromWatch: Boolean = result.looksLikeWatchResult(),
    ) {
        val client = client() ?: return
        if (!isLinked()) return
        save(client, result, fromWatch)
    }

    /** Saves every stored workout whose latest version is not in Health Connect yet. */
    suspend fun saveUnsaved(results: List<WorkoutResultEntity>): SaveSummary {
        val client = client() ?: return SaveSummary()
        if (!isLinked()) return SaveSummary()
        val savedEntries = prefs.savedEntries()
        val missing = results.filter { result ->
            val wanted = healthVersion(result.looksLikeWatchResult())
            savedEntries.none { it.startsWith("${result.healthKey()}@") && it.versionOfEntry() >= wanted }
        }
        return saveAll(client, missing)
    }

    /**
     * Removes the records this app wrote in the time range of [results], then
     * saves every workout again under its stable id. Health Connect only ever
     * deletes the calling app's own records. Returns null if the removal failed.
     */
    suspend fun cleanUp(
        results: List<WorkoutResultEntity>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): SaveSummary? {
        val client = client() ?: return null
        if (!isLinked()) return null
        if (results.isNotEmpty()) {
            val earliest = results.minOf { it.completedAt - it.totalSeconds * 1000L }
            val latest = results.maxOf { it.completedAt }
            val range = TimeRangeFilter.between(
                Instant.ofEpochMilli(earliest).minus(CLEAN_UP_MARGIN_HOURS, ChronoUnit.HOURS),
                Instant.ofEpochMilli(latest).plus(CLEAN_UP_MARGIN_HOURS, ChronoUnit.HOURS),
            )
            try {
                recordTypes.forEach { client.deleteRecords(it, range) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                prefs.markFailed(describe(e))
                return null
            }
        }
        prefs.forgetAll()
        prefs.setCleanedUp()
        return saveAll(client, results, onProgress)
    }

    private suspend fun saveAll(
        client: HealthConnectClient,
        results: List<WorkoutResultEntity>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SaveSummary {
        var saved = 0
        var failed = 0
        results.forEachIndexed { index, result ->
            if (save(client, result, result.looksLikeWatchResult())) saved++ else failed++
            onProgress(index + 1, results.size)
        }
        return SaveSummary(saved, failed)
    }

    private suspend fun save(
        client: HealthConnectClient,
        result: WorkoutResultEntity,
        fromWatch: Boolean,
    ): Boolean = try {
        client.insertRecords(buildHealthRecords(result, fromWatch))
        prefs.markSaved(result.healthKey(), healthVersion(fromWatch), System.currentTimeMillis())
        true
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        prefs.markFailed(describe(e))
        false
    }

    /** Removes the workout's records from Health Connect, when the workout is deleted in the app. */
    suspend fun delete(result: WorkoutResultEntity) {
        val client = client() ?: return
        val key = result.healthKey()
        val ids = listOf(
            healthSessionId(key) to ExerciseSessionRecord::class,
            healthHeartRateId(key) to HeartRateRecord::class,
            healthStepsId(key) to StepsRecord::class,
            healthCaloriesId(key) to TotalCaloriesBurnedRecord::class,
        )
        try {
            if (isLinked()) {
                ids.forEach { (id, type) ->
                    client.deleteRecords(type, emptyList(), listOf(id))
                }
            }
            prefs.forget(key)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            prefs.markFailed(describe(e))
        }
    }

    /** Takes back every permission. Returns false if Health Connect refused. */
    suspend fun stopSharing(): Boolean {
        val client = client() ?: return false
        return try {
            client.permissionController.revokeAllPermissions()
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            prefs.markFailed(describe(e))
            false
        }
    }

    /** The most recent resting heart rate from the last 30 days, or null if there is none to read. */
    suspend fun latestRestingHeartRate(): Int? {
        val client = client() ?: return null
        if (!canReadRestingHeartRate()) return null
        return try {
            val now = Instant.now()
            client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minus(30, ChronoUnit.DAYS), now),
                    ascendingOrder = false,
                    pageSize = 1,
                )
            ).records.firstOrNull()?.beatsPerMinute?.toInt()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is SecurityException -> "Health Connect did not allow it. Check the permissions."
        is IOException -> "Could not reach Health Connect. Try again."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Health Connect refused the workout."
    }

    private fun String.versionOfEntry(): Long = substringAfterLast('@').toLongOrNull() ?: 0L

    private companion object {
        const val CLEAN_UP_MARGIN_HOURS = 12L
    }
}
