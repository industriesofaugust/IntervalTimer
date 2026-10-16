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
    /** Every interval that has finished running, with when it really started and ended. */
    val spans: List<IntervalSpan> = emptyList(),
    /** Every stretch the user had the workout paused. */
    val pauses: List<PauseSpan> = emptyList(),
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
 * The clocks are parameters so tests can control time: [clock] drives the
 * countdown, [wallClock] (epoch millis) stamps when each interval really ran.
 */
class TimerEngine(
    private val scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
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

    // When the interval now running began, and when the current pause began.
    // Null when there is no such thing; the finished ones live in the state.
    private var intervalStartedAt: Long? = null
    private var pauseStartedAt: Long? = null

    /**
     * [elapsedMillis] starts the session that far in, so a second device can
     * join a session that began a moment ago. No cue is played for the
     * interval it lands in.
     */
    fun start(intervals: List<Interval>, elapsedMillis: Long = 0L) {
        if (intervals.isEmpty()) return
        val state = stateAfter(intervals, elapsedMillis)
        pauseStartedAt = null
        intervalStartedAt =
            if (state.phase == TimerPhase.RUNNING) {
                wallClock() - (state.currentDurationMillis - state.remainingMillis)
            } else null
        _uiState.value = state
        startTicker()
    }

    /**
     * Snaps to a position reported by another device. [index] -1 is the
     * get-ready lead-in; an index past the last interval finishes the
     * session. The cue for an interval is played only if this moves into a
     * different interval than the engine was already in, so a device that
     * already got there by itself stays quiet.
     */
    fun syncTo(index: Int, remainingMillis: Long, paused: Boolean) {
        val state = _uiState.value
        if (state.intervals.isEmpty() || state.phase == TimerPhase.FINISHED) return
        val now = wallClock()
        if (index >= state.intervals.size) {
            finish(state, now)
            return
        }
        if (index < 0) {
            _uiState.value = closeInterval(state, now).copy(
                phase = TimerPhase.LEAD_IN,
                currentIndex = 0,
                remainingMillis = remainingMillis.coerceIn(0L, TimerUiState.LEAD_IN_MILLIS),
            ).withPaused(paused, now)
            return
        }
        val interval = state.intervals[index]
        val movedToNewInterval = state.phase != TimerPhase.RUNNING || state.currentIndex != index
        val remaining = remainingMillis.coerceIn(0L, interval.durationSeconds * 1000L)
        var next = state
        if (movedToNewInterval) {
            _cues.tryEmit(startCue(interval))
            // The other device says how far in it is, so that is when this one began.
            val startedAt = now - (interval.durationSeconds * 1000L - remaining)
            val previousStart = intervalStartedAt ?: startedAt
            next = closeInterval(state, maxOf(startedAt, previousStart))
            intervalStartedAt = maxOf(startedAt, previousStart)
        }
        _uiState.value = next.copy(
            phase = TimerPhase.RUNNING,
            currentIndex = index,
            remainingMillis = remaining,
        ).withPaused(paused, now)
    }

    fun togglePause() {
        val state = _uiState.value
        if (state.phase == TimerPhase.FINISHED) return
        _uiState.value = state.withPaused(!state.isPaused, wallClock())
    }

    fun skip() {
        val state = _uiState.value
        if (state.phase == TimerPhase.FINISHED) return
        advance(carryMillis = 0L)
    }

    fun stop() {
        ticker?.cancel()
        intervalStartedAt = null
        pauseStartedAt = null
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
        _uiState.value = closeAll(state, wallClock()).copy(
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
        // The old interval really ended at the moment its time ran out.
        val boundary = wallClock() + carryMillis
        if (next == null) {
            finish(state, boundary)
            return
        }
        _cues.tryEmit(startCue(next))
        val closed = closeInterval(state, boundary)
        intervalStartedAt = boundary
        _uiState.value = closed.copy(
            phase = TimerPhase.RUNNING,
            currentIndex = nextIndex,
            remainingMillis = next.durationSeconds * 1000L + carryMillis,
        )
    }

    /** Records the interval that was running as finished at [endMillis]. */
    private fun closeInterval(state: TimerUiState, endMillis: Long): TimerUiState {
        val startedAt = intervalStartedAt
        intervalStartedAt = null
        if (state.phase != TimerPhase.RUNNING || startedAt == null || endMillis <= startedAt) {
            return state
        }
        return state.copy(spans = state.spans + IntervalSpan(state.currentIndex, startedAt, endMillis))
    }

    /** Closes the running interval and any pause that is still open. */
    private fun closeAll(state: TimerUiState, endMillis: Long): TimerUiState {
        val closed = closeInterval(state, endMillis)
        return if (closed.isPaused) closed.withPaused(false, endMillis) else closed
    }

    /** Starts or ends a pause at [now], keeping the finished ones in the state. */
    private fun TimerUiState.withPaused(paused: Boolean, now: Long): TimerUiState {
        if (paused == isPaused) return this
        if (paused) {
            pauseStartedAt = now
            return copy(isPaused = true)
        }
        val startedAt = pauseStartedAt
        pauseStartedAt = null
        val finished = if (startedAt != null && now > startedAt) pauses + PauseSpan(startedAt, now) else pauses
        return copy(isPaused = false, pauses = finished)
    }

    /** The session ran to its end, at [endMillis] on the wall clock. */
    private fun finish(state: TimerUiState, endMillis: Long) {
        _cues.tryEmit(TimerCue.FINISH)
        _uiState.value = closeAll(state, endMillis).copy(
            phase = TimerPhase.FINISHED,
            remainingMillis = 0L,
            isPaused = false,
            completedIntervals = state.intervals.size,
            elapsedSecondsAtEnd = (state.totalPlannedMillis / 1000).toInt(),
        )
        ticker?.cancel()
    }

    private fun startCue(interval: Interval): TimerCue = when (interval.type) {
        IntervalType.WORK -> TimerCue.WORK_START
        IntervalType.REST -> TimerCue.REST_START
    }

    /** Where a session stands after [elapsedMillis] of running from the very start. */
    private fun stateAfter(intervals: List<Interval>, elapsedMillis: Long): TimerUiState {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        if (elapsed < TimerUiState.LEAD_IN_MILLIS) {
            return TimerUiState(
                intervals = intervals,
                remainingMillis = TimerUiState.LEAD_IN_MILLIS - elapsed,
            )
        }
        var intoIntervals = elapsed - TimerUiState.LEAD_IN_MILLIS
        intervals.forEachIndexed { index, interval ->
            val duration = interval.durationSeconds * 1000L
            if (intoIntervals < duration) {
                return TimerUiState(
                    intervals = intervals,
                    currentIndex = index,
                    remainingMillis = duration - intoIntervals,
                    phase = TimerPhase.RUNNING,
                )
            }
            intoIntervals -= duration
        }
        // Past the end: sit on the last millisecond, so the next tick finishes.
        return TimerUiState(
            intervals = intervals,
            currentIndex = intervals.lastIndex,
            remainingMillis = 1L,
            phase = TimerPhase.RUNNING,
        )
    }
}
