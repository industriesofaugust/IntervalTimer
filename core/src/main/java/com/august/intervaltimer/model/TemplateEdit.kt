package com.august.intervaltimer.model

// Pure edits on the flat interval list, used by the template editor. Each one
// returns a new list; none of them reorders the intervals it keeps.

const val MAX_ROUNDS = 50
const val DEFAULT_WORK_SECONDS = 40
const val DEFAULT_REST_SECONDS = 20

private const val WORK_NAME = "Work"
private const val REST_NAME = "Rest"
private val numberedWorkName = Regex("Work \\d+ of \\d+")

/** A round is one work interval, so this is the number of rounds. */
fun workCount(intervals: List<Interval>): Int = intervals.count { it.type == IntervalType.WORK }

/** The duration of the last interval of this type, or the default when there is none. */
fun lastDurationOf(intervals: List<Interval>, type: IntervalType): Int =
    intervals.lastOrNull { it.type == type }?.durationSeconds
        ?: if (type == IntervalType.WORK) DEFAULT_WORK_SECONDS else DEFAULT_REST_SECONDS

/** The one duration every interval of this type shares; null if they differ or there are none. */
fun commonDuration(intervals: List<Interval>, type: IntervalType): Int? =
    intervals.filter { it.type == type }.map { it.durationSeconds }.distinct().singleOrNull()

fun withAllDurations(intervals: List<Interval>, type: IntervalType, seconds: Int): List<Interval> =
    intervals.map {
        if (it.type == type) it.copy(durationSeconds = seconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)) else it
    }

/**
 * Gives the list this many work intervals. Fewer: keeps everything up to the
 * last work interval that is left and drops what follows, trailing rests
 * included. More: appends a rest (unless the list is empty or already ends in
 * one) and a work interval for each new round, timed like the last of their
 * type.
 */
fun withRounds(intervals: List<Interval>, rounds: Int): List<Interval> {
    val target = rounds.coerceIn(1, MAX_ROUNDS)
    val current = workCount(intervals)

    if (target < current) {
        val workPositions = intervals.indices.filter { intervals[it].type == IntervalType.WORK }
        return intervals.take(workPositions[target - 1] + 1)
    }

    val workSeconds = lastDurationOf(intervals, IntervalType.WORK)
    val restSeconds = lastDurationOf(intervals, IntervalType.REST)
    return buildList {
        addAll(intervals)
        repeat(target - current) {
            if (isNotEmpty() && last().type != IntervalType.REST) {
                add(Interval(REST_NAME, IntervalType.REST, restSeconds))
            }
            add(Interval(WORK_NAME, IntervalType.WORK, workSeconds))
        }
    }
}

/** The whole list again after it: a rest between, unless the list already ends in one. */
fun repeated(intervals: List<Interval>, restBetweenSeconds: Int): List<Interval> {
    if (intervals.isEmpty()) return intervals
    val rest = if (intervals.last().type == IntervalType.REST) {
        emptyList()
    } else {
        listOf(Interval(REST_NAME, IntervalType.REST, restBetweenSeconds))
    }
    return intervals + rest + intervals
}

/** Names the editor made up ("Work 3 of 8", "Rest"), as opposed to ones the user typed. */
fun isGeneratedName(name: String): Boolean =
    name.isBlank() || name == WORK_NAME || name == REST_NAME || numberedWorkName.matches(name)

/**
 * Renumbers the generated names to match the list as it is now, in the same
 * form buildSession uses. Names the user typed are left alone.
 */
fun withGeneratedNames(intervals: List<Interval>): List<Interval> {
    val total = workCount(intervals)
    var workNumber = 0
    return intervals.map { interval ->
        if (interval.type == IntervalType.WORK) workNumber++
        when {
            !isGeneratedName(interval.name) -> interval
            interval.type == IntervalType.WORK -> interval.copy(name = "Work $workNumber of $total")
            else -> interval.copy(name = REST_NAME)
        }
    }
}

/**
 * What an unnamed template is called: "8× 0:40/0:20", "5× 1:00" when there
 * are no rests, or "Workout" when the durations differ.
 */
fun suggestedTemplateName(intervals: List<Interval>): String {
    val work = commonDuration(intervals, IntervalType.WORK) ?: return "Workout"
    val rounds = workCount(intervals)
    if (intervals.none { it.type == IntervalType.REST }) return "$rounds× ${formatDuration(work)}"
    val rest = commonDuration(intervals, IntervalType.REST) ?: return "Workout"
    return "$rounds× ${formatDuration(work)}/${formatDuration(rest)}"
}

/** A ready-made workout, offered when starting a new template. */
data class StarterTemplate(
    val name: String,
    val rounds: Int,
    val workSeconds: Int,
    val restSeconds: Int,
) {
    /** "Tabata 8×20/10" -> "Tabata", for chips. */
    val shortName: String get() = name.substringBefore(' ')

    fun intervals(): List<Interval> =
        buildSession(QuickBuildConfig(rounds, workSeconds, restSeconds))
}

val starterTemplates = listOf(
    StarterTemplate("Tabata 8×20/10", rounds = 8, workSeconds = 20, restSeconds = 10),
    StarterTemplate("HIIT 8×40/20", rounds = 8, workSeconds = 40, restSeconds = 20),
    StarterTemplate("Strength 5×60/90", rounds = 5, workSeconds = 60, restSeconds = 90),
)
