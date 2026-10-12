package com.august.intervaltimer.timer

import android.os.SystemClock
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TimerPhase { LEAD_IN, RUNNING, FINISHED }

/**
 * Moments the UI should signal to the user. The engine only announces them;
 * each device decides how to respond (phone beeps, watch vibrates).
 */
enum class TimerCue { COUNTDOWN_TICK, WORK_START, REST_START, FINISH }

data class TimerUiState(
    val intervals: List<Interval> = emptyList(),
    val currentIndex: Int = 0,
    val remainingMillis: Long = 0L,
    val phase: TimerPhase = TimerPhase.LEAD_IN,
    val isPaused: Boolean = false,
    val endedEarly: Boolean = false,
    val completedIntervals: Int = 0,
    val elapsedSecondsAtEnd: Int = 0,
) {
    val currentInterval: Interval? get() = intervals.getOrNull(currentIndex)

    val nextInterval: Interval? get() = when (phase) {
        TimerPhase.LEAD_IN -> intervals.firstOrNull()
        TimerPhase.RUNNING -> intervals.getOrNull(currentIndex + 1)
        TimerPhase.FINISHED -> null
    }

    /** Remaining time rounded up, so "1" shows for the whole final second. */
    val remainingSeconds: Int get() = ((remainingMillis + 999) / 1000).toInt()

    val currentDurationMillis: Long get() = when (phase) {
        TimerPhase.LEAD_IN -> LEAD_IN_MILLIS
        else -> (currentInterval?.durationSeconds ?: 1) * 1000L
    }

    val totalPlannedMillis: Long get() = intervals.sumOf { it.durationSeconds * 1000L }

    /** How much of the session has actually run, in milliseconds. */
    val elapsedMillis: Long get() = when (phase) {
        TimerPhase.LEAD_IN -> 0L
        TimerPhase.FINISHED ->
            if (endedEarly) elapsedSecondsAtEnd * 1000L else totalPlannedMillis
        TimerPhase.RUNNING ->
            intervals.take(currentIndex).sumOf { it.durationSeconds * 1000L } +
                (currentDurationMillis - remainingMillis)
    }

    /** Fraction of the whole session completed, for overall progress bars. */
    val sessionProgress: Float get() {
        val totalMillis = totalPlannedMillis
        if (totalMillis == 0L) return 0f
        return (elapsedMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    }

    companion object {
        const val LEAD_IN_MILLIS = 5_000L
    }
}

/**
 * The countdown itself. It only needs a coroutine scope to run in, so the
 * phone can host it in a ViewModel and the watch in a foreground service.
 * The clock is a parameter so tests can control time.
 */
class TimerEngine(
    private val scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {

    /**
     * How often the countdown is recalculated. 50 ms keeps the progress ring
     * smooth; a host with nothing on screen can raise it to save battery.
     */
    @Volatile
    var tickMillis: Long = 50L

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private val _cues = MutableSharedFlow<TimerCue>(extraBufferCapacity = 8)
    val cues: SharedFlow<TimerCue> = _cues.asSharedFlow()

    private var ticker: Job? = null

    fun start(intervals: List<Interval>) {
        if (intervals.isEmpty()) return
        _uiState.value = TimerUiState(
            intervals = intervals,
            remainingMillis = TimerUiState.LEAD_IN_MILLIS,
        )
        startTicker()
    }

    fun togglePause() {
        val state = _uiState.value
        if (state.phase == TimerPhase.FINISHED) return
        _uiState.value = state.copy(isPaused = !state.isPaused)
    }

    fun skip() {
        val state = _uiState.value
        if (state.phase == TimerPhase.FINISHED) return
        advance(carryMillis = 0L)
    }

    fun stop() {
        ticker?.cancel()
        _uiState.value = TimerUiState()
    }

    /** Ends the session now, keeping credit for the intervals already done. */
    fun endEarly() {
        val state = _uiState.value
        if (state.phase == TimerPhase.FINISHED || state.intervals.isEmpty()) return
        ticker?.cancel()
        val completed = if (state.phase == TimerPhase.RUNNING) state.currentIndex else 0
        val elapsedSeconds = (state.elapsedMillis / 1000).toInt()
        _cues.tryEmit(TimerCue.FINISH)
        _uiState.value = state.copy(
            phase = TimerPhase.FINISHED,
            remainingMillis = 0L,
            isPaused = false,
            endedEarly = true,
            completedIntervals = completed,
            elapsedSecondsAtEnd = elapsedSeconds,
        )
    }

    /**
     * The ticker measures real elapsed time between iterations instead of
     * assuming a fixed step, so the countdown never drifts.
     */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var lastTick = clock()
            while (isActive) {
                delay(tickMillis)
                val now = clock()
                val elapsed = now - lastTick
                lastTick = now
                onTick(elapsed)
            }
        }
    }

    private fun onTick(elapsedMillis: Long) {
        val state = _uiState.value
        if (state.isPaused || state.phase == TimerPhase.FINISHED) return

        val remaining = state.remainingMillis - elapsedMillis
        if (remaining <= 0) {
            advance(carryMillis = remaining)
            return
        }
        val previousSecond = state.remainingSeconds
        val newSecond = ((remaining + 999) / 1000).toInt()
        if (newSecond != previousSecond && newSecond <= 3) {
            _cues.tryEmit(TimerCue.COUNTDOWN_TICK)
        }
        _uiState.value = state.copy(remainingMillis = remaining)
    }

    /** carryMillis is the (negative) overshoot past zero, kept for accuracy. */
    private fun advance(carryMillis: Long) {
        val state = _uiState.value
        val nextIndex = if (state.phase == TimerPhase.LEAD_IN) 0 else state.currentIndex + 1
        val next = state.intervals.getOrNull(nextIndex)
        if (next == null) {
            _cues.tryEmit(TimerCue.FINISH)
            _uiState.value = state.copy(
                phase = TimerPhase.FINISHED,
                remainingMillis = 0L,
                isPaused = false,
                completedIntervals = state.intervals.size,
                elapsedSecondsAtEnd = (state.totalPlannedMillis / 1000).toInt(),
            )
            ticker?.cancel()
            return
        }
        _cues.tryEmit(
            when (next.type) {
                IntervalType.WORK -> TimerCue.WORK_START
                IntervalType.REST -> TimerCue.REST_START
            }
        )
        _uiState.value = state.copy(
            phase = TimerPhase.RUNNING,
            currentIndex = nextIndex,
            remainingMillis = next.durationSeconds * 1000L + carryMillis,
        )
    }
}
