package com.august.intervaltimer.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Tells the phone which version of the watch app this is and how many
 * templates it holds, so the phone's Devices card can show them. Called when
 * the app process starts and whenever templates arrive.
 */
suspend fun publishWatchInfo(context: Context, templateCount: Int, receivedAtEpochMillis: Long?) {
    runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val info = WatchInfo(
            appVersionName = packageInfo.versionName ?: "?",
            appVersionCode = packageInfo.longVersionCode,
            templateCount = templateCount,
            templatesReceivedAtEpochMillis = receivedAtEpochMillis,
        )
        val request = PutDataMapRequest.create(WatchInfoSync.PATH)
            .apply { dataMap.putString(WatchInfoSync.KEY, WatchInfoCodec.encode(info)) }
            .asPutDataRequest()
            .setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }
}
