package com.august.intervaltimer.sync

import com.august.intervaltimer.model.IntervalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone and watch talk in JSON. These tests check that what one side
 * writes the other reads back unchanged, and that a newer app adding a field
 * does not break an older one.
 */
class SyncCodecTest {

    @Test
    fun `templates survive the trip to the watch`() {
        val list = SyncedTemplateList(
            listOf(
                SyncedTemplate(
                    id = 7,
                    name = "Leg day",
                    intervals = listOf(
                        SyncedInterval("Squats", "WORK", 45),
                        SyncedInterval("Rest", "REST", 15),
                    ),
                )
            )
        )

        val decoded = TemplateSyncCodec.decode(TemplateSyncCodec.encode(list))

        assertEquals(list, decoded)
        assertEquals(60, decoded.templates.single().totalSeconds)
        val intervals = decoded.templates.single().toIntervals()
        assertEquals(listOf(IntervalType.WORK, IntervalType.REST), intervals.map { it.type })
    }

    @Test
    fun `an unknown interval type falls back to work`() {
        val template = SyncedTemplate(1, "Odd", listOf(SyncedInterval("Mystery", "STRETCH", 10)))

        assertEquals(IntervalType.WORK, template.toIntervals().single().type)
    }

    @Test
    fun `workout results survive the trip to the phone`() {
        val result = WorkoutResult(
            completedAt = 1_760_000_000_000,
            templateName = "Tabata",
            totalSeconds = 230,
            workSeconds = 160,
            workIntervals = 8,
            avgHeartRate = 141.5,
            maxHeartRate = 172.0,
            steps = 312,
            calories = 48.2,
            hrSeries = listOf(HrSample(0, 98.0), HrSample(30, 150.0)),
            linkId = "link-1",
        )

        assertEquals(result, ResultSyncCodec.decode(ResultSyncCodec.encode(result)))
    }

    @Test
    fun `a result from an older watch has no link id`() {
        val json = """{"completedAt":5,"totalSeconds":60,"workSeconds":40,"workIntervals":2}"""

        assertNull(ResultSyncCodec.decode(json).linkId)
    }

    @Test
    fun `a result without sensor data has empty health fields`() {
        val json = """{"completedAt":5,"totalSeconds":60,"workSeconds":40,"workIntervals":2}"""

        val result = ResultSyncCodec.decode(json)

        assertNull(result.avgHeartRate)
        assertNull(result.steps)
        assertTrue(result.hrSeries.isEmpty())
    }

    @Test
    fun `fields added by a newer app version are ignored`() {
        val json = """{"completedAt":5,"totalSeconds":60,"workSeconds":40,""" +
            """"workIntervals":2,"somethingNew":"hello"}"""

        assertEquals(60, ResultSyncCodec.decode(json).totalSeconds)
    }

    @Test
    fun `watch cue settings round trip and convert level to volume`() {
        val settings = WatchCueSettings(
            vibrationEnabled = false,
            vibrationLevel = 2,
            soundEnabled = true,
            soundLevel = 5,
        )

        val decoded = WatchCueSettingsCodec.decode(WatchCueSettingsCodec.encode(settings))

        assertEquals(settings, decoded)
        assertEquals(100, decoded.soundVolumePercent)
    }

    @Test
    fun `a damaged heart rate series reads as empty instead of crashing`() {
        assertTrue(HrSeriesCodec.decode("not json").isEmpty())
    }
}
