package com.august.intervaltimer.ui

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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.august.intervaltimer.metrics.LiveMetrics
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.settings.WatchSettings
import com.august.intervaltimer.settings.WatchSettingsRepository
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.workout.WorkoutSession
import kotlin.math.roundToInt

@Composable
fun WearTimerScreen(
    isAmbient: Boolean,
    onDone: () -> Unit,
) {
    // The workout itself runs in WorkoutSession and WorkoutService, which
    // also play the cues. This screen only shows it and passes on taps.
    val state by WorkoutSession.state.collectAsState()
    val live by WorkoutSession.live.collectAsState()

    val context = LocalContext.current
    val settingsRepository = remember { WatchSettingsRepository(context.applicationContext) }
    val settings by settingsRepository.settings.collectAsState(initial = WatchSettings())

    if (settings.keepScreenOn && state.phase != TimerPhase.FINISHED) {
        KeepScreenOn()
    }

    if (state.intervals.isEmpty()) return

    when {
        state.phase == TimerPhase.FINISHED ->
            FinishedContent(state = state, live = live, onDone = onDone)
        isAmbient -> AmbientContent(state = state)
        else -> RunningContent(state = state, live = live)
    }
}

/**
 * The dimmed, low-power version: mostly black, no color fills, no buttons.
 * It shows just enough to check progress with a glance at the wrist.
 */
@Composable
private fun AmbientContent(state: TimerUiState) {
    val ringProgress =
        (state.remainingMillis.toFloat() / state.currentDurationMillis).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            progress = ringProgress,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            indicatorColor = Color.Gray,
            trackColor = Color.DarkGray.copy(alpha = 0.4f),
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
                text = phaseLabel(state).uppercase(),
                style = MaterialTheme.typography.caption2.copy(letterSpacing = 1.5.sp),
                color = Color.Gray,
            )
            Text(
                text = displayTime(state.remainingSeconds),
                style = TextStyle(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Light,
                    fontFeatureSettings = "tnum",
                ),
                color = Color.LightGray,
            )
            Text(
                text = when (state.phase) {
                    TimerPhase.LEAD_IN -> "${state.intervals.size} intervals"
                    else -> "${state.currentIndex + 1} of ${state.intervals.size}"
                },
                style = MaterialTheme.typography.caption1,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun RunningContent(
    state: TimerUiState,
    live: LiveMetrics,
) {
    val accent by animateColorAsState(
        targetValue = when {
            state.phase == TimerPhase.LEAD_IN -> ReadyAccent
            state.currentInterval?.type == IntervalType.WORK -> WorkAccent
            else -> RestAccent
        },
        animationSpec = tween(durationMillis = 400),
        label = "accent",
    )
    val ringProgress =
        (state.remainingMillis.toFloat() / state.currentDurationMillis).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        // Outer ring: time left in this interval.
        CircularProgressIndicator(
            progress = ringProgress,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            indicatorColor = accent,
            trackColor = accent.copy(alpha = 0.15f),
            strokeWidth = 5.dp,
        )
        // Inner thin ring: how far through the whole session.
        CircularProgressIndicator(
            progress = state.sessionProgress,
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
            indicatorColor = MaterialTheme.colors.onSurfaceVariant,
            trackColor = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.12f),
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
                text = when (state.phase) {
                    TimerPhase.LEAD_IN -> phaseLabel(state).uppercase()
                    else -> "${phaseLabel(state).uppercase()} · " +
                        "${state.currentIndex + 1}/${state.intervals.size}"
                },
                style = MaterialTheme.typography.caption2.copy(letterSpacing = 1.sp),
                color = accent,
            )
            Text(
                text = displayTime(state.remainingSeconds),
                style = TextStyle(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colors.onBackground,
            )
            Text(
                text = when (state.phase) {
                    TimerPhase.LEAD_IN -> "${state.intervals.size} intervals"
                    else -> state.currentInterval?.name.orEmpty()
                },
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val next = state.nextInterval
            Text(
                text = if (next != null) {
                    "Next: ${next.name} ${formatDuration(next.durationSeconds)}"
                } else {
                    "Last one"
                },
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimerButton(
                    icon = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    description = if (state.isPaused) "Resume" else "Pause",
                    onClick = WorkoutSession::togglePause,
                )
                TimerButton(
                    icon = Icons.Filled.SkipNext,
                    description = "Skip interval",
                    onClick = WorkoutSession::skip,
                )
                TimerButton(
                    icon = Icons.Filled.Stop,
                    description = "End workout",
                    onClick = WorkoutSession::endEarly,
                )
            }
            val heartRate = live.currentHeartRate
            if (heartRate != null) {
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
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Sized so three fit side by side inside the round screen. */
@Composable
private fun TimerButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FinishedContent(
    state: TimerUiState,
    live: LiveMetrics,
    onDone: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.endedEarly) "Session ended" else "Nice work",
            style = MaterialTheme.typography.title2,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (state.endedEarly) {
                "${state.completedIntervals} of ${state.intervals.size} intervals · " +
                    formatDuration(state.elapsedSecondsAtEnd)
            } else {
                val workCount = state.intervals.count { it.type == IntervalType.WORK }
                "$workCount work · ${formatDuration(state.elapsedSecondsAtEnd)} total"
            },
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
        val avg = live.averageHeartRate
        val max = live.maxHeartRate
        if (avg != null && max != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "♥ ${avg.roundToInt()} avg · ${max.roundToInt()} max",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        if (live.steps > 0 || live.calories > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${live.steps} steps · ${live.calories.roundToInt()} kcal",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Chip(
            onClick = onDone,
            label = { Text("Done") },
            colors = ChipDefaults.primaryChipColors(),
        )
    }
}

/** Under a minute, a single big number is faster to read mid-exercise. */
private fun displayTime(seconds: Int): String =
    if (seconds < 60) "$seconds" else formatDuration(seconds)

private fun phaseLabel(state: TimerUiState): String = when {
    state.isPaused -> "Paused"
    state.phase == TimerPhase.LEAD_IN -> "Get ready"
    state.currentInterval?.type == IntervalType.WORK -> "Work"
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
