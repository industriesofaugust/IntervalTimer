package com.august.intervaltimer.health

import com.august.intervaltimer.sync.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateZonesTest {

    @Test
    fun `max heart rate is 220 minus age`() {
        assertEquals(190, maxHeartRateFor(age = 30, highestSeen = 201.0))
    }

    @Test
    fun `without an age the highest reading ever seen is used`() {
        assertEquals(188, maxHeartRateFor(age = null, highestSeen = 187.6))
    }

    @Test
    fun `an age that cannot be right is ignored`() {
        assertEquals(180, maxHeartRateFor(age = 0, highestSeen = 180.0))
        assertEquals(180, maxHeartRateFor(age = 150, highestSeen = 180.0))
    }

    @Test
    fun `no age and no history gives no max`() {
        assertNull(maxHeartRateFor(age = null, highestSeen = null))
        assertNull(maxHeartRateFor(age = null, highestSeen = 0.0))
    }

    @Test
    fun `zones without a resting rate are shares of the max`() {
        val zones = HeartRateZones.create(maxHeartRate = 200, restingHeartRate = null)!!

        assertEquals(listOf(100, 120, 140, 160, 180), zones.lowerBounds)
        assertNull(zones.restingHeartRate)
    }

    @Test
    fun `zones with a resting rate are shares of the heart rate reserve`() {
        val zones = HeartRateZones.create(maxHeartRate = 190, restingHeartRate = 60)!!

        // 60 + 130 x 0.5 = 125, then 138, 151, 164, 177.
        assertEquals(listOf(125, 138, 151, 164, 177), zones.lowerBounds)
    }

    @Test
    fun `a resting rate too close to the max is not used`() {
        val zones = HeartRateZones.create(maxHeartRate = 100, restingHeartRate = 90)!!

        assertNull(zones.restingHeartRate)
        assertEquals(50, zones.lowerBounds.first())
    }

    @Test
    fun `no max means no zones`() {
        assertNull(HeartRateZones.create(maxHeartRate = null, restingHeartRate = 60))
        assertNull(HeartRateZones.create(maxHeartRate = 0, restingHeartRate = 60))
    }

    @Test
    fun `a heart rate is placed in its zone`() {
        val zones = HeartRateZones.create(maxHeartRate = 200, restingHeartRate = null)!!

        assertNull(zones.zoneOf(99.0))
        assertEquals(1, zones.zoneOf(100.0))
        assertEquals(2, zones.zoneOf(139.0))
        assertEquals(3, zones.zoneOf(140.0))
        assertEquals(5, zones.zoneOf(180.0))
        assertEquals(5, zones.zoneOf(215.0))
    }

    @Test
    fun `time is counted in the zone each reading started`() {
        val zones = HeartRateZones.create(maxHeartRate = 200, restingHeartRate = null)!!
        val series = listOf(
            HrSample(0, 90.0), // below zone 1: not counted
            HrSample(10, 110.0), // zone 1 for 10 s
            HrSample(20, 150.0), // zone 3 for 10 s
            HrSample(30, 185.0), // zone 5 for 5 s
            HrSample(35, 185.0),
        )

        assertEquals(listOf(10, 0, 10, 0, 5), timeInZones(series, zones))
    }

    @Test
    fun `a long gap between readings is not counted in full`() {
        val zones = HeartRateZones.create(maxHeartRate = 200, restingHeartRate = null)!!
        val series = listOf(HrSample(0, 110.0), HrSample(300, 110.0))

        assertEquals(listOf(15, 0, 0, 0, 0), timeInZones(series, zones))
    }

    @Test
    fun `fewer than two readings count no time`() {
        val zones = HeartRateZones.create(maxHeartRate = 200, restingHeartRate = null)!!

        assertEquals(List(5) { 0 }, timeInZones(listOf(HrSample(0, 150.0)), zones))
        assertEquals(List(5) { 0 }, timeInZones(emptyList(), zones))
    }
}
