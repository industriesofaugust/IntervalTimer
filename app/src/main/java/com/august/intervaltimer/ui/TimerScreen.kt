package com.august.intervaltimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.august.intervaltimer.ui.theme.isAppInDarkTheme
import com.august.intervaltimer.ui.theme.isDial
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.recordPhoneWorkout
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.timer.SoundPlayer
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.timer.TimerViewModel
import com.august.intervaltimer.ui.theme.Amber
import com.august.intervaltimer.ui.theme.AmberLight
import com.august.intervaltimer.ui.theme.Coral
import com.august.intervaltimer.ui.theme.CoralLight
import com.august.intervaltimer.ui.theme.Teal
import com.august.intervaltimer.ui.theme.TealLight

@Composable
fun TimerScreen(viewModel: TimerViewModel, sessionName: String?, onExit: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    // The engine announces cue moments; on the phone we answer with beeps,
    // if sounds are enabled in Settings.
    val app = LocalContext.current.applicationContext as TimerApp
    val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())
    val soundPlayer = remember(settings.soundEnabled, settings.soundVolume) {
        if (settings.soundEnabled) SoundPlayer(settings.soundVolume) else null
    }
    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer?.release() }
    }
    LaunchedEffect(viewModel, soundPlayer) {
        viewModel.cues.collect { cue ->
            when (cue) {
                TimerCue.COUNTDOWN_TICK -> soundPlayer?.countdownTick()
                TimerCue.WORK_START -> soundPlayer?.workStart()
                TimerCue.REST_START -> soundPlayer?.restStart()
                TimerCue.FINISH -> soundPlayer?.finish()
            }
        }
    }

    // Save the workout to History once, when it finishes or is ended early.
    var saved by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        if (state.phase == TimerPhase.FINISHED && !saved && state.elapsedSecondsAtEnd > 0) {
            saved = true
            app.recordPhoneWorkout(sessionName, state)
        }
    }

    if (state.intervals.isEmpty()) return

    KeepScreenOn()
    BackHandler {
        viewModel.stop()
        onExit()
    }

    val onDone = {
        viewModel.stop()
        onExit()
    }
    when {
        isDial() && state.phase == TimerPhase.FINISHED ->
            DialFinishedContent(state = state, sessionName = sessionName, onDone = onDone)
        isDial() ->
            DialRunningContent(
                state = state,
                sessionName = sessionName,
                format = settings.countdownFormat,
                onTogglePause = viewModel::togglePause,
                onSkip = viewModel::skip,
                onEnd = {
                    // During get-ready nothing has been done, so there is
                    // nothing to summarize.
                    if (state.phase == TimerPhase.LEAD_IN) onDone() else viewModel.endEarly()
                },
            )
        state.phase == TimerPhase.FINISHED -> FinishedContent(state = state, onDone = onDone)
        else -> RunningContent(state = state, viewModel = viewModel, onExit = onExit)
    }
}

@Composable
private fun RunningContent(
    state: TimerUiState,
    viewModel: TimerViewModel,
    onExit: () -> Unit,
) {
    val accent by animateColorAsState(
        targetValue = stateAccent(state),
        animationSpec = tween(durationMillis = 400),
        label = "accent",
    )
    val ringProgress =
        (state.remainingMillis.toFloat() / state.currentDurationMillis).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        LinearProgressIndicator(
            progress = { state.sessionProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (state.phase) {
                TimerPhase.LEAD_IN -> "${state.intervals.size} intervals ahead"
                else -> "Interval ${state.currentIndex + 1} of ${state.intervals.size}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PhaseChip(text = phaseLabel(state), accent = accent)
                Box(
                    modifier = Modifier.size(290.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ProgressRing(
                        progress = ringProgress,
                        color = accent,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = displayTime(state.remainingSeconds),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = when (state.phase) {
                                TimerPhase.LEAD_IN -> "Get ready"
                                else -> state.currentInterval?.name.orEmpty()
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NextUpText(state = state, accent = accent)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (state.phase == TimerPhase.LEAD_IN) {
                        // Nothing has been done yet, so there is nothing to summarize.
                        viewModel.stop()
                        onExit()
                    } else {
                        viewModel.endEarly()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("End")
            }
            Button(
                onClick = viewModel::togglePause,
                modifier = Modifier
                    .weight(2f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (state.isPaused) "Resume" else "Pause")
            }
            OutlinedButton(
                onClick = viewModel::skip,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Skip")
            }
        }
    }
}

@Composable
private fun FinishedContent(state: TimerUiState, onDone: () -> Unit) {
    // If the session was ended early, only count what was actually done.
    val completed =
        if (state.endedEarly) state.intervals.take(state.completedIntervals) else state.intervals
    val workIntervals = completed.filter { it.type == IntervalType.WORK }
    val totalSeconds = state.elapsedSecondsAtEnd
    val workSeconds = workIntervals.sumOf { it.durationSeconds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = if (state.endedEarly) "Session ended" else "Nice work",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (state.endedEarly) {
                "${state.completedIntervals} of ${state.intervals.size} intervals completed"
            } else {
                "Session complete"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatRow(label = "Total time", value = formatDuration(totalSeconds))
                StatRow(label = "Work intervals", value = "${workIntervals.size}")
                StatRow(label = "Time working", value = formatDuration(workSeconds))
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun NextUpText(state: TimerUiState, accent: Color) {
    val next = state.nextInterval
    when {
        next != null -> Text(
            text = "Next: ${next.name} · ${formatDuration(next.durationSeconds)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.phase == TimerPhase.RUNNING -> Text(
            text = "Last one — finish strong",
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
        )
    }
}

@Composable
private fun PhaseChip(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ProgressRing(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val strokeWidth = 14.dp.toPx()
        val inset = strokeWidth / 2
        val topLeft = Offset(inset, inset)
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

/** Under a minute, a single big number is faster to read mid-exercise. */
private fun displayTime(seconds: Int): String =
    if (seconds < 60) "$seconds" else formatDuration(seconds)

@Composable
private fun phaseLabel(state: TimerUiState): String = when {
    state.isPaused -> "Paused"
    state.phase == TimerPhase.LEAD_IN -> "Get ready"
    state.currentInterval?.type == IntervalType.WORK -> "Work"
    else -> "Rest"
}

@Composable
private fun stateAccent(state: TimerUiState): Color {
    val dark = isAppInDarkTheme()
    return when {
        state.phase == TimerPhase.LEAD_IN -> if (dark) AmberLight else Amber
        state.currentInterval?.type == IntervalType.WORK -> if (dark) CoralLight else Coral
        else -> if (dark) TealLight else Teal
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
