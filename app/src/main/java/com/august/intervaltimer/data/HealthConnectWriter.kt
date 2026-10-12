package com.august.intervaltimer.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import java.time.Instant

/**
 * Writes finished workouts into Health Connect, the shared on-device health
 * store that Fitbit, Google Fit, and other apps read from. Everything here
 * is best-effort: if Health Connect is missing or permission was never
 * granted, we simply skip writing.
 */
class HealthConnectWriter(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    suspend fun hasAllPermissions(): Boolean {
        val client = client() ?: return false
        return runCatching {
            client.permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
    }

    suspend fun write(result: WorkoutResultEntity) {
        val client = client() ?: return
        val endTime = Instant.ofEpochMilli(result.completedAt)
        val startTime = endTime.minusSeconds(result.totalSeconds.toLong().coerceAtLeast(1))

        val records = buildList<Record> {
            add(
                ExerciseSessionRecord(
                    metadata = Metadata.manualEntry(),
                    startTime = startTime,
                    startZoneOffset = null,
                    endTime = endTime,
                    endZoneOffset = null,
                    exerciseType =
                        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
                    title = result.templateName ?: "Interval workout",
                )
            )
            val samples = result.hrSeries()
                .map { sample ->
                    HeartRateRecord.Sample(
                        time = startTime.plusSeconds(sample.elapsedSeconds.toLong())
                            .coerceAtMost(endTime),
                        beatsPerMinute = sample.bpm.toLong(),
                    )
                }
            if (samples.isNotEmpty()) {
                add(
                    HeartRateRecord(
                        metadata = Metadata.manualEntry(),
                        startTime = startTime,
                        startZoneOffset = null,
                        endTime = endTime,
                        endZoneOffset = null,
                        samples = samples,
                    )
                )
            }
            result.steps?.let { steps ->
                add(
                    StepsRecord(
                        metadata = Metadata.manualEntry(),
                        startTime = startTime,
                        startZoneOffset = null,
                        endTime = endTime,
                        endZoneOffset = null,
                        count = steps,
                    )
                )
            }
            result.calories?.let { calories ->
                add(
                    TotalCaloriesBurnedRecord(
                        metadata = Metadata.manualEntry(),
                        startTime = startTime,
                        startZoneOffset = null,
                        endTime = endTime,
                        endZoneOffset = null,
                        energy = Energy.kilocalories(calories),
                    )
                )
            }
        }
        runCatching { client.insertRecords(records) }
    }
}

private fun Instant.coerceAtMost(other: Instant): Instant = if (isAfter(other)) other else this
