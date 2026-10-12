package com.august.intervaltimer.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Mirrors the watch's cue settings to the Data Layer so the phone's Settings
 * page shows the real values. Called when the watch app starts and whenever
 * a setting changes; without the start-up call the phone would only have
 * defaults to show, and editing one would overwrite the rest.
 */
suspend fun publishWatchCues(context: Context, cues: WatchCueSettings) {
    runCatching {
        val request = PutDataMapRequest.create(SyncPaths.WATCH_SETTINGS_PATH)
            .apply {
                dataMap.putString(
                    SyncPaths.WATCH_SETTINGS_KEY,
                    WatchCueSettingsCodec.encode(cues),
                )
            }
            .asPutDataRequest()
            .setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }
}
