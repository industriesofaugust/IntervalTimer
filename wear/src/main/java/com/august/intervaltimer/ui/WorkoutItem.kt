package com.august.intervaltimer.ui

import androidx.compose.runtime.Immutable
import com.august.intervaltimer.design.StripSegment
import com.august.intervaltimer.design.intervalCountLabel
import com.august.intervaltimer.design.stripSegments
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.model.starterTemplates

/**
 * A workout as the lists show it. The texts and the strip are worked out
 * once, when the item is made, so scrolling and coming back to a screen
 * never repeat that work.
 */
@Immutable
class WorkoutItem(val name: String, val intervals: List<Interval>) {

    /** "15 INTERVALS". */
    val countLabel: String = intervalCountLabel(intervals.size)

    /** "3:50". */
    val totalLabel: String = formatDuration(intervals.sumOf { it.durationSeconds })

    /** "15 INTERVALS · 3:50". */
    val summary: String = "$countLabel · $totalLabel"

    val strip: List<StripSegment> = stripSegments(intervals)
}

/** The starter workouts, always available even before the first sync. */
val starterWorkouts: List<WorkoutItem> =
    starterTemplates.map { WorkoutItem(it.name, it.intervals()) }
