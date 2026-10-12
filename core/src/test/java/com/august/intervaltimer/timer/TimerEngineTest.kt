package com.august.intervaltimer.timer

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
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

    private fun TestScope.newEngine() =
        TimerEngine(scope = backgroundScope, clock = { testScheduler.currentTime })

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
}
