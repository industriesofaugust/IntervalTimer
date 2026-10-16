package com.august.intervaltimer.data

import android.content.Context
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.sync.LiveAck
import com.august.intervaltimer.sync.LiveControl
import com.august.intervaltimer.sync.LiveLinkCodec
import com.august.intervaltimer.sync.LiveMetricsUpdate
import com.august.intervaltimer.sync.LivePaths
import com.august.intervaltimer.sync.LiveRequest
import com.august.intervaltimer.sync.LiveStart
import com.august.intervaltimer.sync.LinkState
import com.august.intervaltimer.sync.SyncedInterval
import com.august.intervaltimer.sync.toDomainIntervals
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** The watch asked the phone to follow its workout, and which watch asked. */
data class WatchRequest(val request: LiveRequest, val nodeId: String)

/** How long the watch gets to answer a start before the tag says NO WATCH. */
private const val CONNECT_TIMEOUT_MILLIS = 4_000L

/** A linked watch sends metrics every few seconds; this long without any means LOST. */
private const val STALE_MILLIS = 20_000L

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

    private val _state = MutableStateFlow(LinkState.OFF)

    /** What the watch is doing for this workout; shown as the tag on the timer. */
    val state: StateFlow<LinkState> = _state.asStateFlow()

    /**
     * True once the watch has the workout, even if it has gone quiet. Then the
     * watch saves the result, so the phone must not.
     */
    val watchHasWorkout: Boolean
        get() = _state.value == LinkState.LINKED || _state.value == LinkState.LOST

    private val _metrics = MutableStateFlow<LiveMetricsUpdate?>(null)

    /** The watch's latest heart rate and calories, or null if none yet. */
    val metrics: StateFlow<LiveMetricsUpdate?> = _metrics.asStateFlow()

    private val _watchRequest = MutableStateFlow<WatchRequest?>(null)

    /** A workout the watch is asking the phone to follow; null when there is none. */
    val watchRequest: StateFlow<WatchRequest?> = _watchRequest.asStateFlow()

    /**
     * Set by MainActivity so the listener service knows whether Home is on
     * screen, where the request shows as a banner instead of a notification.
     */
    @Volatile
    var appVisible = false

    @Volatile
    var homeVisible = false

    private var appContext: Context? = null
    private var timer: TimerViewModel? = null
    private var linkId: String? = null
    private var sessionName = ""
    private var intervals: List<SyncedInterval> = emptyList()
    private var watchNodeId: String? = null
    private var lastControlSentAt = 0L
    private var listening = false
    private var connectJob: Job? = null
    private var staleJob: Job? = null

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

        val id = UUID.randomUUID().toString()
        val synced = intervals.toSyncedIntervals()
        attach(context, timer, id, name ?: "Workout", synced)
        val start = LiveStart(
            linkId = id,
            sessionName = sessionName,
            intervals = synced,
            startedAtEpochMillis = startedAt,
        )
        waitForWatch()
        send(LivePaths.START, LiveLinkCodec.encode(start))
    }

    /**
     * The user tapped the NO WATCH tag: start again, lined up with where the
     * phone timer is now. The same linkId is used, so a watch that is already
     * running this workout just answers yes.
     */
    fun retry() {
        val id = linkId ?: return
        val state = timer?.uiState?.value ?: return
        if (_state.value != LinkState.NO_WATCH || state.phase == TimerPhase.FINISHED) return

        val start = LiveStart(
            linkId = id,
            sessionName = sessionName,
            intervals = intervals,
            startedAtEpochMillis = System.currentTimeMillis() - state.millisSinceStart(),
        )
        waitForWatch()
        send(LivePaths.START, LiveLinkCodec.encode(start))
    }

    /**
     * The user chose to follow a workout running on the watch: start the phone
     * timer at the watch's position, link with the watch's linkId and say yes.
 * Returns false if the workout is already over, and nothing was started.
     */
    fun follow(context: Context, timer: TimerViewModel, pending: WatchRequest): Boolean {
        val request = pending.request
        end()
        clearWatchRequest()

        val domainIntervals = request.intervals.toDomainIntervals()
        val now = System.currentTimeMillis()
        val position = request.position.agedTo(domainIntervals, now)
        if (domainIntervals.isEmpty() || position.ended || position.index >= domainIntervals.size) return false

        attach(context, timer, request.linkId, request.sessionName, request.intervals)
        watchNodeId = pending.nodeId
        lastControlSentAt = request.position.sentAtEpochMillis
        timer.start(domainIntervals)
        timer.applyLiveControl(position, now)
        _state.value = LinkState.LINKED
        heardFromWatch()
        send(LivePaths.ACK, LiveLinkCodec.encode(LiveAck(request.linkId, accepted = true)))
        return true
    }

    /** A request from the watch has arrived and is waiting for the user's answer. */
    fun offerWatchRequest(pending: WatchRequest) {
        // The workout we are already following is not a new offer.
        if (watchHasWorkout && pending.request.linkId == linkId) return
        _watchRequest.value = pending
    }

    fun clearWatchRequest() {
        _watchRequest.value = null
    }

    /** Forgets the link, when the user leaves the timer screen. */
    fun end() {
        connectJob?.cancel()
        staleJob?.cancel()
        linkId = null
        watchNodeId = null
        timer = null
        _state.value = LinkState.OFF
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
        if (!watchHasWorkout) return
        send(LivePaths.CONTROL, LiveLinkCodec.encode(state.toLiveControl(id, System.currentTimeMillis())))
    }

    /** Pass the state from just before the user ended the workout on the phone. */
    fun sendEnd(stateBeforeEnd: TimerUiState) {
        val id = linkId ?: return
        if (!watchHasWorkout) return
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
                if (fromLinkedWatch(event, control.linkId, id)) {
                    heardFromWatch()
                    onControl(control)
                }
            }
            LivePaths.METRICS -> {
                val metrics = LiveLinkCodec.decodeMetrics(event.data) ?: return
                if (fromLinkedWatch(event, metrics.linkId, id)) {
                    _metrics.value = metrics
                    heardFromWatch()
                }
            }
        }
    }

    private fun attach(
        context: Context,
        timer: TimerViewModel,
        id: String,
        name: String,
        intervals: List<SyncedInterval>,
    ) {
        val app = context.applicationContext
        appContext = app
        this.timer = timer
        linkId = id
        sessionName = name
        this.intervals = intervals
        lastControlSentAt = 0L
        if (!listening) {
            listening = true
            Wearable.getMessageClient(app).addListener(this)
        }
    }

    /** Shows CONNECTING, and gives up with NO_WATCH if nothing answers in time. */
    private fun waitForWatch() {
        _state.value = LinkState.CONNECTING
        connectJob?.cancel()
        connectJob = scope.launch {
            delay(CONNECT_TIMEOUT_MILLIS)
            _state.compareAndSet(LinkState.CONNECTING, LinkState.NO_WATCH)
        }
    }

    // A late yes still counts: a watch that was busy for a moment may join
    // after the tag has already said NO WATCH.
    private fun onAck(ack: LiveAck, id: String, nodeId: String) {
        if (ack.linkId != id || watchHasWorkout) return
        if (!ack.accepted) {
            connectJob?.cancel()
            _state.value = LinkState.NO_WATCH
            return
        }
        watchNodeId = nodeId
        connectJob?.cancel()
        _state.value = LinkState.LINKED
        heardFromWatch()
        // A retry may have come while paused, which a start message cannot say.
        timer?.uiState?.value?.takeIf { it.isPaused }?.let { sendState(it) }
    }

    /** Any sign of life from the watch restarts the 20 second clock. */
    private fun heardFromWatch() {
        _state.compareAndSet(LinkState.LOST, LinkState.LINKED)
        staleJob?.cancel()
        staleJob = scope.launch {
            delay(STALE_MILLIS)
            _state.compareAndSet(LinkState.LINKED, LinkState.LOST)
        }
    }

    /** A message that crossed with a newer one is ignored. */
    private fun onControl(control: LiveControl) {
        if (control.sentAtEpochMillis < lastControlSentAt) return
        lastControlSentAt = control.sentAtEpochMillis
        timer?.applyLiveControl(control, System.currentTimeMillis())
    }

    private fun fromLinkedWatch(event: MessageEvent, messageLinkId: String, id: String): Boolean =
        watchHasWorkout && messageLinkId == id && event.sourceNodeId == watchNodeId

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

/** How long the workout has been running, counting the get-ready countdown. */
private fun TimerUiState.millisSinceStart(): Long = when (phase) {
    TimerPhase.LEAD_IN -> TimerUiState.LEAD_IN_MILLIS - remainingMillis
    else -> TimerUiState.LEAD_IN_MILLIS + elapsedMillis
}

/**
 * Where the watch timer is by now. A request can wait in a notification for a
 * while, and applyLiveControl only allows for a few seconds of travel time.
 */
private fun LiveControl.agedTo(intervals: List<Interval>, nowEpochMillis: Long): LiveControl {
    if (paused || ended) return this
    var index = index
    var remaining = remainingMillis - (nowEpochMillis - sentAtEpochMillis).coerceAtLeast(0L)
    while (remaining <= 0 && index < intervals.size) {
        index++
        if (index < intervals.size) remaining += intervals[index].durationSeconds * 1000L
    }
    return copy(index = index, remainingMillis = remaining.coerceAtLeast(0L), sentAtEpochMillis = nowEpochMillis)
}
