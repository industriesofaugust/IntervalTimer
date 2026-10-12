package com.august.intervaltimer.metrics

import android.content.Context
import android.os.SystemClock
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.august.intervaltimer.sync.HrSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await

data class LiveMetrics(
    val currentHeartRate: Double? = null,
    val averageHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val steps: Long = 0,
    val calories: Double = 0.0,
)

/**
 * Records heart rate, steps, and calories during a workout using Health
 * Services — the watch's battery-efficient sensor pipeline. The system
 * batches sensor readings and delivers them to the callback below.
 */
class WorkoutMetricsRecorder(context: Context) {

    private val exerciseClient = HealthServices.getClient(context).exerciseClient

    private val heartRateSamples = mutableListOf<Double>()
    private val heartRateTimeline = mutableListOf<HrSample>()
    private var startedAtRealtime = 0L
    private var totalSteps = 0L
    private var totalCalories = 0.0
    private var recording = false

    private val _live = MutableStateFlow(LiveMetrics())
    val live: StateFlow<LiveMetrics> = _live.asStateFlow()

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            update.latestMetrics.getData(DataType.HEART_RATE_BPM).forEach { sample ->
                heartRateSamples.add(sample.value)
                val elapsed = (SystemClock.elapsedRealtime() - startedAtRealtime) / 1000
                heartRateTimeline.add(HrSample(elapsed.toInt(), sample.value))
            }
            update.latestMetrics.getData(DataType.STEPS).forEach { delta ->
                totalSteps += delta.value
            }
            update.latestMetrics.getData(DataType.CALORIES).forEach { delta ->
                totalCalories += delta.value
            }
            _live.value = LiveMetrics(
                currentHeartRate = heartRateSamples.lastOrNull(),
                averageHeartRate = heartRateSamples.takeIf { it.isNotEmpty() }?.average(),
                maxHeartRate = heartRateSamples.maxOrNull(),
                steps = totalSteps,
                calories = totalCalories,
            )
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {}
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
    }

    /** Safe to call repeatedly; only the first successful call starts anything. */
    /** Everything gathered so far, timed from the start of recording. */
    fun heartRateSeries(): List<HrSample> = heartRateTimeline.toList()

    suspend fun start() {
        if (recording) return
        heartRateSamples.clear()
        heartRateTimeline.clear()
        startedAtRealtime = SystemClock.elapsedRealtime()
        totalSteps = 0
        totalCalories = 0.0
        _live.value = LiveMetrics()
        runCatching {
            exerciseClient.setUpdateCallback(callback)
            val config = ExerciseConfig.builder(ExerciseType.HIGH_INTENSITY_INTERVAL_TRAINING)
                .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS, DataType.CALORIES))
                .setIsGpsEnabled(false)
                .setIsAutoPauseAndResumeEnabled(false)
                .build()
            exerciseClient.startExerciseAsync(config).await()
            recording = true
        }
    }

    /** Ends the exercise and returns whatever was gathered. */
    suspend fun stop(): LiveMetrics {
        if (recording) {
            recording = false
            runCatching { exerciseClient.endExerciseAsync().await() }
            runCatching { exerciseClient.clearUpdateCallbackAsync(callback).await() }
        }
        return _live.value
    }
}
