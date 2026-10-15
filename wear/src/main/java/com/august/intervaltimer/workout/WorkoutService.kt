package com.august.intervaltimer.workout

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.august.intervaltimer.MainActivity
import com.august.intervaltimer.R
import com.august.intervaltimer.haptics.HapticsPlayer
import com.august.intervaltimer.settings.WatchSettings
import com.august.intervaltimer.settings.WatchStores
import com.august.intervaltimer.sync.soundVolumePercent
import com.august.intervaltimer.timer.SoundPlayer
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a workout running when the screen is off. Android pauses ordinary
 * apps soon after their screen goes dark; a foreground service with a
 * visible notification is the system's permission slip to keep going.
 *
 * While a workout runs, this service:
 *  - holds a wake lock so the countdown keeps ticking,
 *  - plays the vibration and sound cues,
 *  - records heart rate, steps, and calories,
 *  - shows the icon on the watch face that leads back to the timer,
 *  - sends the result to the phone when the workout ends.
 */
class WorkoutService : Service() {

    private val scope = MainScope()
    private lateinit var haptics: HapticsPlayer
    private var settings = WatchSettings()
    private var soundPlayer: SoundPlayer? = null
    private var soundPlayerLevel = -1
    private var wakeLock: PowerManager.WakeLock? = null
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WorkoutSession.init(this)
        haptics = HapticsPlayer(this)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout in progress",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen promptly after the service is started.
        startInForeground(buildNotification(WorkoutSession.state.value))

        if (!WorkoutSession.inProgress) {
            shutDown()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        scope.launch { WorkoutSession.beginRecording() }
        if (!watching) {
            watching = true
            watchSession()
        }
        return START_NOT_STICKY
    }

    private fun watchSession() {
        scope.launch {
            WatchStores.settings.filterNotNull().collect { settings = it }
        }
        scope.launch {
            WorkoutSession.cues.collect { playCue(it) }
        }
        scope.launch {
            WorkoutSession.state
                .map { Triple(it.phase, it.currentIndex, it.isPaused) }
                .distinctUntilChanged()
                .collect {
                    val state = WorkoutSession.state.value
                    when {
                        state.intervals.isEmpty() -> shutDown()
                        state.phase == TimerPhase.FINISHED -> {
                            WorkoutSession.finishAndPublish()
                            // Let the finish vibration play out before
                            // giving up the foreground.
                            delay(2_500)
                            if (!WorkoutSession.inProgress) shutDown()
                        }
                        else -> {
                            WorkoutSession.setRecordingPaused(state.isPaused)
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, buildNotification(state))
                        }
                    }
                }
        }
    }

    private fun playCue(cue: TimerCue) {
        val cues = settings.cues
        if (cues.vibrationEnabled) haptics.play(cue, cues.vibrationLevel)
        if (!cues.soundEnabled) return
        if (soundPlayer == null || soundPlayerLevel != cues.soundLevel) {
            soundPlayer?.release()
            soundPlayer = SoundPlayer(cues.soundVolumePercent)
            soundPlayerLevel = cues.soundLevel
        }
        when (cue) {
            TimerCue.COUNTDOWN_TICK -> soundPlayer?.countdownTick()
            TimerCue.WORK_START -> soundPlayer?.workStart()
            TimerCue.REST_START -> soundPlayer?.restStart()
            TimerCue.FINISH -> soundPlayer?.finish()
        }
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            // The "health" type is only allowed once a sensor permission has
            // been granted. Without one the timer still runs, as a plain
            // special-use service with no heart rate.
            val type = if (hasSensorPermission()) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasSensorPermission(): Boolean =
        listOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun buildNotification(state: TimerUiState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = statusText(state)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("Interval Timer")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)

        // The Ongoing Activity is what puts the tappable icon on the watch
        // face and in the recent apps list.
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_timer_notification)
            .setTouchIntent(openApp)
            .setStatus(Status.Builder().addTemplate(text).build())
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    private fun statusText(state: TimerUiState): String = when {
        state.intervals.isEmpty() -> "Starting"
        state.isPaused -> "Paused"
        state.phase == TimerPhase.LEAD_IN -> "Get ready"
        state.phase == TimerPhase.FINISHED -> "Finished"
        else -> "${state.currentInterval?.name.orEmpty()} · " +
            "${state.currentIndex + 1}/${state.intervals.size}"
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IntervalTimer:workout")
            .apply { acquire(WAKE_LOCK_LIMIT_MILLIS) }
    }

    private fun releaseResources() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        soundPlayer?.release()
        soundPlayer = null
    }

    private fun shutDown() {
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseResources()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "workout"
        const val NOTIFICATION_ID = 1
        // A safety net so a forgotten workout can't hold the watch awake forever.
        const val WAKE_LOCK_LIMIT_MILLIS = 6 * 60 * 60 * 1000L
    }
}
