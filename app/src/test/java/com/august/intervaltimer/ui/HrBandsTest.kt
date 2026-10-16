package com.august.intervaltimer.ui

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.timer.IntervalSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HrBandsTest {

    private val work = Interval("Work", IntervalType.WORK, 30)
    private val rest = Interval("Rest", IntervalType.REST, 10)

    @Test
    fun `planned bands follow the get-ready and then each other`() {
        assertEquals(
            listOf(
                HrBand(IntervalType.WORK, 5f, 35f),
                HrBand(IntervalType.REST, 35f, 45f),
            ),
            plannedBands(listOf(work, rest)),
        )
    }

    @Test
    fun `timed bands follow when the intervals really ran`() {
        // The work ran 40 s instead of 30 (it was paused), then the rest was skipped after 4 s.
        val spans = listOf(
            IntervalSpan(0, 100_000, 140_000),
            IntervalSpan(1, 140_000, 144_000),
        )

        assertEquals(
            listOf(
                HrBand(IntervalType.WORK, 5f, 45f),
                HrBand(IntervalType.REST, 45f, 49f),
            ),
            timedBands(spans, listOf(work, rest)),
        )
    }

    @Test
    fun `timed bands skip spans of unknown intervals and need at least one span`() {
        assertEquals(
            listOf(HrBand(IntervalType.WORK, 5f, 35f)),
            timedBands(
                listOf(IntervalSpan(0, 0, 30_000), IntervalSpan(5, 30_000, 40_000)),
                listOf(work, rest),
            ),
        )
        assertTrue(timedBands(emptyList(), listOf(work, rest)).isEmpty())
    }
}
