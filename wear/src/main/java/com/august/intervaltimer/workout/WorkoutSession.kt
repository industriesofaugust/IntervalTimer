package com.august.intervaltimer.workout

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.august.intervaltimer.metrics.LiveMetrics
import com.august.intervaltimer.metrics.WorkoutMetricsRecorder
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.ResultSyncCodec
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.sync.WorkoutResult
import com.august.intervaltimer.sync.toSyncedIntervals
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.timer.TimerEngine
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The one workout the watch can be running. It lives for the whole app
 * process rather than inside a screen, so the countdown carries on when the
 * screen turns off or the user leaves the app. [WorkoutService] keeps the
 * process alive while a workout is running; the screens only watch [state].
 */
object WorkoutSession {

    private val scope = MainScope()
    private val engine = TimerEngine(scope)

    val state: StateFlow<TimerUiState> = engine.uiState
    val cues: SharedFlow<TimerCue> = engine.cues

    private val _live = MutableStateFlow(LiveMetrics())
    val live: StateFlow<LiveMetrics> = _live.asStateFlow()

    private var appContext: Context? = null
    private var recorder: WorkoutMetricsRecorder? = null
    private var sessionName: String? = null
    private var resultPublished = true

    val inProgress: Boolean
        get() = state.value.intervals.isNotEmpty() && state.value.phase != TimerPhase.FINISHED

    /** Safe to call from anywhere, any number of times. */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val newRecorder = WorkoutMetricsRecorder(app)
        recorder = newRecorder
        scope.launch { newRecorder.live.collect { _live.value = it } }
    }

    fun start(context: Context, name: String, intervals: List<Interval>) {
        if (intervals.isEmpty()) return
        init(context)
        sessionName = name
        resultPublished = false
        engine.start(intervals)
        val app = context.applicationContext
        ContextCompat.startForegroundService(app, Intent(app, WorkoutService::class.java))
    }

    fun togglePause() = engine.togglePause()
    fun skip() = engine.skip()
    fun endEarly() = engine.endEarly()

    /** Dismisses the finished summary and returns to "no workout". */
    fun clear() = engine.stop()

    /**
     * The ring only needs smooth updates while someone is looking at it.
     * With the screen off or dimmed, a slower tick saves battery.
     */
    fun setSmoothTicks(smooth: Boolean) {
        engine.tickMillis = if (smooth) 50L else 200L
    }

    // --- Called by WorkoutService, which runs in the foreground. ---

    suspend fun beginRecording() {
        recorder?.stop()
        recorder?.start()
    }

    /** Keeps paused time out of the heart rate, step, and calorie totals. */
    suspend fun setRecordingPaused(paused: Boolean) {
        recorder?.setPaused(paused)
    }

    /** Stops the sensors and sends the finished workout to the phone, once. */
    suspend fun finishAndPublish() {
        if (resultPublished) return
        resultPublished = true
        val app = appContext ?: return
        val activeRecorder = recorder ?: return
        val finished = state.value

        val metrics = activeRecorder.stop()
        val completedAt = System.currentTimeMillis()
        // If the user ended early, only count what was actually done.
        val completedList =
            if (finished.endedEarly) finished.intervals.take(finished.completedIntervals)
            else finished.intervals
        val workIntervals = completedList.filter { it.type == IntervalType.WORK }
        // Thin the series so the payload stays small on long workouts.
        val series = activeRecorder.heartRateSeries().let { all ->
            if (all.size <= 600) all
            else {
                val step = all.size / 600 + 1
                all.filterIndexed { index, _ -> index % step == 0 }
            }
        }
        val result = WorkoutResult(
            completedAt = completedAt,
            templateName = sessionName,
            totalSeconds = finished.elapsedSecondsAtEnd,
            workSeconds = workIntervals.sumOf { it.durationSeconds },
            workIntervals = workIntervals.size,
            avgHeartRate = metrics.averageHeartRate,
            maxHeartRate = metrics.maxHeartRate,
            steps = metrics.steps.takeIf { it > 0 },
            calories = metrics.calories.takeIf { it > 0 },
            hrSeries = series,
            intervals = finished.intervals.toSyncedIntervals(),
            completedIntervals = finished.completedIntervals,
        )
        // Data items persist, so the result reaches the phone even if it
        // isn't nearby right now.
        runCatching {
            val request = PutDataMapRequest
                .create("${SyncPaths.RESULTS_PATH_PREFIX}/$completedAt")
                .apply { dataMap.putString(SyncPaths.RESULT_KEY, ResultSyncCodec.encode(result)) }
                .asPutDataRequest()
                .setUrgent()
            Wearable.getDataClient(app).putDataItem(request).await()
        }
    }
}
