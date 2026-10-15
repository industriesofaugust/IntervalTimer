package com.august.intervaltimer.data

import android.content.Context
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.sync.LiveAck
import com.august.intervaltimer.sync.LiveControl
import com.august.intervaltimer.sync.LiveLinkCodec
import com.august.intervaltimer.sync.LiveMetricsUpdate
import com.august.intervaltimer.sync.LivePaths
import com.august.intervaltimer.sync.LiveStart
import com.august.intervaltimer.sync.toLiveControl
import com.august.intervaltimer.sync.toSyncedIntervals
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.timer.TimerViewModel
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * The phone's end of the live link: a phone workout also runs on the watch.
 *
 * It lives for the whole app process and listens only while the app is in
 * use, which is enough because the phone timer keeps the screen on. Every
 * message is best effort: if the watch is away or sending fails, the phone
 * workout simply carries on alone.
 *
 * The screen calls [sendState] and [sendEnd] for things the user does on the
 * phone. Messages from the watch go straight to the timer without passing
 * those, so the two devices never echo each other.
 */
object PhoneLiveLink : MessageClient.OnMessageReceivedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _linked = MutableStateFlow(false)

    /** True once the watch has joined this workout. */
    val linked: StateFlow<Boolean> = _linked.asStateFlow()

    private val _metrics = MutableStateFlow<LiveMetricsUpdate?>(null)

    /** The watch's latest heart rate and calories, or null if none yet. */
    val metrics: StateFlow<LiveMetricsUpdate?> = _metrics.asStateFlow()

    private var appContext: Context? = null
    private var timer: TimerViewModel? = null
    private var linkId: String? = null
    private var watchNodeId: String? = null
    private var lastControlSentAt = 0L
    private var listening = false

    /**
     * Call right after the phone timer starts. With [enabled] off (the "Use
     * watch" setting) nothing is sent.
     */
    fun begin(
        context: Context,
        timer: TimerViewModel,
        name: String?,
        intervals: List<Interval>,
        enabled: Boolean,
    ) {
        val startedAt = System.currentTimeMillis()
        end()
        if (!enabled) return

        val app = context.applicationContext
        appContext = app
        this.timer = timer
        val id = UUID.randomUUID().toString()
        linkId = id
        lastControlSentAt = 0L
        if (!listening) {
            listening = true
            Wearable.getMessageClient(app).addListener(this)
        }

        val start = LiveStart(
            linkId = id,
            sessionName = name ?: "Workout",
            intervals = intervals.toSyncedIntervals(),
            startedAtEpochMillis = startedAt,
        )
        send(LivePaths.START, LiveLinkCodec.encode(start))
    }

    /** Forgets the link, when the user leaves the timer screen. */
    fun end() {
        linkId = null
        watchNodeId = null
        timer = null
        _linked.value = false
        _metrics.value = null
    }

    /** Leaving the timer screen: a workout still going is ended on the watch too. */
    fun leave(state: TimerUiState) {
        if (state.phase != TimerPhase.FINISHED) sendEnd(state)
        end()
    }

    /** After the user paused, resumed or skipped on the phone. */
    fun sendState(state: TimerUiState) {
        val id = linkId ?: return
        if (!_linked.value) return
        send(LivePaths.CONTROL, LiveLinkCodec.encode(state.toLiveControl(id, System.currentTimeMillis())))
    }

    /** Pass the state from just before the user ended the workout on the phone. */
    fun sendEnd(stateBeforeEnd: TimerUiState) {
        val id = linkId ?: return
        if (!_linked.value) return
        val control = stateBeforeEnd.toLiveControl(id, System.currentTimeMillis(), ended = true)
        send(LivePaths.CONTROL, LiveLinkCodec.encode(control))
    }

    override fun onMessageReceived(event: MessageEvent) {
        val id = linkId ?: return
        when (event.path) {
            LivePaths.ACK -> {
                val ack = LiveLinkCodec.decodeAck(event.data) ?: return
                onAck(ack, id, event.sourceNodeId)
            }
            LivePaths.CONTROL -> {
                val control = LiveLinkCodec.decodeControl(event.data) ?: return
                if (fromLinkedWatch(event, control.linkId, id)) onControl(control)
            }
            LivePaths.METRICS -> {
                val metrics = LiveLinkCodec.decodeMetrics(event.data) ?: return
                if (fromLinkedWatch(event, metrics.linkId, id)) _metrics.value = metrics
            }
        }
    }

    // The watch answers whenever it gets to it, even after the get-ready
    // countdown; the workout is on the watch as soon as it says yes.
    private fun onAck(ack: LiveAck, id: String, nodeId: String) {
        if (ack.linkId != id || !ack.accepted || _linked.value) return
        watchNodeId = nodeId
        _linked.value = true
    }

    /** A message that crossed with a newer one is ignored. */
    private fun onControl(control: LiveControl) {
        if (control.sentAtEpochMillis < lastControlSentAt) return
        lastControlSentAt = control.sentAtEpochMillis
        timer?.applyLiveControl(control, System.currentTimeMillis())
    }

    private fun fromLinkedWatch(event: MessageEvent, messageLinkId: String, id: String): Boolean =
        _linked.value && messageLinkId == id && event.sourceNodeId == watchNodeId

    /** To the watch that joined, or to every connected node while still looking for one. */
    private fun send(path: String, bytes: ByteArray) {
        val context = appContext ?: return
        val target = watchNodeId
        scope.launch {
            runCatching {
                val nodeIds = if (target != null) {
                    listOf(target)
                } else {
                    Wearable.getNodeClient(context).connectedNodes.await().map { it.id }
                }
                val client = Wearable.getMessageClient(context)
                for (nodeId in nodeIds) {
                    runCatching { client.sendMessage(nodeId, path, bytes).await() }
                }
            }
        }
    }
}
