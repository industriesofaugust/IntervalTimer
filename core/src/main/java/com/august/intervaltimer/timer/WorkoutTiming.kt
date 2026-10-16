package com.august.intervaltimer.timer

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** When one interval really ran, in epoch milliseconds. [index] is its place in the workout. */
@Serializable
data class IntervalSpan(
    val index: Int,
    val startMillis: Long,
    val endMillis: Long,
)

/** A stretch of the workout the user had paused, in epoch milliseconds. */
@Serializable
data class PauseSpan(
    val startMillis: Long,
    val endMillis: Long,
)

/** Everything about when a workout really ran. Stored with the result as JSON. */
@Serializable
data class WorkoutTiming(
    val spans: List<IntervalSpan> = emptyList(),
    val pauses: List<PauseSpan> = emptyList(),
)

object WorkoutTimingCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(timing: WorkoutTiming): String =
        json.encodeToString(WorkoutTiming.serializer(), timing)

    fun decode(raw: String): WorkoutTiming =
        runCatching { json.decodeFromString(WorkoutTiming.serializer(), raw) }
            .getOrDefault(WorkoutTiming())
}

enum class PieceKind { WORK, REST, PAUSE }

/** One stretch of the workout with a single meaning: working, resting or paused. */
data class SessionPiece(
    val kind: PieceKind,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * Lays the real timing out as pieces that follow each other without overlap.
 * A pause inside an interval cuts that interval in two. Anything that would
 * not fit (an unknown interval, a piece of no length) is left out.
 */
fun sessionPieces(
    spans: List<IntervalSpan>,
    pauses: List<PauseSpan>,
    intervals: List<Interval>,
): List<SessionPiece> {
    val ordered = spans.filter { it.endMillis > it.startMillis }.sortedBy { it.startMillis }
    if (ordered.isEmpty()) return emptyList()
    val sessionStart = ordered.first().startMillis
    val sessionEnd = ordered.maxOf { it.endMillis }

    // Only the part of a pause inside the session counts, and touching pauses join up.
    val pauseRuns = mutableListOf<PauseSpan>()
    pauses
        .map { PauseSpan(maxOf(it.startMillis, sessionStart), minOf(it.endMillis, sessionEnd)) }
        .filter { it.endMillis > it.startMillis }
        .sortedBy { it.startMillis }
        .forEach { pause ->
            val last = pauseRuns.lastOrNull()
            if (last != null && pause.startMillis <= last.endMillis) {
                pauseRuns[pauseRuns.lastIndex] = last.copy(endMillis = maxOf(last.endMillis, pause.endMillis))
            } else {
                pauseRuns.add(pause)
            }
        }

    val pieces = mutableListOf<SessionPiece>()
    pauseRuns.forEach { pieces.add(SessionPiece(PieceKind.PAUSE, it.startMillis, it.endMillis)) }
    ordered.forEach { span ->
        val kind = when (intervals.getOrNull(span.index)?.type) {
            IntervalType.WORK -> PieceKind.WORK
            IntervalType.REST -> PieceKind.REST
            null -> return@forEach
        }
        var from = span.startMillis
        pauseRuns.filter { it.endMillis > span.startMillis && it.startMillis < span.endMillis }
            .forEach { pause ->
                if (pause.startMillis > from) pieces.add(SessionPiece(kind, from, pause.startMillis))
                from = maxOf(from, pause.endMillis)
            }
        if (from < span.endMillis) pieces.add(SessionPiece(kind, from, span.endMillis))
    }

    // Safety net: if two spans overlapped, keep the earlier piece.
    val result = mutableListOf<SessionPiece>()
    pieces.sortedBy { it.startMillis }.forEach { piece ->
        if (result.isEmpty() || piece.startMillis >= result.last().endMillis) result.add(piece)
    }
    return result
}
