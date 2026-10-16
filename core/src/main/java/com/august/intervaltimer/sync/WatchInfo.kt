package com.august.intervaltimer.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object WatchInfoSync {
    const val PATH = "/watch-info"
    const val KEY = "watch_info_json"
}

/**
 * What the watch app tells the phone about itself, so the phone can show the
 * watch's version and whether its templates are up to date.
 */
@Serializable
data class WatchInfo(
    val appVersionName: String,
    val appVersionCode: Long,
    val templateCount: Int,
    // Null until the watch has received a template list.
    val templatesReceivedAtEpochMillis: Long? = null,
)

object WatchInfoCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(info: WatchInfo): String = json.encodeToString(WatchInfo.serializer(), info)

    /** Null if the text is damaged, so a bad item never crashes the phone. */
    fun decode(raw: String): WatchInfo? =
        runCatching { json.decodeFromString(WatchInfo.serializer(), raw) }.getOrNull()
}
