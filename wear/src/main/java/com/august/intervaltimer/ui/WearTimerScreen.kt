package com.august.intervaltimer.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.august.intervaltimer.haptics.HapticsPlayer
import com.august.intervaltimer.metrics.MetricsViewModel
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.settings.WatchSettings
import com.august.intervaltimer.settings.WatchSettingsRepository
import com.august.intervaltimer.sync.soundVolumePercent
import com.august.intervaltimer.timer.SoundPlayer
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.timer.TimerViewModel
import kotlin.math.roundToInt

@Composable
fun WearTimerScreen(
    viewModel: TimerViewModel,
    metricsViewModel: MetricsViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val settingsRepository = remember { WatchSettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = WatchSettings())

    // The engine announces cue moments; the watch answers with vibration
    // and beeps, according to the settings.
    val haptics = remember { HapticsPlayer(context) }
    val soundPlayer = remember(settings.cues.soundEnabled, settings.cues.soundLevel) {
        if (settings.cues.soundEnabled) SoundPlayer(settings.cues.soundVolumePercent) else null
    }
    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer?.release() }
    }
    LaunchedEffect(viewModel, settings) {
        viewModel.cues.collect { cue ->
            if (settings.cues.vibrationEnabled) {
                haptics.play(cue, settings.cues.vibrationLevel)
            }
            when (cue) {
                TimerCue.COUNTDOWN_TICK -> soundPlayer?.countdownTick()
                TimerCue.WORK_START -> soundPlayer?.workStart()
                TimerCue.REST_START -> soundPlayer?.restStart()
                TimerCue.FINISH -> soundPlayer?.finish()
            }
        }
    }
    if (settings.keepScreenOn) {
        KeepScreenOn()
    }

    // Health data needs the user's permission. If they say no, the timer
    // still works — the workout just has no heart rate or steps.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        metricsViewModel.startRecording()
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
            )
        )
    }
    // Start recording with the lead-in; stop and send to phone when finished.
    LaunchedEffect(state.phase) {
        when (state.phase) {
            TimerPhase.LEAD_IN, TimerPhase.RUNNING -> metricsViewModel.startRecording()
            TimerPhase.FINISHED -> metricsViewModel.finishAndPublish(state)
        }
    }
    LaunchedEffect(state.isPaused) {
        if (state.phase != TimerPhase.FINISHED) metricsViewModel.setPaused(state.isPaused)
    }

    if (state.intervals.isEmpty()) return

    if (state.phase == TimerPhase.FINISHED) {
        FinishedContent(state = state, metricsViewModel = metricsViewModel, onDone = onDone)
    } else {
        RunningContent(state = state, viewModel = viewModel, metricsViewModel = metricsViewModel)
    }
}

@Composable
private fun RunningContent(
    state: TimerUiState,
    viewModel: TimerViewModel,
    metricsViewModel: MetricsViewModel,
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
    val live by metricsViewModel.live.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            progress = ringProgress,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            indicatorColor = accent,
            trackColor = accent.copy(alpha = 0.15f),
            strokeWidth = 5.dp,
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
                color = accent,
            )
            Text(
                text = displayTime(state.remainingSeconds),
                style = TextStyle(
                    fontSize = 46.sp,
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
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::togglePause,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                ) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (state.isPaused) "Resume" else "Pause",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Button(
                    onClick = viewModel::skip,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Skip interval",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Button(
                    onClick = viewModel::endEarly,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "End workout",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            val heartRate = live.currentHeartRate
            if (heartRate != null) {
                Spacer(Modifier.height(6.dp))
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

@Composable
private fun FinishedContent(
    state: TimerUiState,
    metricsViewModel: MetricsViewModel,
    onDone: () -> Unit,
) {
    val live by metricsViewModel.live.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.endedEarly) "Session ended" else "Nice work",
            style = MaterialTheme.typography.title1,
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
