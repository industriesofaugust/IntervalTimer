package com.august.intervaltimer.timer

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.LiveControl
import com.august.intervaltimer.sync.applyLiveControl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests run the real engine against a fake clock, so ten seconds of
 * workout takes no real time and every tick lands exactly where expected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private val work3 = Interval("Push-ups", IntervalType.WORK, 3)
    private val rest2 = Interval("Rest", IntervalType.REST, 2)
    private val work4 = Interval("Squats", IntervalType.WORK, 4)

    // The wall clock starts at a fixed moment and moves with the fake time.
    private val wallStart = 1_700_000_000_000L

    private fun TestScope.newEngine() = TimerEngine(
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
        wallClock = { wallStart + testScheduler.currentTime },
    )

    private fun TestScope.recordCues(engine: TimerEngine): List<TimerCue> {
        val cues = mutableListOf<TimerCue>()
        backgroundScope.launch { engine.cues.collect { cues.add(it) } }
        runCurrent()
        return cues
    }

    @Test
    fun `starts with a five second lead in`() = runTest {
        val engine = newEngine()

        engine.start(listOf(work3, rest2))

        val state = engine.uiState.value
        assertEquals(TimerPhase.LEAD_IN, state.phase)
        assertEquals(5, state.remainingSeconds)
        assertEquals(work3, state.nextInterval)
    }

    @Test
    fun `runs every interval in order then finishes`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))

        advanceTimeBy(5_050)
        assertEquals(TimerPhase.RUNNING, engine.uiState.value.phase)
        assertEquals(work3, engine.uiState.value.currentInterval)

        advanceTimeBy(3_000)
        assertEquals(rest2, engine.uiState.value.currentInterval)

        advanceTimeBy(2_000)
        val finished = engine.uiState.value
        assertEquals(TimerPhase.FINISHED, finished.phase)
        assertFalse(finished.endedEarly)
        assertEquals(2, finished.completedIntervals)
        assertEquals(5, finished.elapsedSecondsAtEnd)
    }

    @Test
    fun `announces countdown ticks and interval starts`() = runTest {
        val engine = newEngine()
        val cues = recordCues(engine)

        engine.start(listOf(work3, rest2))
        advanceTimeBy(10_100)

        assertEquals(
            listOf(
                // Lead-in: 3, 2, 1.
                TimerCue.COUNTDOWN_TICK, TimerCue.COUNTDOWN_TICK, TimerCue.COUNTDOWN_TICK,
                TimerCue.WORK_START,
                // Three seconds of work: ticks at 2 and 1.
                TimerCue.COUNTDOWN_TICK, TimerCue.COUNTDOWN_TICK,
                TimerCue.REST_START,
                // Two seconds of rest: tick at 1.
                TimerCue.COUNTDOWN_TICK,
                TimerCue.FINISH,
            ),
            cues,
        )
    }

    @Test
    fun `time does not pass while paused`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))
        advanceTimeBy(6_050)
        val before = engine.uiState.value.remainingMillis

        engine.togglePause()
        advanceTimeBy(30_000)

        assertTrue(engine.uiState.value.isPaused)
        assertEquals(before, engine.uiState.value.remainingMillis)
        assertEquals(work3, engine.uiState.value.currentInterval)

        engine.togglePause()
        advanceTimeBy(2_000)
        assertEquals(rest2, engine.uiState.value.currentInterval)
    }

    @Test
    fun `skip jumps to the next interval`() = runTest {
        val engine = newEngine()
        val cues = recordCues(engine)
        engine.start(listOf(work3, rest2))

        engine.skip()
        assertEquals(work3, engine.uiState.value.currentInterval)
        engine.skip()
        assertEquals(rest2, engine.uiState.value.currentInterval)
        assertEquals(2, engine.uiState.value.remainingSeconds)
        runCurrent()

        assertEquals(listOf(TimerCue.WORK_START, TimerCue.REST_START), cues)
    }

    @Test
    fun `ending early credits only completed intervals`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2, work4))
        // Lead-in, all of work (3 s), all of rest (2 s), one second into squats.
        advanceTimeBy(5_000 + 3_000 + 2_000 + 1_050)

        engine.endEarly()

        val ended = engine.uiState.value
        assertEquals(TimerPhase.FINISHED, ended.phase)
        assertTrue(ended.endedEarly)
        assertEquals(2, ended.completedIntervals)
        assertEquals(6, ended.elapsedSecondsAtEnd)
    }

    @Test
    fun `ending during the lead in credits nothing`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3))
        advanceTimeBy(2_000)

        engine.endEarly()

        assertEquals(0, engine.uiState.value.completedIntervals)
        assertEquals(0, engine.uiState.value.elapsedSecondsAtEnd)
    }

    @Test
    fun `session progress runs from zero to one`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))
        assertEquals(0f, engine.uiState.value.sessionProgress)

        advanceTimeBy(5_000 + 2_500)
        assertEquals(0.5f, engine.uiState.value.sessionProgress, 0.02f)

        advanceTimeBy(10_000)
        assertEquals(1f, engine.uiState.value.sessionProgress)
    }

    @Test
    fun `stop clears the session`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3))
        advanceTimeBy(6_000)

        engine.stop()
        advanceTimeBy(10_000)

        assertTrue(engine.uiState.value.intervals.isEmpty())
    }

    @Test
    fun `an empty session does not start`() = runTest {
        val engine = newEngine()

        engine.start(emptyList())

        assertTrue(engine.uiState.value.intervals.isEmpty())
    }

    @Test
    fun `starting part way into the lead in shortens it`() = runTest {
        val engine = newEngine()

        engine.start(listOf(work3, rest2), elapsedMillis = 2_000)

        val state = engine.uiState.value
        assertEquals(TimerPhase.LEAD_IN, state.phase)
        assertEquals(3_000, state.remainingMillis)
    }

    @Test
    fun `starting past the lead in begins in the right interval`() = runTest {
        val engine = newEngine()

        // 5 s lead-in, all 3 s of work, then half a second of rest.
        engine.start(listOf(work3, rest2), elapsedMillis = 5_000 + 3_000 + 500)

        val state = engine.uiState.value
        assertEquals(TimerPhase.RUNNING, state.phase)
        assertEquals(rest2, state.currentInterval)
        assertEquals(1_500, state.remainingMillis)
    }

    @Test
    fun `starting exactly on an interval boundary begins the next one`() = runTest {
        val engine = newEngine()

        engine.start(listOf(work3, rest2), elapsedMillis = 5_000 + 3_000)

        assertEquals(rest2, engine.uiState.value.currentInterval)
        assertEquals(2_000, engine.uiState.value.remainingMillis)
    }

    @Test
    fun `a session started late carries on and plays no cue for where it landed`() = runTest {
        val engine = newEngine()
        val cues = recordCues(engine)

        engine.start(listOf(work3, rest2), elapsedMillis = 5_000 + 1_000)
        runCurrent()
        assertTrue(cues.isEmpty())

        advanceTimeBy(2_050)
        assertEquals(rest2, engine.uiState.value.currentInterval)
    }

    @Test
    fun `starting after the session would have ended finishes it`() = runTest {
        val engine = newEngine()

        engine.start(listOf(work3, rest2), elapsedMillis = 60_000)
        advanceTimeBy(100)

        assertEquals(TimerPhase.FINISHED, engine.uiState.value.phase)
    }

    @Test
    fun `syncTo moves to the given interval and position`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2, work4))

        engine.syncTo(index = 1, remainingMillis = 1_200, paused = false)

        val state = engine.uiState.value
        assertEquals(TimerPhase.RUNNING, state.phase)
        assertEquals(rest2, state.currentInterval)
        assertEquals(1_200, state.remainingMillis)
        assertFalse(state.isPaused)
    }

    @Test
    fun `syncTo can pause and hold the time`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))

        engine.syncTo(index = 0, remainingMillis = 2_000, paused = true)
        advanceTimeBy(10_000)

        assertTrue(engine.uiState.value.isPaused)
        assertEquals(2_000, engine.uiState.value.remainingMillis)
        assertEquals(TimerPhase.RUNNING, engine.uiState.value.phase)
    }

    @Test
    fun `syncTo minus one goes back to the lead in`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))

        engine.syncTo(index = -1, remainingMillis = 4_000, paused = false)

        assertEquals(TimerPhase.LEAD_IN, engine.uiState.value.phase)
        assertEquals(4_000, engine.uiState.value.remainingMillis)
    }

    @Test
    fun `syncTo plays a cue only when the interval changes`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2, work4))
        advanceTimeBy(5_050)
        val cues = recordCues(engine)

        // Already in the first interval: nothing to announce.
        engine.syncTo(index = 0, remainingMillis = 1_000, paused = false)
        // A new interval is announced once, however often it is repeated.
        engine.syncTo(index = 1, remainingMillis = 2_000, paused = false)
        engine.syncTo(index = 1, remainingMillis = 1_900, paused = true)
        runCurrent()

        assertEquals(listOf(TimerCue.REST_START), cues)
    }

    @Test
    fun `syncTo past the last interval finishes the session`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))
        advanceTimeBy(5_050)
        val cues = recordCues(engine)

        engine.syncTo(index = 2, remainingMillis = 0, paused = false)
        runCurrent()

        val state = engine.uiState.value
        assertEquals(TimerPhase.FINISHED, state.phase)
        assertFalse(state.endedEarly)
        assertEquals(2, state.completedIntervals)
        assertEquals(5, state.elapsedSecondsAtEnd)
        assertEquals(listOf(TimerCue.FINISH), cues)
    }

    @Test
    fun `syncTo is ignored once the session is over`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3))
        advanceTimeBy(9_000)
        assertEquals(TimerPhase.FINISHED, engine.uiState.value.phase)

        engine.syncTo(index = 0, remainingMillis = 1_000, paused = false)

        assertEquals(TimerPhase.FINISHED, engine.uiState.value.phase)
    }

    @Test
    fun `a snapshot from the other device is applied and an early end ends the session`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2, work4))
        val now = 1_000_000L

        engine.applyLiveControl(
            LiveControl("a", index = 1, remainingMillis = 1_500, paused = false, sentAtEpochMillis = now - 100),
            nowEpochMillis = now,
        )
        assertEquals(rest2, engine.uiState.value.currentInterval)
        assertEquals(1_400, engine.uiState.value.remainingMillis)

        engine.applyLiveControl(
            LiveControl("a", index = 1, remainingMillis = 1_400, paused = false, ended = true, sentAtEpochMillis = now),
            nowEpochMillis = now,
        )
        assertTrue(engine.uiState.value.endedEarly)
        assertEquals(1, engine.uiState.value.completedIntervals)
    }

    @Test
    fun `an uninterrupted workout records each interval back to back`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))

        advanceTimeBy(10_100)

        val state = engine.uiState.value
        assertEquals(
            listOf(
                IntervalSpan(0, wallStart + 5_000, wallStart + 8_000),
                IntervalSpan(1, wallStart + 8_000, wallStart + 10_000),
            ),
            state.spans,
        )
        assertTrue(state.pauses.isEmpty())
    }

    @Test
    fun `a pause is recorded and the interval carries on after it`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))
        advanceTimeBy(6_000)

        engine.togglePause()
        advanceTimeBy(30_000)
        engine.togglePause()
        advanceTimeBy(2_100)

        val state = engine.uiState.value
        assertEquals(listOf(PauseSpan(wallStart + 6_000, wallStart + 36_000)), state.pauses)
        // The work interval is one span that lasted through the pause.
        assertEquals(IntervalSpan(0, wallStart + 5_000, wallStart + 38_000), state.spans.single())
        assertEquals(rest2, state.currentInterval)
    }

    @Test
    fun `a skipped interval ends at the moment it was skipped`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))
        engine.skip()
        advanceTimeBy(1_000)

        engine.skip()

        assertEquals(listOf(IntervalSpan(0, wallStart, wallStart + 1_000)), engine.uiState.value.spans)
        assertEquals(rest2, engine.uiState.value.currentInterval)

        advanceTimeBy(500)
        engine.skip()

        // Skipping the last interval finishes the workout, and it ends there too.
        val state = engine.uiState.value
        assertEquals(TimerPhase.FINISHED, state.phase)
        assertEquals(IntervalSpan(1, wallStart + 1_000, wallStart + 1_500), state.spans.last())
    }

    @Test
    fun `ending early stops the interval that was running`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2, work4))
        advanceTimeBy(5_000 + 3_000 + 2_000 + 1_050)

        engine.endEarly()

        assertEquals(
            listOf(
                IntervalSpan(0, wallStart + 5_000, wallStart + 8_000),
                IntervalSpan(1, wallStart + 8_000, wallStart + 10_000),
                IntervalSpan(2, wallStart + 10_000, wallStart + 11_050),
            ),
            engine.uiState.value.spans,
        )
    }

    @Test
    fun `ending during the lead in records no interval`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3))
        advanceTimeBy(2_000)

        engine.endEarly()

        assertTrue(engine.uiState.value.spans.isEmpty())
    }

    @Test
    fun `ending while paused closes the pause too`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2))
        advanceTimeBy(6_000)
        engine.togglePause()
        advanceTimeBy(2_000)

        engine.endEarly()

        val state = engine.uiState.value
        assertEquals(listOf(PauseSpan(wallStart + 6_000, wallStart + 8_000)), state.pauses)
        assertEquals(listOf(IntervalSpan(0, wallStart + 5_000, wallStart + 8_000)), state.spans)
    }

    @Test
    fun `joining part way in counts the interval from when it really began`() = runTest {
        val engine = newEngine()
        // One second into the first interval.
        engine.start(listOf(work3, rest2), elapsedMillis = 5_000 + 1_000)

        advanceTimeBy(2_100)

        assertEquals(
            IntervalSpan(0, wallStart - 1_000, wallStart + 2_000),
            engine.uiState.value.spans.first(),
        )
    }

    @Test
    fun `following the other device closes the interval it moves on from`() = runTest {
        val engine = newEngine()
        engine.start(listOf(work3, rest2, work4))
        advanceTimeBy(6_050)

        // The other device is already half a second into the rest.
        engine.syncTo(index = 1, remainingMillis = 1_500, paused = false)
        advanceTimeBy(1_000)
        engine.syncTo(index = 1, remainingMillis = 500, paused = true)
        advanceTimeBy(1_000)
        engine.syncTo(index = 1, remainingMillis = 500, paused = false)

        val state = engine.uiState.value
        assertEquals(IntervalSpan(0, wallStart + 5_000, wallStart + 5_550), state.spans.single())
        assertEquals(listOf(PauseSpan(wallStart + 7_050, wallStart + 8_050)), state.pauses)
    }
}
