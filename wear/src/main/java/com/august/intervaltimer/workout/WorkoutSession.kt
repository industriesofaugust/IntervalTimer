package com.august.intervaltimer.workout

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.august.intervaltimer.metrics.LiveMetrics
import com.august.intervaltimer.metrics.WorkoutMetricsRecorder
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.LiveAck
import com.august.intervaltimer.sync.LiveControl
import com.august.intervaltimer.sync.LiveLinkCodec
import com.august.intervaltimer.sync.LinkState
import com.august.intervaltimer.sync.LiveMetricsUpdate
import com.august.intervaltimer.sync.LivePaths
import com.august.intervaltimer.sync.LiveRequest
import com.august.intervaltimer.sync.LiveStart
import com.august.intervaltimer.sync.LiveUnlink
import com.august.intervaltimer.sync.ResultSyncCodec
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.sync.WorkoutResult
import com.august.intervaltimer.sync.applyLiveControl
import com.august.intervaltimer.sync.toDomainIntervals
import com.august.intervaltimer.sync.toLiveControl
import com.august.intervaltimer.sync.toSyncedIntervals
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.timer.TimerEngine
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * The one workout the watch can be running. It lives for the whole app
 * process rather than inside a screen, so the countdown carries on when the
 * screen turns off or the user leaves the app. [WorkoutService] keeps the
 * process alive while a workout is running; the screens only watch [state].
 */
object WorkoutSession {

    private val scope = MainScope()
    private val engine = TimerEngine(scope)

    val state: StateFlow<TimerUiState> = engine.uiState
    val cues: SharedFlow<TimerCue> = engine.cues

    private val _live = MutableStateFlow(LiveMetrics())
    val live: StateFlow<LiveMetrics> = _live.asStateFlow()

    private var appContext: Context? = null
    private var recorder: WorkoutMetricsRecorder? = null
    private var sessionName: String? = null
    private var resultPublished = true

    val inProgress: Boolean
        get() = state.value.intervals.isNotEmpty() && state.value.phase != TimerPhase.FINISHED

