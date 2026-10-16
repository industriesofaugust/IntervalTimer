package com.august.intervaltimer.sync

import com.august.intervaltimer.haptics.HapticsPlayer
import com.august.intervaltimer.settings.WatchStores
import com.august.intervaltimer.timer.SoundPlayer
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.workout.WorkoutSession
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Receives control messages from the phone: settings changes made in the
 * phone's Settings page, "play a test sequence" requests, and the live link
 * (a workout started on the phone, its pauses and skips, and its answer to
 * SYNC PHONE). Runs even when the watch app is closed.
 */
class PhoneListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        // Anything that does not decode is simply ignored.
        when (event.path) {
            LivePaths.START -> LiveLinkCodec.decodeStart(event.data)?.let {
                WorkoutSession.onLiveStart(applicationContext, it, event.sourceNodeId)
            }
            LivePaths.ACK -> LiveLinkCodec.decodeAck(event.data)?.let {
                WorkoutSession.onLiveAck(it, event.sourceNodeId)
            }
            LivePaths.CONTROL -> LiveLinkCodec.decodeControl(event.data)?.let {
                WorkoutSession.onLiveControl(it)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            when {
                path == SyncPaths.WATCH_SETTINGS_PATH ->
                    runCatching { applySettings(event.dataItem) }
                path.startsWith(SyncPaths.WATCH_TEST_PATH_PREFIX) -> {
                    // A one-off request: remove it so these don't pile up.
                    runCatching { Wearable.getDataClient(this).deleteDataItems(event.dataItem.uri) }
                    runCatching { playTestSequence() }
                }
            }
        }
    }

    private fun applySettings(item: DataItem) {
        val json = DataMapItem.fromDataItem(item)
            .dataMap.getString(SyncPaths.WATCH_SETTINGS_KEY) ?: return
        val cues = WatchCueSettingsCodec.decode(json)
        runBlocking { WatchStores.repository.setCues(cues) }
    }

    /** Plays 3-2-1, work, rest, finish with the current settings. */
    private fun playTestSequence() = runBlocking {
        val settings = WatchStores.repository.settings.first()
        val haptics = HapticsPlayer(applicationContext)
        val sound =
            if (settings.cues.soundEnabled) SoundPlayer(settings.cues.soundVolumePercent) else null

        fun cue(cue: TimerCue) {
            if (settings.cues.vibrationEnabled) haptics.play(cue, settings.cues.vibrationLevel)
            when (cue) {
                TimerCue.COUNTDOWN_TICK -> sound?.countdownTick()
                TimerCue.WORK_START -> sound?.workStart()
                TimerCue.REST_START -> sound?.restStart()
                TimerCue.FINISH -> sound?.finish()
            }
        }

        repeat(3) {
            cue(TimerCue.COUNTDOWN_TICK)
            delay(700)
        }
        cue(TimerCue.WORK_START)
        delay(1300)
        cue(TimerCue.REST_START)
        delay(1300)
        cue(TimerCue.FINISH)
        sound?.release()
    }
}
