package com.august.intervaltimer.design

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState

/** The two looks the apps can wear. Dial is the default. */
enum class AppStyle { DIAL, MINIMAL }

/** How the big countdown is written: bare seconds ("90") or "1:30". */
enum class CountdownFormat { SECONDS, MINUTES_SECONDS }

/** The big timer number. Every other duration in the apps uses formatDuration. */
fun formatCountdown(seconds: Int, format: CountdownFormat): String = when (format) {
    CountdownFormat.SECONDS -> seconds.toString()
    CountdownFormat.MINUTES_SECONDS -> formatDuration(seconds)
}

/** "1 INTERVAL", "5 INTERVALS". */
fun intervalCountLabel(count: Int): String = if (count == 1) "1 INTERVAL" else "$count INTERVALS"

enum class SegmentState { DONE, CURRENT, UPCOMING }

/**
 * One interval drawn as an arc of the session ring. Angles are in degrees,
 * measured clockwise, with 0 at the top of the dial.
 */
data class RingSegment(
    val startAngle: Float,
    val sweep: Float,
    val type: IntervalType,
    val state: SegmentState,
)

/** One interval drawn as a piece of the flat session strip. */
data class StripSegment(
    val fraction: Float,
    val type: IntervalType,
)

// Above this many intervals the gaps would eat the ring, so segments touch.
private const val MERGE_ABOVE = 40

// No interval is drawn thinner than this share of the session, so a 5-second
// rest next to a 5-minute work interval is still visible.
private const val MIN_SHARE = 0.012f

/** How much room each interval gets, as fractions that add up to 1. */
private fun shares(intervals: List<Interval>): List<Float> {
    if (intervals.isEmpty()) return emptyList()
    val total = intervals.sumOf { it.durationSeconds }.coerceAtLeast(1).toFloat()
    val raw = intervals.map { (it.durationSeconds / total).coerceAtLeast(MIN_SHARE) }
    val sum = raw.sum()
    return raw.map { it / sum }
}

/** Degrees left empty between neighbouring segments. */
fun ringGapDegrees(intervalCount: Int): Float = when {
    intervalCount <= 1 -> 0f
    intervalCount <= 12 -> 4f
    intervalCount <= 24 -> 3f
    intervalCount <= MERGE_ABOVE -> 2f
    else -> 0f
}

/**
 * Lays a session out around a dial: each interval gets an arc sized by its
 * duration. Intervals before [doneCount] are DONE, the one at [currentIndex]
 * is CURRENT, the rest are UPCOMING.
 */
fun ringSegments(
    intervals: List<Interval>,
    doneCount: Int = 0,
    currentIndex: Int = -1,
): List<RingSegment> {
    if (intervals.isEmpty()) return emptyList()
    val gap = ringGapDegrees(intervals.size)
    val usable = 360f - gap * intervals.size
    var angle = gap / 2f
    return shares(intervals).mapIndexed { index, share ->
        val sweep = usable * share
        val segment = RingSegment(
            startAngle = angle,
            sweep = sweep,
            type = intervals[index].type,
            state = when {
                index == currentIndex -> SegmentState.CURRENT
                index < doneCount -> SegmentState.DONE
                else -> SegmentState.UPCOMING
            },
        )
        angle += sweep + gap
        segment
    }
}

/**
 * The ring for a running or finished timer, from just the fields that decide
 * its shape. Screens that watch those fields separately use this directly, so
 * they don't rebuild the ring on every tick.
 */
fun ringSegments(
    phase: TimerPhase,
    intervals: List<Interval>,
    currentIndex: Int,
    completedIntervals: Int,
): List<RingSegment> = when (phase) {
    TimerPhase.LEAD_IN -> ringSegments(intervals)
    TimerPhase.RUNNING -> ringSegments(intervals, doneCount = currentIndex, currentIndex = currentIndex)
    TimerPhase.FINISHED -> ringSegments(intervals, doneCount = completedIntervals)
}

/** The ring for a running or finished timer. */
fun TimerUiState.ringSegments(): List<RingSegment> =
    ringSegments(phase, intervals, currentIndex, completedIntervals)

/** How much of the current interval is left, from 1 (just started) to 0. */
val TimerUiState.currentRemainingFraction: Float
    get() = (remainingMillis.toFloat() / currentDurationMillis).coerceIn(0f, 1f)

/** The same session as a flat bar, for cards and lists. */
fun stripSegments(intervals: List<Interval>): List<StripSegment> =
    shares(intervals).mapIndexed { index, share -> StripSegment(share, intervals[index].type) }
