package com.august.intervaltimer.metrics

import android.content.Context
import android.os.SystemClock
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.august.intervaltimer.sync.HrSample
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

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
    private var isPaused = false
    private var ended: CompletableDeferred<Unit>? = null

    private val _live = MutableStateFlow(LiveMetrics())
    val live: StateFlow<LiveMetrics> = _live.asStateFlow()

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            update.latestMetrics.getData(DataType.HEART_RATE_BPM).forEach { sample ->
                heartRateSamples.add(sample.value)
                // Readings arrive in batches, so use the time each one was
                // measured, not the time the batch was delivered.
                val measuredAt = sample.timeDurationFromBoot.toMillis()
                val elapsed = ((measuredAt - startedAtRealtime) / 1000).coerceAtLeast(0)
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
            if (update.exerciseStateInfo.state.isEnded) ended?.complete(Unit)
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {}
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
    }

    /** Everything gathered so far, timed from the start of recording. */
    fun heartRateSeries(): List<HrSample> = heartRateTimeline.toList()

    /** Pauses or resumes sensor recording along with the timer. */
    suspend fun setPaused(paused: Boolean) {
        if (!recording || paused == isPaused) return
        isPaused = paused
        runCatching {
            if (paused) exerciseClient.pauseExerciseAsync().await()
            else exerciseClient.resumeExerciseAsync().await()
        }
    }

    // Start and stop never overlap: a stop that arrived while the exercise was
    // still starting used to find nothing to end, and the exercise kept running.
    private val lock = Mutex()

    /** Safe to call repeatedly; only the first successful call starts anything. */
    suspend fun start() = lock.withLock {
        if (recording) return@withLock
        heartRateSamples.clear()
        heartRateTimeline.clear()
        startedAtRealtime = SystemClock.elapsedRealtime()
        totalSteps = 0
        totalCalories = 0.0
        _live.value = LiveMetrics()
        runCatching {
            exerciseClient.setUpdateCallback(callback)
            // With the screen off the watch otherwise holds readings back for
            // minutes; ask for heart rate every few seconds where supported.
            val capabilities = exerciseClient.getCapabilitiesAsync().await()
            val batching = setOf(BatchingMode.HEART_RATE_5_SECONDS)
                .filter { it in capabilities.supportedBatchingModeOverrides }
                .toSet()
            val config = ExerciseConfig.builder(ExerciseType.HIGH_INTENSITY_INTERVAL_TRAINING)
                .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS, DataType.CALORIES))
                .setIsGpsEnabled(false)
                .setIsAutoPauseAndResumeEnabled(false)
                .setBatchingModeOverrides(batching)
                .build()
            exerciseClient.startExerciseAsync(config).await()
            recording = true
            isPaused = false
        }
    }

    /** Ends the exercise and returns whatever was gathered. */
    suspend fun stop(): LiveMetrics = lock.withLock {
        if (recording || ownsRunningExercise()) {
            recording = false
            // Readings still held back by the watch arrive in one last update
            // after ending; wait for it so the end of the workout isn't lost.
            val endedSignal = CompletableDeferred<Unit>()
            ended = endedSignal
            runCatching { exerciseClient.endExerciseAsync().await() }
            withTimeoutOrNull(3_000) { endedSignal.await() }
            ended = null
            runCatching { exerciseClient.clearUpdateCallbackAsync(callback).await() }
        }
        _live.value
    }

    /**
     * Health Services keeps an exercise going even if the app that started it
     * is closed or killed, and while it runs the watch's own all-day heart
     * rate tracking stays paused. Call when no workout is in progress to end
     * any exercise this app left behind.
     */
    suspend fun endLeftoverExercise() = lock.withLock {
        if (recording || !ownsRunningExercise()) return@withLock
        runCatching { exerciseClient.endExerciseAsync().await() }
        runCatching { exerciseClient.clearUpdateCallbackAsync(callback).await() }
    }

    private suspend fun ownsRunningExercise(): Boolean = runCatching {
        exerciseClient.getCurrentExerciseInfoAsync().await().exerciseTrackedStatus ==
            ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS
    }.getOrDefault(false)
}
