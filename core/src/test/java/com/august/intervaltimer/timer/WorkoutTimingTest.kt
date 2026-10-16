package com.august.intervaltimer.timer

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTimingTest {

    private val work = Interval("Work", IntervalType.WORK, 30)
    private val rest = Interval("Rest", IntervalType.REST, 10)
    private val intervals = listOf(work, rest, work)

    @Test
    fun `intervals without pauses become work and rest pieces`() {
        val spans = listOf(
            IntervalSpan(0, 1_000, 31_000),
            IntervalSpan(1, 31_000, 41_000),
        )

        assertEquals(
            listOf(
                SessionPiece(PieceKind.WORK, 1_000, 31_000),
                SessionPiece(PieceKind.REST, 31_000, 41_000),
            ),
            sessionPieces(spans, emptyList(), intervals),
        )
    }

    @Test
    fun `a pause cuts its interval in two`() {
        val spans = listOf(IntervalSpan(0, 0, 60_000))
        val pauses = listOf(PauseSpan(10_000, 25_000))

        assertEquals(
            listOf(
                SessionPiece(PieceKind.WORK, 0, 10_000),
                SessionPiece(PieceKind.PAUSE, 10_000, 25_000),
                SessionPiece(PieceKind.WORK, 25_000, 60_000),
            ),
            sessionPieces(spans, pauses, intervals),
        )
    }

    @Test
    fun `a pause that lasts through a skip is one pause piece`() {
        // Paused in the work, skipped to the rest while paused, resumed later.
        val spans = listOf(IntervalSpan(0, 0, 20_000), IntervalSpan(1, 20_000, 50_000))
        val pauses = listOf(PauseSpan(10_000, 30_000))

        assertEquals(
            listOf(
                SessionPiece(PieceKind.WORK, 0, 10_000),
                SessionPiece(PieceKind.PAUSE, 10_000, 30_000),
                SessionPiece(PieceKind.REST, 30_000, 50_000),
            ),
            sessionPieces(spans, pauses, intervals),
        )
    }

    @Test
    fun `pieces never overlap and stay inside the session`() {
        val spans = listOf(IntervalSpan(0, 5_000, 35_000), IntervalSpan(1, 35_000, 45_000))
        // The first pause began in the get-ready, the second runs past the end.
        val pauses = listOf(PauseSpan(1_000, 8_000), PauseSpan(40_000, 90_000))

        val pieces = sessionPieces(spans, pauses, intervals)

        assertEquals(5_000L, pieces.first().startMillis)
        assertEquals(45_000L, pieces.last().endMillis)
        pieces.zipWithNext().forEach { (a, b) -> assertTrue(a.endMillis <= b.startMillis) }
        pieces.forEach { assertTrue(it.startMillis < it.endMillis) }
    }

    @Test
    fun `spans of no length or of an unknown interval are left out`() {
        val spans = listOf(
            IntervalSpan(0, 1_000, 1_000),
            IntervalSpan(1, 1_000, 11_000),
            IntervalSpan(9, 11_000, 20_000),
        )

        assertEquals(
            listOf(SessionPiece(PieceKind.REST, 1_000, 11_000)),
            sessionPieces(spans, emptyList(), intervals),
        )
    }

    @Test
    fun `no spans give no pieces`() {
        assertTrue(sessionPieces(emptyList(), listOf(PauseSpan(0, 10)), intervals).isEmpty())
    }

    @Test
    fun `timing survives being stored as JSON`() {
        val timing = WorkoutTiming(
            spans = listOf(IntervalSpan(0, 1_000, 2_000)),
            pauses = listOf(PauseSpan(1_200, 1_500)),
        )

        assertEquals(timing, WorkoutTimingCodec.decode(WorkoutTimingCodec.encode(timing)))
    }

    @Test
    fun `unreadable timing decodes as empty`() {
        assertEquals(WorkoutTiming(), WorkoutTimingCodec.decode("not json"))
    }
}
