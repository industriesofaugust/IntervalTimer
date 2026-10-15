package com.august.intervaltimer.ui

import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.august.intervaltimer.design.AppStyle
import com.august.intervaltimer.metrics.LiveMetrics
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.settings.rememberWatchSettings
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.ui.components.ButtonIcon
import com.august.intervaltimer.ui.components.HoldToEndButton
import com.august.intervaltimer.ui.components.ProgressRing
import com.august.intervaltimer.workout.WorkoutSession
import kotlin.math.roundToInt

/**
 * The timer screen: picks the face (Dial or Minimal, running, dimmed, or
 * finished). It draws no clock at the top, unlike the menu screens.
 */
@Composable
fun WearTimerScreen(
    isAmbient: Boolean,
    onDone: () -> Unit,
) {
    // The workout itself runs in WorkoutSession and WorkoutService, which
    // also play the cues. This screen only shows it and passes on taps.
    //
    // It follows the session but ignores the empty state that Done leaves
    // behind, so the finished summary stays up while the screen slides away
    // instead of flashing blank.
    val shown = remember { mutableStateOf(WorkoutSession.state.value) }
    LaunchedEffect(Unit) {
        WorkoutSession.state.collect { if (it.intervals.isNotEmpty()) shown.value = it }
    }
    val view = remember { TimerView(shown) }
    val live = WorkoutSession.live.collectAsState()
    val settings = rememberWatchSettings().value

    if (settings.keepScreenOn && view.phase != TimerPhase.FINISHED) {
        KeepScreenOn()
    }

    if (view.intervals.isEmpty()) return

    val dial = settings.style == AppStyle.DIAL
    when {
        view.phase == TimerPhase.FINISHED ->
            if (dial) DialFinishedContent(view = view, live = live, onDone = onDone)
            else FinishedContent(view = view, live = live, onDone = onDone)
        isAmbient ->
            if (dial) DialAmbientContent(view = view, format = settings.countdownFormat)
            else AmbientContent(view = view)
        else ->
            if (dial) DialRunningContent(view = view, live = live, format = settings.countdownFormat)
            else RunningContent(view = view, live = live)
    }
}

/**
 * The dimmed, low-power version: mostly black, no color fills, no buttons.
 * It shows just enough to check progress with a glance at the wrist.
 */
@Composable
private fun AmbientContent(view: TimerView) {
    Box(modifier = Modifier.fillMaxSize()) {
        ProgressRing(
            progress = { view.remainingFraction() },
            color = Color.Gray,
            trackColor = Color.DarkGray.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            strokeWidth = 2.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = phaseLabel(view).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = Color.Gray,
            )
            RemainingTime(view, fontSize = 46.sp, color = Color.LightGray)
            Text(
                text = if (view.phase == TimerPhase.LEAD_IN) {
                    "${view.intervals.size} intervals"
                } else {
                    "${view.currentIndex + 1} of ${view.intervals.size}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun RunningContent(
    view: TimerView,
    live: State<LiveMetrics>,
) {
    val accent by animateColorAsState(
        targetValue = when {
            view.phase == TimerPhase.LEAD_IN -> ReadyAccent
            view.currentInterval?.type == IntervalType.WORK -> WorkAccent
            else -> RestAccent
        },
        animationSpec = tween(durationMillis = 400),
        label = "accent",
    )
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxSize()) {
        // Outer ring: time left in this interval.
        ProgressRing(
            progress = { view.remainingFraction() },
            color = accent,
            trackColor = accent.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            strokeWidth = 5.dp,
        )
        // Inner thin ring: how far through the whole session.
        ProgressRing(
            progress = { view.sessionFraction() },
            color = quiet,
            trackColor = quiet.copy(alpha = 0.12f),
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
            strokeWidth = 2.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (view.phase == TimerPhase.LEAD_IN) {
                    phaseLabel(view).uppercase()
                } else {
                    "${phaseLabel(view).uppercase()} · ${view.currentIndex + 1}/${view.intervals.size}"
                },
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = accent,
            )
            RemainingTime(view, fontSize = 42.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = if (view.phase == TimerPhase.LEAD_IN) {
                    "${view.intervals.size} intervals"
                } else {
                    view.currentInterval?.name.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val next = view.nextInterval
            Text(
                text = if (next != null) {
                    "Next: ${next.name} ${formatDuration(next.durationSeconds)}"
                } else {
                    "Last one"
                },
                style = MaterialTheme.typography.bodyExtraSmall,
                color = quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val size = IconButtonDefaults.SmallButtonSize
                HoldToEndButton(onEnd = WorkoutSession::endEarly, size = size)
                FilledIconButton(
                    onClick = WorkoutSession::togglePause,
                    modifier = Modifier.size(size),
                ) {
                    ButtonIcon(
                        icon = if (view.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        description = if (view.isPaused) "Resume" else "Pause",
                        buttonSize = size,
                    )
                }
                FilledTonalIconButton(
                    onClick = WorkoutSession::skip,
                    modifier = Modifier.size(size),
                ) {
                    ButtonIcon(Icons.Filled.SkipNext, "Skip interval", size)
                }
            }
            MinimalHeartRate(live)
        }
    }
}

/** Under a minute, a single big number is faster to read mid-exercise. */
@Composable
private fun RemainingTime(view: TimerView, fontSize: TextUnit, color: Color) {
    val seconds = view.remainingSeconds
    Text(
        text = if (seconds < 60) "$seconds" else formatDuration(seconds),
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.Light,
            fontFeatureSettings = "tnum",
        ),
        color = color,
    )
}

@Composable
private fun MinimalHeartRate(live: State<LiveMetrics>) {
    val heartRate = live.value.currentHeartRate ?: return
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = WorkAccent,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${heartRate.roundToInt()} bpm",
            style = MaterialTheme.typography.bodyExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FinishedContent(
    view: TimerView,
    live: State<LiveMetrics>,
    onDone: () -> Unit,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val elapsed = formatDuration(view.elapsedSecondsAtEnd)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (view.endedEarly) "Session ended" else "Nice work",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (view.endedEarly) {
                "${view.completedIntervals} of ${view.intervals.size} intervals · $elapsed"
            } else {
                val workCount = view.intervals.count { it.type == IntervalType.WORK }
                "$workCount work · $elapsed total"
            },
            style = MaterialTheme.typography.bodySmall,
            color = quiet,
        )
        val metrics = live.value
        val avg = metrics.averageHeartRate
        val max = metrics.maxHeartRate
        if (avg != null && max != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "♥ ${avg.roundToInt()} avg · ${max.roundToInt()} max",
                style = MaterialTheme.typography.bodySmall,
                color = quiet,
            )
        }
        if (metrics.steps > 0 || metrics.calories > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${metrics.steps} steps · ${metrics.calories.roundToInt()} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = quiet,
            )
        }
        Spacer(Modifier.height(10.dp))
        CompactButton(onClick = onDone, modifier = Modifier.width(88.dp), colors = ButtonDefaults.buttonColors()) {
            Text("DONE", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

private fun phaseLabel(view: TimerView): String = when {
    view.isPaused -> "Paused"
    view.phase == TimerPhase.LEAD_IN -> "Get ready"
    view.currentInterval?.type == IntervalType.WORK -> "Work"
    else -> "Rest"
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
