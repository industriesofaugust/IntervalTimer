package com.august.intervaltimer.health

import com.august.intervaltimer.sync.HrSample
import kotlin.math.roundToInt

const val ZONE_COUNT = 5

// Zone 1 starts at 50 % of the range, zone 5 at 90 %.
private val ZONE_STARTS = listOf(0.5, 0.6, 0.7, 0.8, 0.9)

// Readings further apart than this (a pause, a dropout) are not counted as time in a zone.
private const val LONGEST_GAP_SECONDS = 15

private const val YOUNGEST_AGE = 10
private const val OLDEST_AGE = 100

/**
 * The best guess at someone's top heart rate: 220 minus their age, or, with
 * no usable age, the highest reading they have ever recorded.
 */
fun maxHeartRateFor(age: Int?, highestSeen: Double?): Int? {
    if (age != null && age in YOUNGEST_AGE..OLDEST_AGE) return 220 - age
    return highestSeen?.roundToInt()?.takeIf { it > 0 }
}

/**
 * Five heart-rate zones. With a resting heart rate they are bands of the
 * heart-rate reserve (Karvonen: resting + share × (max − resting)); without
 * one they are plain shares of the maximum.
 */
class HeartRateZones private constructor(
    val maxHeartRate: Int,
    /** Null when the zones are shares of the maximum. */
    val restingHeartRate: Int?,
) {
    /** The lowest heart rate of each zone, zone 1 first. */
    val lowerBounds: List<Int> = ZONE_STARTS.map { share ->
        if (restingHeartRate == null) (share * maxHeartRate).roundToInt()
        else (restingHeartRate + share * (maxHeartRate - restingHeartRate)).roundToInt()
    }

    /** 1 to 5, or null below zone 1. */
    fun zoneOf(bpm: Double): Int? {
        val index = lowerBounds.indexOfLast { bpm >= it }
        return if (index < 0) null else index + 1
    }

    companion object {
        // A reserve this small would make every zone a sliver; use the maximum instead.
        private const val SMALLEST_RESERVE = 20

        fun create(maxHeartRate: Int?, restingHeartRate: Int?): HeartRateZones? {
            if (maxHeartRate == null || maxHeartRate <= 0) return null
            val usableResting = restingHeartRate
                ?.takeIf { it > 0 && maxHeartRate - it >= SMALLEST_RESERVE }
            return HeartRateZones(maxHeartRate, usableResting)
        }
    }
}

/** Seconds spent in each zone, zone 1 first. Time below zone 1 is not counted. */
fun timeInZones(series: List<HrSample>, zones: HeartRateZones): List<Int> {
    val seconds = IntArray(ZONE_COUNT)
    series.sortedBy { it.elapsedSeconds }.zipWithNext().forEach { (current, next) ->
        val gap = next.elapsedSeconds - current.elapsedSeconds
        if (gap <= 0) return@forEach
        val zone = zones.zoneOf(current.bpm) ?: return@forEach
        seconds[zone - 1] += gap.coerceAtMost(LONGEST_GAP_SECONDS)
    }
    return seconds.toList()
}
