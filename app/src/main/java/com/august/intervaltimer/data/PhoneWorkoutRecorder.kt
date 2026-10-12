package com.august.intervaltimer.data

import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.WorkoutResult
import com.august.intervaltimer.timer.TimerUiState

/**
 * Saves a workout that was run on the phone, so it shows up in History and
 * the Activity stats next to the ones from the watch. The phone has no
 * sensors, so there is no heart rate, step, or calorie data.
 */
suspend fun TimerApp.recordPhoneWorkout(name: String?, finished: TimerUiState) {
    // If the user ended early, only count what was actually done.
    val completed =
        if (finished.endedEarly) finished.intervals.take(finished.completedIntervals)
        else finished.intervals
    val workIntervals = completed.filter { it.type == IntervalType.WORK }

    val entity = resultsRepository.insert(
        WorkoutResult(
            completedAt = System.currentTimeMillis(),
            templateName = name,
            totalSeconds = finished.elapsedSecondsAtEnd,
            workSeconds = workIntervals.sumOf { it.durationSeconds },
            workIntervals = workIntervals.size,
        )
    )
    val healthConnect = HealthConnectWriter(this)
    if (healthConnect.hasAllPermissions()) healthConnect.write(entity)
}
