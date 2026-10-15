package com.august.intervaltimer.sync

import com.august.intervaltimer.timer.TimerEngine
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * The "live link": while the phone runs a workout, the watch runs it too.
 * These are the short messages the two devices send each other. They go as
 * Wearable messages, which are quick but not stored: a device that is out of
 * reach simply misses them and carries on by itself.
 */
object LivePaths {
    /** The watch listens for everything under this prefix. */
    const val PREFIX = "/live"
    const val START = "/live/start"
    const val ACK = "/live/ack"
    const val CONTROL = "/live/control"
    const val METRICS = "/live/metrics"
}

/** Phone to watch: "I just started this workout; join it." */
@Serializable
data class LiveStart(
    val linkId: String,
    val sessionName: String,
    val intervals: List<SyncedInterval>,
    val startedAtEpochMillis: Long,
)

/** Watch to phone: whether the watch joined the workout. */
@Serializable
data class LiveAck(
    val linkId: String,
    val accepted: Boolean,
)

/**
 * Either device to the other, after a pause, resume, skip or end. It is a
 * snapshot of where the timer is, not a command, so receiving it twice or
 * crossing paths with the other device's snapshot does no harm.
 * [index] is the current interval, -1 during get-ready, or the number of
 * intervals once the workout has finished. [ended] means the user ended it
 * early.
 */
@Serializable
data class LiveControl(
    val linkId: String,
    val index: Int,
    val remainingMillis: Long,
    val paused: Boolean,
    val ended: Boolean = false,
    val sentAtEpochMillis: Long,
)

/** Watch to phone, every few seconds: what the sensors are reading. */
@Serializable
data class LiveMetricsUpdate(
    val linkId: String,
    val heartRate: Double? = null,
    val calories: Double = 0.0,
    val steps: Long = 0,
)

// Each returns null while there is nothing worth showing yet.

/** "128" */
fun LiveMetricsUpdate.heartRateText(): String? = heartRate?.roundToInt()?.toString()

/** "45" */
fun LiveMetricsUpdate.caloriesText(): String? =
    calories.takeIf { it >= 0.5 }?.roundToInt()?.toString()

/** "1,204 STEPS" */
fun LiveMetricsUpdate.stepsText(): String? =
    steps.takeIf { it > 0 }?.let { "%,d STEPS".format(it) }

/** A message that is cut off or not ours decodes to null instead of throwing. */
object LiveLinkCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(message: LiveStart): ByteArray = encode(LiveStart.serializer(), message)
    fun encode(message: LiveAck): ByteArray = encode(LiveAck.serializer(), message)
    fun encode(message: LiveControl): ByteArray = encode(LiveControl.serializer(), message)
    fun encode(message: LiveMetricsUpdate): ByteArray =
        encode(LiveMetricsUpdate.serializer(), message)

    fun decodeStart(bytes: ByteArray): LiveStart? = decode(LiveStart.serializer(), bytes)
    fun decodeAck(bytes: ByteArray): LiveAck? = decode(LiveAck.serializer(), bytes)
    fun decodeControl(bytes: ByteArray): LiveControl? = decode(LiveControl.serializer(), bytes)
    fun decodeMetrics(bytes: ByteArray): LiveMetricsUpdate? =
        decode(LiveMetricsUpdate.serializer(), bytes)

    private fun <T> encode(serializer: KSerializer<T>, message: T): ByteArray =
        json.encodeToString(serializer, message).encodeToByteArray()

    private fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray): T? =
        runCatching { json.decodeFromString(serializer, bytes.decodeToString()) }.getOrNull()
}

/**
 * Describes the timer for the other device. To describe an early end, pass
 * the state from just before ending, so the position is kept.
 */
fun TimerUiState.toLiveControl(
    linkId: String,
    nowEpochMillis: Long,
    ended: Boolean = false,
): LiveControl = LiveControl(
    linkId = linkId,
    index = when (phase) {
        TimerPhase.LEAD_IN -> -1
        TimerPhase.RUNNING -> currentIndex
        TimerPhase.FINISHED -> if (endedEarly) currentIndex else intervals.size
    },
    remainingMillis = remainingMillis,
    paused = isPaused,
    ended = ended,
    sentAtEpochMillis = nowEpochMillis,
)

/** Messages rarely take long; a wrong clock must not shove the timer far. */
private const val MAX_TRAVEL_MILLIS = 5_000L

/** How much time is left now, allowing for the time the message spent travelling. */
fun LiveControl.remainingNow(nowEpochMillis: Long): Long {
    if (paused || ended) return remainingMillis
    val travel = (nowEpochMillis - sentAtEpochMillis).coerceIn(0L, MAX_TRAVEL_MILLIS)
    return (remainingMillis - travel).coerceAtLeast(0L)
}

/** Moves this timer to where the other device's snapshot says it is. */
fun TimerEngine.applyLiveControl(control: LiveControl, nowEpochMillis: Long) {
    if (control.ended) {
        endEarly()
    } else {
        syncTo(control.index, control.remainingNow(nowEpochMillis), control.paused)
    }
}
