package com.august.intervaltimer.data

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.HrSample
import com.august.intervaltimer.sync.HrSeriesCodec
import com.august.intervaltimer.sync.IntervalListCodec
import com.august.intervaltimer.sync.SyncedInterval
import com.august.intervaltimer.timer.IntervalSpan
import com.august.intervaltimer.timer.PauseSpan
import com.august.intervaltimer.timer.WorkoutTiming
import com.august.intervaltimer.timer.WorkoutTimingCodec
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRecordsTest {

    private val start = 1_760_000_000_000L

    // 30 s work, 10 s rest, 30 s work.
    private val intervals = IntervalListCodec.encode(
        listOf(
            SyncedInterval("Work", IntervalType.WORK.name, 30),
            SyncedInterval("Rest", IntervalType.REST.name, 10),
            SyncedInterval("Work", IntervalType.WORK.name, 30),
        )
    )

    private fun entity(
        id: Long = 7,
        linkId: String? = null,
        timing: WorkoutTiming? = null,
        withSensors: Boolean = true,
    ) = WorkoutResultEntity(
        id = id,
        completedAt = start + 70_000,
        templateName = "Tabata",
        totalSeconds = 70,
        workSeconds = 60,
        workIntervals = 2,
        avgHeartRate = if (withSensors) 140.0 else null,
        maxHeartRate = if (withSensors) 170.0 else null,
        steps = if (withSensors) 120L else null,
        calories = if (withSensors) 12.5 else null,
        hrSeriesJson = if (withSensors) {
            // The watch counts from the get-ready, 5 s before the first interval.
            HrSeriesCodec.encode(listOf(HrSample(2, 90.0), HrSample(10, 120.0), HrSample(60, 150.0)))
        } else null,
        intervalsJson = intervals,
        completedIntervals = 3,
        linkId = linkId,
        timingJson = timing?.let { WorkoutTimingCodec.encode(it) },
    )

    private val timing = WorkoutTiming(
        spans = listOf(
            IntervalSpan(0, start, start + 30_000),
            IntervalSpan(1, start + 30_000, start + 40_000),
            IntervalSpan(2, start + 40_000, start + 70_000),
        ),
        pauses = listOf(PauseSpan(start + 10_000, start + 15_000)),
    )

    @Test
    fun `a linked workout is keyed by its link id and an unlinked one by its row`() {
        assertEquals("link-1", entity(linkId = "link-1").healthKey())
        assertEquals("local-7", entity(id = 7).healthKey())
    }

    @Test
    fun `every record has a stable id and the version of who saved it`() {
        val phone = buildHealthRecords(entity(linkId = "link-1"), fromWatch = false)
        val watch = buildHealthRecords(entity(linkId = "link-1"), fromWatch = true)

        assertEquals(
            listOf("link-1-session", "link-1-hr", "link-1-steps", "link-1-kcal"),
            watch.map { it.metadata.clientRecordId },
        )
        assertTrue(phone.all { it.metadata.clientRecordVersion == 1L })
        assertTrue(watch.all { it.metadata.clientRecordVersion == 2L })
        assertTrue(phone.all { it.metadata.device?.type == Device.TYPE_PHONE })
        assertTrue(watch.all { it.metadata.device?.type == Device.TYPE_WATCH })
    }

    @Test
    fun `the same workout builds the same ids every time`() {
        val first = buildHealthRecords(entity(), fromWatch = true).map { it.metadata.clientRecordId }
        val again = buildHealthRecords(entity(), fromWatch = true).map { it.metadata.clientRecordId }

        assertEquals(first, again)
    }

    @Test
    fun `a workout without sensors is only a session`() {
        val records = buildHealthRecords(entity(withSensors = false), fromWatch = false)

        assertEquals(1, records.size)
        assertTrue(records.single() is ExerciseSessionRecord)
    }

    @Test
    fun `the session carries the name and the work, rest and pause segments`() {
        val session = buildHealthRecords(entity(timing = timing), fromWatch = true)
            .filterIsInstance<ExerciseSessionRecord>().single()

        assertEquals("Tabata", session.title)
        assertEquals(Instant.ofEpochMilli(start), session.startTime)
        assertEquals(Instant.ofEpochMilli(start + 70_000), session.endTime)
        assertEquals(
            listOf(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            ),
            session.segments.map { it.segmentType },
        )
        session.segments.zipWithNext().forEach { (a, b) -> assertTrue(a.endTime <= b.startTime) }
        session.segments.forEach { assertTrue(it.startTime < it.endTime) }
    }

    @Test
    fun `an old workout without timing ends at the save and has no segments`() {
        val session = buildHealthRecords(entity(), fromWatch = true)
            .filterIsInstance<ExerciseSessionRecord>().single()

        assertEquals(Instant.ofEpochMilli(start), session.startTime)
        assertEquals(Instant.ofEpochMilli(start + 70_000), session.endTime)
        assertTrue(session.segments.isEmpty())
    }

    @Test
    fun `heart rate readings are placed by the real start of the workout`() {
        val heartRate = buildHealthRecords(entity(timing = timing), fromWatch = true)
            .filterIsInstance<HeartRateRecord>().single()

        // The reading at 2 s was in the get-ready, before the session began: left out.
        // 10 s after recording began is 5 s into the first interval.
        assertEquals(
            listOf(start + 5_000, start + 55_000),
            heartRate.samples.map { it.time.toEpochMilli() },
        )
    }

    @Test
    fun `steps and calories are saved as they were measured`() {
        val records = buildHealthRecords(entity(), fromWatch = true)

        assertEquals(120L, records.filterIsInstance<StepsRecord>().single().count)
        assertEquals(
            12.5,
            records.filterIsInstance<TotalCaloriesBurnedRecord>().single().energy.inKilocalories,
            0.001,
        )
    }

    @Test
    fun `a title is always set`() {
        val session = buildHealthRecords(
            entity(timing = timing).copy(templateName = null),
            fromWatch = false,
        ).filterIsInstance<ExerciseSessionRecord>().single()

        assertEquals("Interval workout", session.title)
    }

    @Test
    fun `a result looks like the watch's only when it has sensor data`() {
        assertTrue(entity().looksLikeWatchResult())
        assertEquals(false, entity(withSensors = false).looksLikeWatchResult())
    }
}
