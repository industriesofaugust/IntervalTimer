package com.august.intervaltimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import com.august.intervaltimer.design.RingSegment
import com.august.intervaltimer.design.currentRemainingFraction
import com.august.intervaltimer.design.ringSegments
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.workout.WorkoutSession

/**
 * The running timer as the screens read it. The timer state changes many
 * times a second, but most of what is on screen (the label, the exercise
 * name, the buttons) changes rarely. Each field here only reports a change
 * when its own value changes, so a Text recomposes only when its string does.
 *
 * The one thing that moves every tick, the fill of the ring, is read through
 * [remainingFraction] and [sessionFraction] inside draw code, which redraws
 * without recomposing anything.
 */
@Stable
class TimerView(private val source: State<TimerUiState>) {

    val phase by derivedStateOf { source.value.phase }
    val isPaused by derivedStateOf { source.value.isPaused }
    val currentIndex by derivedStateOf { source.value.currentIndex }
    val remainingSeconds by derivedStateOf { source.value.remainingSeconds }
    val completedIntervals by derivedStateOf { source.value.completedIntervals }
    val endedEarly by derivedStateOf { source.value.endedEarly }
    val elapsedSecondsAtEnd by derivedStateOf { source.value.elapsedSecondsAtEnd }

    // The same list object for the whole workout; comparing it by reference
    // avoids walking a long list on every tick.
    val intervals: List<Interval> by derivedStateOf(referentialEqualityPolicy()) {
        source.value.intervals
    }

    val currentInterval: Interval? by derivedStateOf { source.value.currentInterval }
    val nextInterval: Interval? by derivedStateOf { source.value.nextInterval }

    /** How much of the current interval is left, from 1 to 0. Call from draw code only. */
    fun remainingFraction(): Float = source.value.currentRemainingFraction

    /** How far through the whole session we are, from 0 to 1. Call from draw code only. */
    fun sessionFraction(): Float = source.value.sessionProgress
}

/** The ring's arcs, rebuilt only when the interval or phase changes. */
@Composable
fun TimerView.rememberRingSegments(): List<RingSegment> {
    val phase = phase
    val intervals = intervals
    val currentIndex = currentIndex
    val completedIntervals = completedIntervals
    return remember(phase, intervals, currentIndex, completedIntervals) {
        ringSegments(phase, intervals, currentIndex, completedIntervals)
    }
}

/**
 * True while a workout is in progress (started and not finished). It changes
 * only when a workout starts or ends, not on every tick, so reading it does
 * not make a screen recompose 10 times a second.
 */
@Composable
fun rememberWorkoutRunning(): State<Boolean> {
    val session = WorkoutSession.state.collectAsState()
    return remember {
        derivedStateOf {
            session.value.intervals.isNotEmpty() && session.value.phase != TimerPhase.FINISHED
        }
    }
}
