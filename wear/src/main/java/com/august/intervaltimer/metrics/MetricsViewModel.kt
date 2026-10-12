package com.august.intervaltimer.metrics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.ResultSyncCodec
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.sync.WorkoutResult
import com.august.intervaltimer.timer.TimerUiState
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Owns the metrics recorder for the current session and, when the workout
 * finishes, packages the result and sends it to the phone.
 */
class MetricsViewModel(app: Application) : AndroidViewModel(app) {

    private val recorder = WorkoutMetricsRecorder(app)
    private val dataClient = Wearable.getDataClient(app)

    val live = recorder.live

    private var sessionName: String? = null
    private var sessionActive = false

    /** Called when a new workout is picked, before the timer starts. */
    fun prepareNew(name: String?) {
        sessionName = name
        sessionActive = true
        viewModelScope.launch { recorder.stop() }
    }

    fun startRecording() {
        if (!sessionActive) return
        viewModelScope.launch { recorder.start() }
    }

    /** Keeps paused time out of the heart rate, step, and calorie totals. */
    fun setPaused(paused: Boolean) {
        if (!sessionActive) return
        viewModelScope.launch { recorder.setPaused(paused) }
    }

    fun finishAndPublish(state: TimerUiState) {
        if (!sessionActive) return
        sessionActive = false
        viewModelScope.launch {
            val metrics = recorder.stop()
            val completedAt = System.currentTimeMillis()
            // If the user ended early, only count what was actually done.
            val completedList =
                if (state.endedEarly) state.intervals.take(state.completedIntervals)
                else state.intervals
            val workIntervals = completedList.filter { it.type == IntervalType.WORK }
            // Thin the series so the payload stays small on long workouts.
            val series = recorder.heartRateSeries().let { all ->
                if (all.size <= 600) all
                else {
                    val step = all.size / 600 + 1
                    all.filterIndexed { index, _ -> index % step == 0 }
                }
            }
            val result = WorkoutResult(
                completedAt = completedAt,
                templateName = sessionName,
                totalSeconds = state.elapsedSecondsAtEnd,
                workSeconds = workIntervals.sumOf { it.durationSeconds },
                workIntervals = workIntervals.size,
                avgHeartRate = metrics.averageHeartRate,
                maxHeartRate = metrics.maxHeartRate,
                steps = metrics.steps.takeIf { it > 0 },
                calories = metrics.calories.takeIf { it > 0 },
                hrSeries = series,
            )
            // Data items persist, so the result reaches the phone even if
            // it isn't nearby right now.
            runCatching {
                val request = PutDataMapRequest
                    .create("${SyncPaths.RESULTS_PATH_PREFIX}/$completedAt")
                    .apply { dataMap.putString(SyncPaths.RESULT_KEY, ResultSyncCodec.encode(result)) }
                    .asPutDataRequest()
                    .setUrgent()
                dataClient.putDataItem(request).await()
            }
        }
    }
}
