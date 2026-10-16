package com.august.intervaltimer.sync

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The live link messages must read back unchanged, and anything that is not
 * a valid message must come back as null rather than crash the receiver.
 */
class LiveLinkTest {

    @Test
    fun `a start message survives the trip`() {
        val start = LiveStart(
            linkId = "abc",
            sessionName = "Tabata",
            intervals = listOf(SyncedInterval("Burpees", "WORK", 20), SyncedInterval("Rest", "REST", 10)),
            startedAtEpochMillis = 1_760_000_000_000,
        )

        assertEquals(start, LiveLinkCodec.decodeStart(LiveLinkCodec.encode(start)))
    }

    @Test
    fun `an ack survives the trip`() {
        val ack = LiveAck("abc", accepted = false)

        assertEquals(ack, LiveLinkCodec.decodeAck(LiveLinkCodec.encode(ack)))
    }

    @Test
    fun `a control snapshot survives the trip`() {
        val control = LiveControl(
            linkId = "abc",
            index = 3,
            remainingMillis = 12_345,
            paused = true,
            ended = true,
            sentAtEpochMillis = 1_760_000_000_500,
        )

        assertEquals(control, LiveLinkCodec.decodeControl(LiveLinkCodec.encode(control)))
    }

    @Test
    fun `metrics survive the trip with and without a heart rate`() {
        val withHeartRate = LiveMetricsUpdate("abc", heartRate = 128.0, calories = 45.5)
        val withoutHeartRate = LiveMetricsUpdate("abc")

        assertEquals(withHeartRate, LiveLinkCodec.decodeMetrics(LiveLinkCodec.encode(withHeartRate)))
        assertEquals(withoutHeartRate, LiveLinkCodec.decodeMetrics(LiveLinkCodec.encode(withoutHeartRate)))
    }

    @Test
    fun `fields added by a newer app are ignored`() {
        val raw = """{"linkId":"abc","accepted":true,"somethingNew":1}"""

        assertEquals(LiveAck("abc", true), LiveLinkCodec.decodeAck(raw.encodeToByteArray()))
    }

    @Test
    fun `malformed messages decode to null`() {
        val garbage = "not json at all".encodeToByteArray()
        val empty = ByteArray(0)
        val wrongKind = LiveLinkCodec.encode(LiveAck("abc", true))

        assertNull(LiveLinkCodec.decodeStart(garbage))
        assertNull(LiveLinkCodec.decodeAck(empty))
        assertNull(LiveLinkCodec.decodeControl(garbage))
        assertNull(LiveLinkCodec.decodeMetrics(empty))
        assertNull(LiveLinkCodec.decodeControl(wrongKind))
    }

    private val intervals = listOf(
        Interval("Push-ups", IntervalType.WORK, 30),
        Interval("Rest", IntervalType.REST, 10),
    )

    @Test
    fun `a snapshot describes the lead in, a running interval and a finished session`() {
        val leadIn = TimerUiState(intervals = intervals, remainingMillis = 4_000)
        val running = TimerUiState(
            intervals = intervals,
            currentIndex = 1,
            remainingMillis = 7_000,
            phase = TimerPhase.RUNNING,
            isPaused = true,
        )
        val finished = TimerUiState(intervals = intervals, currentIndex = 1, phase = TimerPhase.FINISHED)

        assertEquals(-1, leadIn.toLiveControl("a", 100).index)
        val runningControl = running.toLiveControl("a", 100)
        assertEquals(1, runningControl.index)
        assertEquals(7_000, runningControl.remainingMillis)
        assertEquals(true, runningControl.paused)
        assertEquals(100, runningControl.sentAtEpochMillis)
        assertEquals(2, finished.toLiveControl("a", 100).index)
    }

    @Test
    fun `travel time is taken off a running timer but not a paused one`() {
        val running = LiveControl("a", 0, remainingMillis = 10_000, paused = false, sentAtEpochMillis = 1_000)
        val paused = running.copy(paused = true)

        assertEquals(9_700, running.remainingNow(nowEpochMillis = 1_300))
        assertEquals(10_000, paused.remainingNow(nowEpochMillis = 1_300))
    }

    @Test
    fun `a wrong clock cannot push the timer far`() {
        val control = LiveControl("a", 0, remainingMillis = 10_000, paused = false, sentAtEpochMillis = 1_000)

        // The sender's clock is ahead: no negative travel time.
        assertEquals(10_000, control.remainingNow(nowEpochMillis = 500))
        // The sender's clock is far behind: travel time is capped at five seconds.
        assertEquals(5_000, control.remainingNow(nowEpochMillis = 600_000))
        // It never goes below zero.
        assertEquals(0, control.copy(remainingMillis = 100).remainingNow(nowEpochMillis = 3_000))
    }

    @Test
    fun `live metrics are formatted for the timer screen`() {
        val metrics = LiveMetricsUpdate("a", heartRate = 127.6, calories = 45.2, steps = 1204)
        assertEquals("128", metrics.heartRateText())
        assertEquals("45", metrics.caloriesText())
        assertEquals("%,d STEPS".format(1204), metrics.stepsText())

        val empty = LiveMetricsUpdate("a")
        assertNull(empty.heartRateText())
        assertNull(empty.caloriesText())
        assertNull(empty.stepsText())
    }

    @Test
    fun `a sync request from the watch survives the trip`() {
        val request = LiveRequest(
            linkId = "w1",
            sessionName = "Leg day",
            intervals = listOf(SyncedInterval("Squats", "WORK", 40), SyncedInterval("Rest", "REST", 20)),
            position = LiveControl("w1", index = 1, remainingMillis = 12_000, paused = false, sentAtEpochMillis = 5_000),
        )
        assertEquals(request, LiveLinkCodec.decodeRequest(LiveLinkCodec.encode(request)))
        assertNull(LiveLinkCodec.decodeRequest("not json".encodeToByteArray()))
    }
}