    /** Safe to call from anywhere, any number of times. */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val newRecorder = WorkoutMetricsRecorder(app)
        recorder = newRecorder
        scope.launch { newRecorder.live.collect { _live.value = it } }
    }

    /** A workout the user started on the watch. */
    fun start(context: Context, name: String, intervals: List<Interval>) {
        if (intervals.isEmpty()) return
        forgetLink()
        begin(context, name, intervals, elapsedMillis = 0L)
    }

    /** False if the foreground service could not be started. */
    private fun begin(
        context: Context,
        name: String,
        intervals: List<Interval>,
        elapsedMillis: Long,
    ): Boolean {
        init(context)
        sessionName = name
        resultPublished = false
        engine.start(intervals, elapsedMillis)
        val app = context.applicationContext
        return runCatching {
            ContextCompat.startForegroundService(app, Intent(app, WorkoutService::class.java))
        }.isSuccess
    }

    // The user's own taps are also sent to the phone when linked. Messages
    // from the phone go straight to the engine, so nothing is sent back.

    fun togglePause() {
        val wasRunning = inProgress
        engine.togglePause()
        if (wasRunning) sendControl(state.value)
    }

    fun skip() {
        val wasRunning = inProgress
        engine.skip()
        if (wasRunning) sendControl(state.value)
    }

    fun endEarly() {
        val wasRunning = inProgress
        val before = state.value
        engine.endEarly()
        // Sent from just before ending, so the phone ends at the same place.
        if (wasRunning) sendControl(before, ended = true)
        withdrawRequest(before)
    }

    /** Dismisses the finished summary and returns to "no workout". */
    fun clear() {
        forgetLink()
        engine.stop()
    }

    // --- The live link: a workout the phone started and the watch joined. ---

    /** [phoneOwned]: the phone started this workout and keeps it if syncing stops. */
    private class Link(val id: String, val phoneNodeId: String, val phoneOwned: Boolean)

    private var link: Link? = null
    private var lastControlSentAt = 0L

    private val _linkState = MutableStateFlow(LinkState.OFF)

    /**
     * LINKED while the phone runs this workout too. CONNECTING while a SYNC
     * PHONE request waits for the phone. There is no LOST here: the phone
     * sends no heartbeat, so the watch cannot tell a quiet phone from a gone one.
     */
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private fun forgetLink() {
        link = null
        requested = null
        requestTimeout?.cancel()
        _linkState.value = LinkState.OFF
    }

    /**
     * The phone started a workout. Joins it, lined up with the phone's clock,
     * and answers the phone either way. Safe to call from any thread.
     */
    fun onLiveStart(context: Context, message: LiveStart, phoneNodeId: String) {
        scope.launch {
            val accepted = join(context, message, phoneNodeId)
            val ack = LiveAck(message.linkId, accepted)
            sendTo(context.applicationContext, phoneNodeId, LivePaths.ACK, LiveLinkCodec.encode(ack))
        }
    }

    private fun join(context: Context, message: LiveStart, phoneNodeId: String): Boolean {
        // The same message arriving twice is not a second workout.
        if (inProgress && link?.id == message.linkId) return true
        // The user's own workout wins.
        if (inProgress) return false

        val intervals = message.intervals.toDomainIntervals()
        val elapsed = System.currentTimeMillis() - message.startedAtEpochMillis
        val totalMillis = TimerUiState.LEAD_IN_MILLIS + intervals.sumOf { it.durationSeconds * 1000L }
        if (intervals.isEmpty() || elapsed >= totalMillis) return false

        link = Link(message.linkId, phoneNodeId, phoneOwned = true)
        lastControlSentAt = 0L
        if (begin(context, message.sessionName, intervals, elapsed)) {
            _linkState.value = LinkState.LINKED
            return true
        }
        // Android did not allow the foreground service; carry on without the watch.
        link = null
        engine.stop()
        return false
    }

    // --- SYNC PHONE: a workout the watch started, offered to the phone. ---

    private class Request(val id: String, val nodeIds: List<String>)

    private var requested: Request? = null
    private var requestTimeout: Job? = null

    /**
     * Asks the phone to follow this workout. The phone only joins when the
     * user taps its notification, so an answer can take a while.
     */
    fun requestPhoneSync() {
        val context = appContext ?: return
        if (!inProgress || link != null || _linkState.value == LinkState.CONNECTING) return
        _linkState.value = LinkState.CONNECTING
        requestTimeout?.cancel()
        requestTimeout = scope.launch {
            delay(REQUEST_WAIT_MILLIS)
            // Back to SYNC PHONE. The request stays valid: a late tap on the phone still links.
            if (link == null) _linkState.value = LinkState.OFF
        }
        scope.launch {
            val nodeIds = runCatching {
                Wearable.getNodeClient(context).connectedNodes.await().map { it.id }
            }.getOrDefault(emptyList())
            if (link != null) return@launch
            if (nodeIds.isEmpty()) {
                requestTimeout?.cancel()
                _linkState.value = LinkState.OFF
                return@launch
            }
            // A new tap replaces the old request, so the phone doesn't show two.
            withdrawRequest(state.value)
            val id = UUID.randomUUID().toString()
            requested = Request(id, nodeIds)
            val request = LiveRequest(
                linkId = id,
                sessionName = sessionName.orEmpty(),
                intervals = state.value.intervals.toSyncedIntervals(),
                position = state.value.toLiveControl(id, System.currentTimeMillis()),
            )
            val bytes = LiveLinkCodec.encode(request)
            nodeIds.forEach { sendTo(context, it, LivePaths.REQUEST, bytes) }
        }
    }

    /** The phone answered a request. Safe to call from any thread. */
    fun onLiveAck(ack: LiveAck, phoneNodeId: String) {
        scope.launch {
            val pending = requested ?: return@launch
            if (ack.linkId != pending.id || !ack.accepted || !inProgress || link != null) {
                return@launch
            }
            link = Link(pending.id, phoneNodeId, phoneOwned = false)
            lastControlSentAt = 0L
            requested = null
            requestTimeout?.cancel()
            _linkState.value = LinkState.LINKED
        }
    }

    /** Tells the phone to drop a request that is no longer wanted, such as when the workout ends. */
    private fun withdrawRequest(snapshotOf: TimerUiState) {
        val context = appContext ?: return
        val pending = requested ?: return
        requested = null
        val control = snapshotOf.toLiveControl(pending.id, System.currentTimeMillis(), ended = true)
        val bytes = LiveLinkCodec.encode(control)
        pending.nodeIds.forEach { sendTo(context, it, LivePaths.CONTROL, bytes) }
    }

    /** The phone paused, resumed, skipped or ended. Safe to call from any thread. */
    fun onLiveControl(control: LiveControl) {
        scope.launch {
            val current = link ?: return@launch
            // A message that crossed with a newer one is stale.
            if (control.linkId != current.id || control.sentAtEpochMillis < lastControlSentAt) {
                return@launch
            }
            lastControlSentAt = control.sentAtEpochMillis
            engine.applyLiveControl(control, System.currentTimeMillis())
        }
    }

    // --- Stopping the sync: the owner keeps the workout, the other device drops its copy. ---

    private val _stepOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when this watch drops a workout the phone owns; the timer screen goes back Home. */
    val stepOut: SharedFlow<Unit> = _stepOut.asSharedFlow()

    /** The user confirmed STOP SYNC? on the watch. */
    fun stopSync() {
        val current = link ?: return
        send(LivePaths.UNLINK, LiveLinkCodec.encode(LiveUnlink(current.id)))
        syncStopped(current)
    }

    /** The phone stopped syncing. Safe to call from any thread. */
    fun onLiveUnlink(unlink: LiveUnlink) {
        scope.launch {
            val current = link ?: return@launch
            if (unlink.linkId == current.id) syncStopped(current)
        }
    }

    private fun syncStopped(stopped: Link) {
        if (!inProgress) return
        if (stopped.phoneOwned) {
            dropCopy()
        } else {
            // The watch's own workout carries on alone; SYNC PHONE can link it again.
            forgetLink()
        }
    }

    /**
     * Stops this copy of a phone-owned workout without saving it: the phone
     * keeps the workout and saves it. Nothing is published.
     */
    private fun dropCopy() {
        resultPublished = true
        forgetLink()
        engine.stop()
        scope.launch { recorder?.stop() }
        _stepOut.tryEmit(Unit)
    }

    /** Called every few seconds by [WorkoutService] while a linked workout runs. */
    fun sendLiveMetrics() {
        val current = link ?: return
        if (!inProgress) return
        val metrics = live.value
        val update = LiveMetricsUpdate(current.id, metrics.currentHeartRate, metrics.calories, metrics.steps)
        send(LivePaths.METRICS, LiveLinkCodec.encode(update))
    }

    private fun sendControl(snapshotOf: TimerUiState, ended: Boolean = false) {
        val current = link ?: return
        val control = snapshotOf.toLiveControl(current.id, System.currentTimeMillis(), ended)
        send(LivePaths.CONTROL, LiveLinkCodec.encode(control))
    }

    private fun send(path: String, bytes: ByteArray) {
        val context = appContext ?: return
        val nodeId = link?.phoneNodeId ?: return
        sendTo(context, nodeId, path, bytes)
    }

    // Best effort: if the phone is out of reach the workout simply goes on alone.
    private fun sendTo(context: Context, nodeId: String, path: String, bytes: ByteArray) {
        runCatching { Wearable.getMessageClient(context).sendMessage(nodeId, path, bytes) }
    }

    /**
     * The ring only needs smooth updates while someone is looking at it.
     * With the screen off or dimmed, a slower tick saves battery.
     */
    fun setSmoothTicks(smooth: Boolean) {
        engine.tickMillis = if (smooth) 100L else 200L
    }

    // --- Called by WorkoutService, which runs in the foreground. ---

    suspend fun beginRecording() {
        recorder?.stop()
        recorder?.start()
    }

    /** Keeps paused time out of the heart rate, step, and calorie totals. */
    suspend fun setRecordingPaused(paused: Boolean) {
        recorder?.setPaused(paused)
    }

    /** Stops the sensors and sends the finished workout to the phone, once. */
    suspend fun finishAndPublish() {
        if (resultPublished) return
        resultPublished = true
        val app = appContext ?: return
        val activeRecorder = recorder ?: return
        val finished = state.value
        val linkId = link?.id
        withdrawRequest(finished)
        if (link == null) {
            requestTimeout?.cancel()
            _linkState.value = LinkState.OFF
        }

        val metrics = activeRecorder.stop()
        // Ended during get-ready: nothing was done, so there is nothing to save.
        if (finished.elapsedSecondsAtEnd == 0) return
        val completedAt = System.currentTimeMillis()
        // If the user ended early, only count what was actually done.
        val completedList =
            if (finished.endedEarly) finished.intervals.take(finished.completedIntervals)
            else finished.intervals
        val workIntervals = completedList.filter { it.type == IntervalType.WORK }
        // Thin the series so the payload stays small on long workouts.
        val series = activeRecorder.heartRateSeries().let { all ->
            if (all.size <= 600) all
            else {
                val step = all.size / 600 + 1
                all.filterIndexed { index, _ -> index % step == 0 }
            }
        }
        val result = WorkoutResult(
            completedAt = completedAt,
            templateName = sessionName,
            totalSeconds = finished.elapsedSecondsAtEnd,
            workSeconds = workIntervals.sumOf { it.durationSeconds },
            workIntervals = workIntervals.size,
            avgHeartRate = metrics.averageHeartRate,
            maxHeartRate = metrics.maxHeartRate,
            steps = metrics.steps.takeIf { it > 0 },
            calories = metrics.calories.takeIf { it > 0 },
            hrSeries = series,
            intervals = finished.intervals.toSyncedIntervals(),
            completedIntervals = finished.completedIntervals,
            linkId = linkId,
        )
        // Data items persist, so the result reaches the phone even if it
        // isn't nearby right now.
        runCatching {
            val request = PutDataMapRequest
                .create("${SyncPaths.RESULTS_PATH_PREFIX}/$completedAt")
                .apply { dataMap.putString(SyncPaths.RESULT_KEY, ResultSyncCodec.encode(result)) }
                .asPutDataRequest()
                .setUrgent()
            Wearable.getDataClient(app).putDataItem(request).await()
        }
    }

    private const val REQUEST_WAIT_MILLIS = 60_000L
}
