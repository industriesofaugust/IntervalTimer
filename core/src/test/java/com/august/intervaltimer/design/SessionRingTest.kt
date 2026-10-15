package com.august.intervaltimer.design

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.QuickBuildConfig
import com.august.intervaltimer.model.buildSession
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRingTest {

    private val session = buildSession(QuickBuildConfig(rounds = 3, workSeconds = 40, restSeconds = 20))

    @Test
    fun `segments and gaps fill the whole dial`() {
        val segments = ringSegments(session)
        val gap = ringGapDegrees(session.size)

        assertEquals(session.size, segments.size)
        assertEquals(360f, segments.sumOf { it.sweep.toDouble() }.toFloat() + gap * segments.size, 0.01f)
        // Each segment starts where the previous one ended, plus one gap.
        segments.zipWithNext { a, b ->
            assertEquals(a.startAngle + a.sweep + gap, b.startAngle, 0.01f)
        }
    }

    @Test
    fun `longer intervals get longer arcs`() {
        val segments = ringSegments(session)

        // 40 s of work is drawn twice as long as 20 s of rest.
        assertEquals(2f, segments[0].sweep / segments[1].sweep, 0.01f)
        assertEquals(IntervalType.WORK, segments[0].type)
        assertEquals(IntervalType.REST, segments[1].type)
    }

    @Test
    fun `a very short interval is still visible`() {
        val lopsided = listOf(
            Interval("Long", IntervalType.WORK, 3000),
            Interval("Blink", IntervalType.REST, 1),
        )

        assertTrue(ringSegments(lopsided)[1].sweep >= 3f)
    }

    @Test
    fun `states follow the running timer`() {
        val running = TimerUiState(
            intervals = session,
            currentIndex = 2,
            remainingMillis = 10_000,
            phase = TimerPhase.RUNNING,
        )

        assertEquals(
            listOf(
                SegmentState.DONE, SegmentState.DONE, SegmentState.CURRENT,
                SegmentState.UPCOMING, SegmentState.UPCOMING,
            ),
            running.ringSegments().map { it.state },
        )
        assertEquals(0.25f, running.currentRemainingFraction, 0.001f)
    }

    @Test
    fun `the ring can be built from just the fields that shape it`() {
        val ended = TimerUiState(
            intervals = session,
            currentIndex = 1,
            phase = TimerPhase.FINISHED,
            endedEarly = true,
            completedIntervals = 2,
        )

        assertEquals(
            ended.ringSegments(),
            ringSegments(TimerPhase.FINISHED, session, currentIndex = 1, completedIntervals = 2),
        )
    }

    @Test
    fun `nothing is done during the lead in and everything after a full session`() {
        val leadIn = TimerUiState(intervals = session, phase = TimerPhase.LEAD_IN)
        val finished = TimerUiState(
            intervals = session,
            phase = TimerPhase.FINISHED,
            completedIntervals = session.size,
        )

        assertTrue(leadIn.ringSegments().all { it.state == SegmentState.UPCOMING })
        assertTrue(finished.ringSegments().all { it.state == SegmentState.DONE })
    }

    @Test
    fun `ending early lights only the completed intervals`() {
        val ended = TimerUiState(
            intervals = session,
            phase = TimerPhase.FINISHED,
            endedEarly = true,
            completedIntervals = 2,
        )

        assertEquals(2, ended.ringSegments().count { it.state == SegmentState.DONE })
    }

    @Test
    fun `long sessions drop the gaps`() {
        val long = buildSession(QuickBuildConfig(rounds = 30, workSeconds = 30, restSeconds = 15))

        assertEquals(0f, ringGapDegrees(long.size))
        assertEquals(360f, ringSegments(long).sumOf { it.sweep.toDouble() }.toFloat(), 0.05f)
    }

    @Test
    fun `strip fractions add up to one`() {
        val strip = stripSegments(session)

        assertEquals(1f, strip.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
        assertEquals(session.map { it.type }, strip.map { it.type })
    }

    @Test
    fun `an empty session draws nothing`() {
        assertTrue(ringSegments(emptyList()).isEmpty())
        assertTrue(stripSegments(emptyList()).isEmpty())
    }
}
