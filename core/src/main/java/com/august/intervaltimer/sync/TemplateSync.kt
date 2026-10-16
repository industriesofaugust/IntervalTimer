package com.august.intervaltimer.sync

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire format for phone→watch template sync. These classes are turned
 * into JSON, put on the Wearable Data Layer by the phone, and read back on
 * the watch. Both sides depend on this file, so they can never disagree
 * about the format.
 */
object SyncPaths {
    const val TEMPLATES_PATH = "/templates"
    const val TEMPLATES_KEY = "templates_json"
    const val RESULTS_PATH_PREFIX = "/results"
    const val RESULT_KEY = "result_json"
    const val CREATE_TEMPLATE_PATH_PREFIX = "/template-create"
    const val CREATE_TEMPLATE_KEY = "create_json"
    const val WATCH_SETTINGS_PATH = "/watch-settings"
    const val WATCH_SETTINGS_KEY = "settings_json"
    const val WATCH_TEST_PATH_PREFIX = "/watch-test"
    const val WATCH_TEST_KEY = "test"
}

/**
 * The watch's cue settings, shared over the Data Layer so the phone can
 * show and change them too. Levels run 1 (subtle) to 5 (maximum).
 */
@Serializable
data class WatchCueSettings(
    val vibrationEnabled: Boolean = true,
    val vibrationLevel: Int = 4,
    val soundEnabled: Boolean = false,
    val soundLevel: Int = 3,
)

/** Maps a 1–5 sound level to the 0–100 volume ToneGenerator expects. */
val WatchCueSettings.soundVolumePercent: Int
    get() = soundLevel.coerceIn(1, 5) * 20

object WatchCueSettingsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: WatchCueSettings): String =
        json.encodeToString(WatchCueSettings.serializer(), settings)

    fun decode(raw: String): WatchCueSettings =
        json.decodeFromString(WatchCueSettings.serializer(), raw)
}

/** A workout the user recently ran on the watch, kept for one-tap access. */
@Serializable
data class RecentTemplate(
    val name: String,
    val intervals: List<SyncedInterval>,
)

object RecentTemplatesCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer =
        kotlinx.serialization.builtins.ListSerializer(RecentTemplate.serializer())

    fun encode(recents: List<RecentTemplate>): String =
        json.encodeToString(serializer, recents)

    fun decode(raw: String): List<RecentTemplate> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}

fun RecentTemplate.toIntervals(): List<Interval> = intervals.map {
    Interval(
        name = it.name,
        type = runCatching { IntervalType.valueOf(it.type) }.getOrDefault(IntervalType.WORK),
        durationSeconds = it.durationSeconds,
    )
}

fun List<Interval>.toSyncedIntervals(): List<SyncedInterval> =
    map { SyncedInterval(name = it.name, type = it.type.name, durationSeconds = it.durationSeconds) }

/** A quick template built on the watch, sent to the phone to be saved. */
@Serializable
data class CreateTemplateRequest(
    val name: String,
    val rounds: Int,
    val workSeconds: Int,
    val restSeconds: Int,
)

object CreateTemplateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(request: CreateTemplateRequest): String =
        json.encodeToString(CreateTemplateRequest.serializer(), request)

    fun decode(raw: String): CreateTemplateRequest =
        json.decodeFromString(CreateTemplateRequest.serializer(), raw)
}

@Serializable
data class SyncedInterval(
    val name: String,
    val type: String,
    val durationSeconds: Int,
)

@Serializable
data class SyncedTemplate(
    val id: Long,
    val name: String,
    val intervals: List<SyncedInterval>,
)

@Serializable
data class SyncedTemplateList(
    val templates: List<SyncedTemplate>,
)

object TemplateSyncCodec {
    // ignoreUnknownKeys keeps old watches working if the format gains fields.
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(list: SyncedTemplateList): String =
        json.encodeToString(SyncedTemplateList.serializer(), list)

    fun decode(raw: String): SyncedTemplateList =
        json.decodeFromString(SyncedTemplateList.serializer(), raw)
}

/** One heart-rate reading, timed from the start of the workout. */
@Serializable
data class HrSample(
    val elapsedSeconds: Int,
    val bpm: Double,
)

/**
 * A completed workout, recorded on the watch and synced back to the phone.
 * Health values are nullable: they're absent if the sensors were unavailable
 * or permission was denied.
 */
@Serializable
data class WorkoutResult(
    val completedAt: Long,
    val templateName: String? = null,
    val totalSeconds: Int,
    val workSeconds: Int,
    val workIntervals: Int,
    val avgHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val steps: Long? = null,
    val calories: Double? = null,
    val hrSeries: List<HrSample> = emptyList(),
    // The workout's intervals and how many were finished, so History can
    // draw its shape. Absent on results saved by older versions.
    val intervals: List<SyncedInterval> = emptyList(),
    val completedIntervals: Int? = null,
    // Set when the workout ran with the live link, so the phone can tell this
    // result apart from the copy it saved itself. Null for unlinked workouts.
    val linkId: String? = null,
)

object HrSeriesCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = kotlinx.serialization.builtins.ListSerializer(HrSample.serializer())

    fun encode(series: List<HrSample>): String = json.encodeToString(serializer, series)

    fun decode(raw: String): List<HrSample> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}

object ResultSyncCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(result: WorkoutResult): String =
        json.encodeToString(WorkoutResult.serializer(), result)

    fun decode(raw: String): WorkoutResult =
        json.decodeFromString(WorkoutResult.serializer(), raw)
}

/** Converts a synced template into the interval list the timer runs. */
fun SyncedTemplate.toIntervals(): List<Interval> = intervals.map {
    Interval(
        name = it.name,
        type = runCatching { IntervalType.valueOf(it.type) }.getOrDefault(IntervalType.WORK),
        durationSeconds = it.durationSeconds,
    )
}

val SyncedTemplate.totalSeconds: Int get() = intervals.sumOf { it.durationSeconds }

object IntervalListCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer =
        kotlinx.serialization.builtins.ListSerializer(SyncedInterval.serializer())

    fun encode(intervals: List<SyncedInterval>): String = json.encodeToString(serializer, intervals)

    fun decode(raw: String): List<SyncedInterval> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}

fun List<SyncedInterval>.toDomainIntervals(): List<Interval> = map {
    Interval(
        name = it.name,
        type = runCatching { IntervalType.valueOf(it.type) }.getOrDefault(IntervalType.WORK),
        durationSeconds = it.durationSeconds,
    )
}
