package com.august.intervaltimer.data

import android.content.Context
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.sync.SyncedInterval
import com.august.intervaltimer.sync.SyncedTemplate
import com.august.intervaltimer.sync.SyncedTemplateList
import com.august.intervaltimer.sync.TemplateSyncCodec
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Publishes the template list to the Wearable Data Layer. Data items are
 * persistent and synced: the watch receives the latest version whenever it
 * connects, even if it was off when the templates changed.
 */
class TemplateSyncPublisher(private val context: Context) {

    suspend fun publish(templates: List<TemplateWithIntervals>) {
        val payload = TemplateSyncCodec.encode(templates.toSyncedList())
        val request = PutDataMapRequest.create(SyncPaths.TEMPLATES_PATH).apply {
            dataMap.putString(SyncPaths.TEMPLATES_KEY, payload)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }
}

private fun List<TemplateWithIntervals>.toSyncedList() = SyncedTemplateList(
    templates = map { stored ->
        SyncedTemplate(
            id = stored.template.id,
            name = stored.template.name,
            intervals = stored.intervals.sortedBy { it.position }.map {
                SyncedInterval(
                    name = it.name,
                    type = it.type,
                    durationSeconds = it.durationSeconds,
                )
            },
        )
    }
)
