package com.august.intervaltimer.workout

import android.content.Context
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.LinkedTimer
import com.august.intervaltimer.data.PhoneLiveLink
import com.august.intervaltimer.data.WatchLinkMode
import com.august.intervaltimer.data.WatchRequest
import com.august.intervaltimer.data.recordPhoneWorkout
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.sync.LiveControl
import com.august.intervaltimer.sync.applyLiveControl
import com.august.intervaltimer.timer.TimerEngine
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The phone's one workout. It belongs to the app process, not to a screen, so
 * the countdown, the cues and the saving all carry on while the screen is
 * locked or another app is in front. [PhoneWorkoutService] keeps the process
 * alive for as long as a workout runs.
 *
 * The timer screen and the notification both call the same functions here,
 * so a pause is a pause wherever it was tapped.
 */
object PhoneWorkout {

    // Everything runs on the main thread, like the screens that call in.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = TimerEngine(scope)

    val uiState: StateFlow<TimerUiState> = engine.uiState

    private val _sessionName = MutableStateFlow<String?>(null)

    /** Name of the workout being run; it is saved with it in History. */
    val sessionName: StateFlow<String?> = _sessionName.asStateFlow()

    /** True from start until the user leaves the finish screen. */
    val isActive: Boolean get() = uiState.value.intervals.isNotEmpty()

    private var app: TimerApp? = null
    private var settings: StateFlow<AppSettings>? = null
    private var cuePlayer: CuePlayer? = null
    private var saved = false

    /** What [PhoneLiveLink] is allowed to do to the timer. */
    private val linkedTimer = object : LinkedTimer {
        override val uiState: StateFlow<TimerUiState> get() = engine.uiState
        override fun start(intervals: List<Interval>) = engine.start(intervals)
        override fun applyLiveControl(control: LiveControl, nowEpochMillis: Long) =
            engine.applyLiveControl(control, nowEpochMillis)
    }

    /** Starts a workout on the phone, and on the watch if [watchLink] says so. */
    fun start(context: Context, name: String, intervals: List<Interval>, watchLink: WatchLinkMode) {
        if (intervals.isEmpty()) return
        val app = prepare(context, name)
        settings?.value?.let { cuePlayer?.warmUp(it) }
        engine.start(intervals)
        PhoneLiveLink.begin(app, linkedTimer, name, intervals, watchLink)
        PhoneWorkoutService.start(app)
    }

    /**
     * Joins a workout that is running on the watch. Returns false if that
     * workout is already over, and then nothing is started.
     */
    fun follow(context: Context, pending: WatchRequest): Boolean {
        val app = prepare(context, pending.request.sessionName)
        if (!PhoneLiveLink.follow(app, linkedTimer, pending)) {
            discard()
            return false
        }
        PhoneWorkoutService.start(app)
        return true
    }

    fun togglePause() {
        engine.togglePause()
        PhoneLiveLink.sendState(uiState.value)
    }

    fun skip() {
        engine.skip()
        PhoneLiveLink.sendState(uiState.value)
    }

    /**
     * Ends the workout now and keeps what was done. During get-ready nothing
     * has been done yet, so it simply closes.
     */
    fun end() {
        val before = uiState.value
        if (before.intervals.isEmpty() || before.phase == TimerPhase.FINISHED) return
        if (before.phase == TimerPhase.LEAD_IN) {
            close()
        } else {
            engine.endEarly()
            PhoneLiveLink.sendEnd(before)
        }
    }

    /** Leaves the workout. One that is still going is ended on the watch too. */
    fun close() {
        PhoneLiveLink.leave(uiState.value)
        discard()
    }

    /** The app counts faster while its timer is on screen, to keep the ring smooth. */
    fun setVisible(visible: Boolean) {
        engine.tickMillis = if (visible) VISIBLE_TICK_MILLIS else HIDDEN_TICK_MILLIS
    }

    /** Forgets the workout without saving anything more. */
    private fun discard() {
        engine.stop()
        _sessionName.value = null
        cuePlayer?.release()
        app?.let { PhoneWorkoutService.clearNotification(it) }
    }

    /** Remembers the app and starts the listeners; they then run for the whole process. */
    private fun prepare(context: Context, name: String?): TimerApp {
        _sessionName.value = name
        saved = false
        app?.let { return it }

        val app = context.applicationContext as TimerApp
        this.app = app
        val settings = app.settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
        this.settings = settings
        val cuePlayer = CuePlayer(app)
        this.cuePlayer = cuePlayer

        scope.launch {
            engine.cues.collect { cue ->
                // The engine announces a cue just before it moves to the new
                // interval. Posting lets it finish, so the voice reads the new name.
                launch(Dispatchers.Main) {
                    cuePlayer.play(cue, uiState.value, settings.value, PhoneLiveLink.state.value)
                }
            }
        }
        // Save the workout to History once, when it finishes or is ended early.
        // If the watch is linked, it saves it (with heart rate). A watch that has
        // gone silent (LOST) may not, so then the phone saves its own copy, and
        // the watch's replaces it if it turns up later.
        scope.launch {
            uiState.collect { state ->
                if (state.phase == TimerPhase.FINISHED && !saved && state.elapsedSecondsAtEnd > 0) {
                    saved = true
                    if (!PhoneLiveLink.watchSavesResult) {
                        val name = _sessionName.value
                        val linkId = PhoneLiveLink.currentLinkId
                        launch { app.recordPhoneWorkout(name, state, linkId) }
                    }
                }
            }
        }
        // The watch owns this workout and sync stopped (here or on the watch):
        // drop the phone's copy without saving it.
        scope.launch {
            PhoneLiveLink.stepOut.collect { discard() }
        }
        return app
    }

    private const val VISIBLE_TICK_MILLIS = 50L
    private const val HIDDEN_TICK_MILLIS = 250L
}
