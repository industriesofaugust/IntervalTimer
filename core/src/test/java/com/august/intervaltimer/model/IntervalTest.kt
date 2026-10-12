package com.august.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalTest {

    @Test
    fun `quick build alternates work and rest with no trailing rest`() {
        val session = buildSession(QuickBuildConfig(rounds = 3, workSeconds = 40, restSeconds = 20))

        assertEquals(
            listOf(
                IntervalType.WORK, IntervalType.REST,
                IntervalType.WORK, IntervalType.REST,
                IntervalType.WORK,
            ),
            session.map { it.type },
        )
        assertEquals(listOf(40, 20, 40, 20, 40), session.map { it.durationSeconds })
        assertEquals("Work 2 of 3", session[2].name)
    }

    @Test
    fun `a single round is one work interval`() {
        val session = buildSession(QuickBuildConfig(rounds = 1, workSeconds = 30, restSeconds = 10))

        assertEquals(1, session.size)
        assertEquals(IntervalType.WORK, session.single().type)
    }

    @Test
    fun `plus button adds five seconds up to the maximum`() {
        assertEquals(45, stepDuration(40, 5))
        assertEquals(12, stepDuration(7, 5))
        assertEquals(605, stepDuration(600, 5))
        assertEquals(MAX_INTERVAL_SECONDS, stepDuration(MAX_INTERVAL_SECONDS - 2, 5))
    }

    @Test
    fun `minus button stops at five seconds`() {
        assertEquals(35, stepDuration(40, -5))
        assertEquals(5, stepDuration(7, -5))
        assertEquals(5, stepDuration(5, -5))
    }

    @Test
    fun `minus button leaves a precise short value alone`() {
        assertEquals(3, stepDuration(3, -5))
        assertEquals(1, stepDuration(1, -5))
    }

    @Test
    fun `durations format as minutes and two digit seconds`() {
        assertEquals("0:07", formatDuration(7))
        assertEquals("1:00", formatDuration(60))
        assertEquals("59:59", formatDuration(3599))
    }
}
