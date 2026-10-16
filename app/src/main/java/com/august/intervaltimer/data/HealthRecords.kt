package com.august.intervaltimer.data

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import com.august.intervaltimer.timer.PieceKind
import com.august.intervaltimer.timer.SessionPiece
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.timer.sessionPieces
import java.time.Instant
import java.time.ZoneId

// Health Connect replaces a saved record only by a higher version. The watch's
// result has the heart rate, so it beats the phone's own copy of the workout.
const val PHONE_VERSION = 1L
const val WATCH_VERSION = 2L

/** One key per workout: the live link's id, which the phone's and the watch's copies share. */
fun WorkoutResultEntity.healthKey(): String = linkId ?: "local-$id"

/** Which of the stored results came from the watch, for those saved before that was tracked. */
fun WorkoutResultEntity.looksLikeWatchResult(): Boolean =
    avgHeartRate != null || hrSeriesJson != null || steps != null || calories != null

fun healthVersion(fromWatch: Boolean): Long = if (fromWatch) WATCH_VERSION else PHONE_VERSION

/** The id each of the workout's records is saved under. */
private fun id(key: String, part: String) = "$key-$part"

fun healthSessionId(key: String) = id(key, "session")
fun healthHeartRateId(key: String) = id(key, "hr")
fun healthStepsId(key: String) = id(key, "steps")
fun healthCaloriesId(key: String) = id(key, "kcal")

/**
 * The Health Connect records for one workout. Each has a fixed id and
 * version, so saving the same workout again replaces its records instead of
 * adding more.
 */
fun buildHealthRecords(result: WorkoutResultEntity, fromWatch: Boolean): List<Record> {
    val key = result.healthKey()
    val version = healthVersion(fromWatch)
    val device = Device(type = if (fromWatch) Device.TYPE_WATCH else Device.TYPE_PHONE)
    fun metadata(recordId: String) = Metadata.activelyRecorded(device, recordId, version)

    val timing = result.timing()
    val pieces = sessionPieces(timing.spans, timing.pauses, result.intervals())
    // Workouts saved before the real timing was kept: end at the save, and
    // count back by the time worked (pauses are not in that time).
    val startMillis = pieces.firstOrNull()?.startMillis
        ?: (result.completedAt - result.totalSeconds.toLong().coerceAtLeast(1) * 1000)
    val endMillis = pieces.lastOrNull()?.endMillis ?: result.completedAt
    val start = Instant.ofEpochMilli(startMillis)
    val end = Instant.ofEpochMilli(endMillis)
    val zone = ZoneId.systemDefault().rules
    val startOffset = zone.getOffset(start)
    val endOffset = zone.getOffset(end)

    val session = { segments: List<ExerciseSegment> ->
        ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = startOffset,
            endTime = end,
            endZoneOffset = endOffset,
            metadata = metadata(healthSessionId(key)),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            title = result.templateName?.takeIf { it.isNotBlank() } ?: "Interval workout",
            segments = segments,
        )
    }

    return buildList {
        // If Health Connect would reject the segments, the session is still saved without them.
        add(runCatching { session(pieces.mapNotNull { it.toSegment() }) }.getOrNull() ?: session(emptyList()))

        // The watch counts its readings from when it started recording, which is the get-ready.
        val recordingStart = startMillis - TimerUiState.LEAD_IN_MILLIS
        val samples = result.hrSeries().mapNotNull { sample ->
            val time = recordingStart + sample.elapsedSeconds * 1000L
            if (time < startMillis || time > endMillis) return@mapNotNull null
            HeartRateRecord.Sample(
                time = Instant.ofEpochMilli(time),
                beatsPerMinute = sample.bpm.toLong().coerceIn(1, 300),
            )
        }
        if (samples.isNotEmpty()) {
            add(
                HeartRateRecord(
                    startTime = start,
                    startZoneOffset = startOffset,
                    endTime = end,
                    endZoneOffset = endOffset,
                    samples = samples,
                    metadata = metadata(healthHeartRateId(key)),
                )
            )
        }
        result.steps?.takeIf { it > 0 }?.let { steps ->
            add(
                StepsRecord(
                    startTime = start,
                    startZoneOffset = startOffset,
                    endTime = end,
                    endZoneOffset = endOffset,
                    count = steps,
                    metadata = metadata(healthStepsId(key)),
                )
            )
        }
        result.calories?.takeIf { it > 0 }?.let { calories ->
            add(
                TotalCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = startOffset,
                    endTime = end,
                    endZoneOffset = endOffset,
                    energy = Energy.kilocalories(calories),
                    metadata = metadata(healthCaloriesId(key)),
                )
            )
        }
    }
}

private fun SessionPiece.toSegment(): ExerciseSegment? = runCatching {
    ExerciseSegment(
        startTime = Instant.ofEpochMilli(startMillis),
        endTime = Instant.ofEpochMilli(endMillis),
        segmentType = when (kind) {
            PieceKind.WORK -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            PieceKind.REST -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST
            PieceKind.PAUSE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE
        },
    )
}.getOrNull()
