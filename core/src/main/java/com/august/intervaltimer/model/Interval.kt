package com.august.intervaltimer.model

enum class IntervalType { WORK, REST }

data class Interval(
    val name: String,
    val type: IntervalType,
    val durationSeconds: Int,
)

data class QuickBuildConfig(
    val rounds: Int,
    val workSeconds: Int,
    val restSeconds: Int,
)

/**
 * Expands a quick-build config into a flat list of intervals.
 * No trailing rest after the final work interval.
 */
fun buildSession(config: QuickBuildConfig): List<Interval> = buildList {
    repeat(config.rounds) { round ->
        add(
            Interval(
                name = "Work ${round + 1} of ${config.rounds}",
                type = IntervalType.WORK,
                durationSeconds = config.workSeconds,
            )
        )
        if (round < config.rounds - 1) {
            add(
                Interval(
                    name = "Rest",
                    type = IntervalType.REST,
                    durationSeconds = config.restSeconds,
                )
            )
        }
    }
}

fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
