package com.august.intervaltimer.workout

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.august.intervaltimer.MainActivity
import com.august.intervaltimer.R
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.ui.theme.Amber
import com.august.intervaltimer.ui.theme.Coral
import com.august.intervaltimer.ui.theme.Teal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a phone workout alive when the app is not on screen: Android lets a
 * "foreground service" with a visible notification keep running, where an
 * ordinary background app is frozen within seconds. The workout itself lives
 * in [PhoneWorkout]; this only holds the door open and shows the notification
 * with the countdown and the pause, skip and end buttons.
 */
class PhoneWorkoutService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> PhoneWorkout.togglePause()
            ACTION_SKIP -> PhoneWorkout.skip()
            ACTION_END -> PhoneWorkout.end()
        }
        val state = PhoneWorkout.uiState.value
        // Android insists on the notification within seconds of a start request.
        if (intent?.action == null) goForeground(state)
        if (state.intervals.isEmpty()) {
            // Nothing to keep alive (a button tapped on a stale notification).
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            // One notification update per change that shows in it, not per tick.
            // collectLatest: a newer change interrupts the wait after a finish.
            watcher = scope.launch {
                PhoneWorkout.uiState
                    .map { NotificationKey(it.intervals.size, it.phase, it.currentIndex, it.isPaused) }
                    .distinctUntilChanged()
                    .collectLatest { onWorkoutChanged(PhoneWorkout.uiState.value) }
            }
        }
        return START_NOT_STICKY
    }

    private data class NotificationKey(val count: Int, val phase: TimerPhase, val index: Int, val paused: Boolean)

    private fun goForeground(state: TimerUiState) {
        createChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        // Starting can be refused if the app is somehow not in front; the
        // workout then still runs for as long as the screen stays on.
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, runningNotification(state), type) }
    }

    private suspend fun onWorkoutChanged(state: TimerUiState) {
        when {
            state.intervals.isEmpty() -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            state.phase == TimerPhase.FINISHED -> {
                releaseWakeLock()
                notify(finishedNotification(state))
                // Stay a moment for the finish cue and for the workout to be saved.
                delay(FINISH_LINGER_MILLIS)
                stopForeground(STOP_FOREGROUND_DETACH)
                // Detaching leaves the last notification up; swap in one that can be swiped away.
                notify(finishedNotification(state, ongoing = false))
                stopSelf()
            }
            else -> {
                // Counting needs the processor awake; a paused workout does not.
                if (state.isPaused) releaseWakeLock() else holdWakeLock()
                notify(runningNotification(state))
            }
        }
    }

    @SuppressLint("MissingPermission") // Without the permission Android just hides it.
    private fun notify(notification: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun runningNotification(state: TimerUiState): Notification {
        val current = state.currentInterval
        val leadIn = state.phase == TimerPhase.LEAD_IN
        val color = when {
            leadIn -> Amber
            current?.type == IntervalType.REST -> Teal
            else -> Coral
        }.toArgb()
        val title = if (leadIn) "Get ready" else current?.name ?: "Workout"
        val next = state.nextInterval
        val text = when {
            state.isPaused -> "Paused · ${formatDuration(state.remainingSeconds)} left"
            next != null -> "Next: ${next.name} · ${formatDuration(next.durationSeconds)}"
            else -> "Last one"
        }
        val position = if (leadIn) "" else " · ${state.currentIndex + 1}/${state.intervals.size}"

        val builder = baseBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(PhoneWorkout.sessionName.value.orEmpty() + position)
            .setColor(color)
            .setOngoing(true)
            .setStyle(progressStyle(state))
            // Android 16 can show this as a live chip in the status bar and on the lock screen.
            .setRequestPromotedOngoing(true)
            .addAction(0, if (state.isPaused) "Resume" else "Pause", action(ACTION_PAUSE, 1))
            .addAction(0, "Skip", action(ACTION_SKIP, 2))
            .addAction(0, "End", action(ACTION_END, 3))
        if (state.isPaused) {
            builder.setShowWhen(false)
        } else {
            // The system draws the countdown itself, so no update is needed every second.
            builder
                .setWhen(System.currentTimeMillis() + state.remainingMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        return builder.build()
    }

    /** The session as a bar: one coloured piece per interval, filled up to where we are. */
    private fun progressStyle(state: TimerUiState): NotificationCompat.ProgressStyle {
        val style = NotificationCompat.ProgressStyle().setStyledByProgress(true)
        val intervals = state.intervals
        if (intervals.size <= MAX_SEGMENTS) {
            intervals.forEach { interval ->
                val color = if (interval.type == IntervalType.WORK) Coral else Teal
                style.addProgressSegment(
                    NotificationCompat.ProgressStyle.Segment(interval.durationSeconds.coerceAtLeast(1))
                        .setColor(color.toArgb())
                )
            }
        } else {
            // Android merges long lists anyway; one plain bar in the current colour reads better.
            val total = intervals.sumOf { it.durationSeconds }.coerceAtLeast(1)
            val color = if (state.currentInterval?.type == IntervalType.REST) Teal else Coral
            style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(total).setColor(color.toArgb()))
        }
        return style.setProgress((state.elapsedMillis / 1000).toInt())
    }

    private fun finishedNotification(state: TimerUiState, ongoing: Boolean = true): Notification {
        val title = if (state.endedEarly) "Workout ended" else "Workout complete"
        val text = "${formatDuration(state.elapsedSecondsAtEnd)} · " +
            "${state.completedIntervals}/${state.intervals.size} intervals"
        return baseBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(PhoneWorkout.sessionName.value)
            .setColor(Teal.toArgb())
            .setOngoing(ongoing)
            .setAutoCancel(true)
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(openApp())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    /** Tapping the notification brings the app back; it then shows the timer by itself. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, PhoneWorkoutService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun holdWakeLock() {
        val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IntervalTimer:workout")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        // The time limit is a safety net; the lock is let go when the workout ends.
        runCatching { lock.acquire(WAKE_LOCK_LIMIT_MILLIS) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_PAUSE = "com.august.intervaltimer.workout.PAUSE"
        private const val ACTION_SKIP = "com.august.intervaltimer.workout.SKIP"
        private const val ACTION_END = "com.august.intervaltimer.workout.END"
        private const val FINISH_LINGER_MILLIS = 4_000L
        private const val WAKE_LOCK_LIMIT_MILLIS = 4 * 60 * 60 * 1000L
        // Android draws at most this many pieces in a progress notification.
        private const val MAX_SEGMENTS = 10

        /** Call while the app is on screen, right after the workout starts. */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PhoneWorkoutService::class.java))
            }
        }

        /** Takes down the "workout complete" notification once the user has seen the summary. */
        fun clearNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout in progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows the running workout with pause, skip and end"
                setShowBadge(false)
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}
