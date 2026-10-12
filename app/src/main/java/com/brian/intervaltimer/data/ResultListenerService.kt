package com.brian.intervaltimer.data

import com.brian.intervaltimer.TimerApp
import com.brian.intervaltimer.model.QuickBuildConfig
import com.brian.intervaltimer.model.buildSession
import com.brian.intervaltimer.sync.CreateTemplateCodec
import com.brian.intervaltimer.sync.ResultSyncCodec
import com.brian.intervaltimer.sync.SyncPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * Receives data from the watch. Because this is a manifest-registered
 * service, the system starts the app in the background to deliver the data —
 * the phone app doesn't need to be open. Handles two kinds of messages:
 * finished workouts, and "save this template" requests.
 */
class ResultListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            when {
                path.startsWith(SyncPaths.RESULTS_PATH_PREFIX) ->
                    runCatching { handleResult(event.dataItem) }
                path.startsWith(SyncPaths.CREATE_TEMPLATE_PATH_PREFIX) ->
                    runCatching { handleCreateTemplate(event.dataItem) }
            }
        }
    }

    private fun handleResult(item: DataItem) {
        val app = application as TimerApp
        val json = DataMapItem.fromDataItem(item)
            .dataMap.getString(SyncPaths.RESULT_KEY) ?: return
        val result = ResultSyncCodec.decode(json)
        runBlocking {
            val entity = app.resultsRepository.insert(result)
            // Forward to Health Connect if the user linked it.
            val writer = HealthConnectWriter(applicationContext)
            if (writer.hasAllPermissions()) writer.write(entity)
        }
    }

    private fun handleCreateTemplate(item: DataItem) {
        val app = application as TimerApp
        val json = DataMapItem.fromDataItem(item)
            .dataMap.getString(SyncPaths.CREATE_TEMPLATE_KEY) ?: return
        val request = CreateTemplateCodec.decode(json)
        val intervals = buildSession(
            QuickBuildConfig(request.rounds, request.workSeconds, request.restSeconds)
        )
        runBlocking {
            // Saving triggers the template publisher, so the new template
            // flows straight back to the watch's list.
            app.templatesRepository.saveTemplate(id = 0, name = request.name, intervals = intervals)
        }
    }
}
