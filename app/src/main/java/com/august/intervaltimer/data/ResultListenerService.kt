package com.august.intervaltimer.data

import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.model.QuickBuildConfig
import com.august.intervaltimer.model.buildSession
import com.august.intervaltimer.sync.CreateTemplateCodec
import com.august.intervaltimer.sync.LiveControl
import com.august.intervaltimer.sync.LiveLinkCodec
import com.august.intervaltimer.sync.LivePaths
import com.august.intervaltimer.sync.ResultSyncCodec
import com.august.intervaltimer.sync.SyncPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Receives data from the watch. Because this is a manifest-registered
 * service, the system starts the app in the background to deliver the data —
 * the phone app doesn't need to be open. Handles finished workouts, "save
 * this template" requests, and a workout on the watch asking the phone to
 * follow along.
 */
class ResultListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            when {
                path.startsWith(SyncPaths.RESULTS_PATH_PREFIX) ->
                    runCatching {
                        handleResult(event.dataItem)
                        discard(event.dataItem)
                    }
                path.startsWith(SyncPaths.CREATE_TEMPLATE_PATH_PREFIX) ->
                    runCatching {
                        handleCreateTemplate(event.dataItem)
                        discard(event.dataItem)
                    }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            LivePaths.REQUEST -> LiveLinkCodec.decodeRequest(event.data)?.let {
                handleWatchRequest(WatchRequest(it, event.sourceNodeId))
            }
            LivePaths.CONTROL -> LiveLinkCodec.decodeControl(event.data)?.let {
                handleWatchControl(it)
            }
        }
    }

    // On Home the app shows its own banner; anywhere else, or closed, it is a notification.
    private fun handleWatchRequest(pending: WatchRequest) {
        val app = application as TimerApp
        val mode = runBlocking { app.settingsRepository.settings.first().watchLink }
        if (mode == WatchLinkMode.OFF || pending.request.position.ended) return

        PhoneLiveLink.offerWatchRequest(pending)
        if (PhoneLiveLink.watchRequest.value != pending) return
        if (!(PhoneLiveLink.appVisible && PhoneLiveLink.homeVisible)) {
            WatchRequestNotification.show(this, pending)
        }
    }

    // The watch workout is over, so there is nothing left to follow.
    private fun handleWatchControl(control: LiveControl) {
        val pending = PhoneLiveLink.watchRequest.value ?: return
        val request = pending.request
        if (control.linkId != request.linkId) return
        if (control.ended || control.index >= request.intervals.size) {
            PhoneLiveLink.clearWatchRequest()
            WatchRequestNotification.cancel(this)
        }
    }

    /**
     * These are one-off messages, not shared state. Once handled, remove
     * them so they don't pile up in the phone-watch sync storage.
     */
    private fun discard(item: DataItem) {
        Wearable.getDataClient(this).deleteDataItems(item.uri)
    }

    private fun handleResult(item: DataItem) {
        val app = application as TimerApp
        val json = DataMapItem.fromDataItem(item)
            .dataMap.getString(SyncPaths.RESULT_KEY) ?: return
        val result = ResultSyncCodec.decode(json)
        runBlocking {
            val entity = app.resultsRepository.insertFromWatch(result)
            // Forward to Health Connect if the user linked it. The watch's result replaces the phone's copy there too.
            HealthConnectWriter(applicationContext).saveIfLinked(entity, fromWatch = true)
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
