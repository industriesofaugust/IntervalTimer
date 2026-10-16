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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.PhoneLiveLink
import com.august.intervaltimer.data.WhenWatchJoins
import com.august.intervaltimer.data.recordPhoneWorkout
import com.august.intervaltimer.haptics.HapticsPlayer
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.sync.LinkState
import com.august.intervaltimer.sync.LiveMetricsUpdate
import com.august.intervaltimer.sync.stepsText
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
    // if sounds and vibration are enabled in Settings.
    val app = LocalContext.current.applicationContext as TimerApp
    val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())
    val soundPlayer = remember(settings.soundEnabled, settings.soundVolume) {
        if (settings.soundEnabled) SoundPlayer(settings.soundVolume) else null
    }
    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer?.release() }
    }
    val haptics = remember { HapticsPlayer(app) }
    LaunchedEffect(viewModel, soundPlayer, settings.vibrate, settings.vibrationLevel, settings.whenWatchJoins) {
        viewModel.cues.collect { cue ->
            // While the watch is running the workout too, the setting decides what the phone adds.
            // On NO WATCH or LOST the watch is not cueing, so the phone does.
            val watchJoined = PhoneLiveLink.state.value == LinkState.LINKED
            val playSound = !watchJoined || settings.whenWatchJoins != WhenWatchJoins.QUIET
            val playVibration = !watchJoined || settings.whenWatchJoins == WhenWatchJoins.BOTH
            if (settings.vibrate && playVibration) haptics.play(cue, settings.vibrationLevel)
            if (!playSound) return@collect
            when (cue) {
                TimerCue.COUNTDOWN_TICK -> soundPlayer?.countdownTick()
                TimerCue.WORK_START -> soundPlayer?.workStart()
                TimerCue.REST_START -> soundPlayer?.restStart()
                TimerCue.FINISH -> soundPlayer?.finish()
            }
        }
    }

    // Save the workout to History once, when it finishes or is ended early.
    // If the watch is linked, it saves it (with heart rate). A watch that has
    // gone silent (LOST) may not, so then the phone saves its own copy, and
    // the watch's replaces it if it turns up later.
    var saved by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        if (state.phase == TimerPhase.FINISHED && !saved && state.elapsedSecondsAtEnd > 0) {
            saved = true
            if (!PhoneLiveLink.watchSavesResult) {
                app.recordPhoneWorkout(sessionName, state, PhoneLiveLink.currentLinkId)
            }
        }
    }

    // The watch owns this workout and sync stopped (here or on the watch):
    // drop the phone's copy without saving it and go back.
    LaunchedEffect(viewModel) {
        PhoneLiveLink.stepOut.collect {
            viewModel.stop()
            onExit()
        }
    }

    if (state.intervals.isEmpty()) return

    KeepScreenOn()
    val metrics by PhoneLiveLink.metrics.collectAsState()
    val linkState by PhoneLiveLink.state.collectAsState()

    // Leaving also ends the workout on the watch, if it is still going.
    val onDone = {
        PhoneLiveLink.leave(viewModel.uiState.value)
        viewModel.stop()
        onExit()
    }
    BackHandler(onBack = onDone)

    // The user's own taps are passed on to the watch. What the watch sends
    // goes straight to the timer, so nothing is sent back.
    val onTogglePause = {
        viewModel.togglePause()
        PhoneLiveLink.sendState(viewModel.uiState.value)
    }
    val onSkip = {
        viewModel.skip()
        PhoneLiveLink.sendState(viewModel.uiState.value)
    }
    val onStopSync = PhoneLiveLink::stopSyncing
    val onEnd = {
        val before = viewModel.uiState.value
        // During get-ready nothing has been done, so there is nothing to summarize.
        if (before.phase == TimerPhase.LEAD_IN) {
            onDone()
        } else {
            viewModel.endEarly()
            PhoneLiveLink.sendEnd(before)
        }
    }
    when {
        isDial() && state.phase == TimerPhase.FINISHED ->
            DialFinishedContent(state = state, sessionName = sessionName, onDone = onDone)
        isDial() ->
            DialRunningContent(
                state = state,
                sessionName = sessionName,
                format = settings.countdownFormat,
                liveMetrics = metrics,
                linkState = linkState,
                onRetryLink = PhoneLiveLink::retry,
                onStopSync = onStopSync,
                onTogglePause = onTogglePause,
                onSkip = onSkip,
                onEnd = onEnd,
            )
        state.phase == TimerPhase.FINISHED -> FinishedContent(state = state, onDone = onDone)
        else -> RunningContent(
            state = state,
            sessionName = sessionName,
            liveMetrics = metrics,
            linkState = linkState,
            onRetryLink = PhoneLiveLink::retry,
            onStopSync = onStopSync,
            onTogglePause = onTogglePause,
            onSkip = onSkip,
            onEnd = onEnd,
        )
    }
}

@Composable
private fun RunningContent(
    state: TimerUiState,
    sessionName: String?,
    liveMetrics: LiveMetricsUpdate?,
    linkState: LinkState,
    onRetryLink: () -> Unit,
    onStopSync: () -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
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
        // A fixed height, so nothing moves when the link tag appears.
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sessionName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LinkTag(linkState, onRetryLink, onStopSync)
        }
        LinearProgressIndicator(
            progress = { state.sessionProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
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
                LiveMetricsRow(liveMetrics, stale = linkState == LinkState.LOST, modifier = Modifier.width(290.dp))
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

        // A fixed-height slot, so the timer does not move when the watch's numbers arrive.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            liveMetrics?.stepsText()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(if (linkState == LinkState.LOST) STALE_ALPHA else 1f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onEnd,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("End")
            }
            Button(
                onClick = onTogglePause,
                modifier = Modifier
                    .weight(2f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (state.isPaused) "Resume" else "Pause")
            }
            OutlinedButton(
                onClick = onSkip,
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
