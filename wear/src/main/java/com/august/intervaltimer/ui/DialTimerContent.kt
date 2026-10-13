package com.august.intervaltimer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.august.intervaltimer.design.CountdownFormat
import com.august.intervaltimer.design.currentRemainingFraction
import com.august.intervaltimer.design.formatCountdown
import com.august.intervaltimer.design.intervalCountLabel
import com.august.intervaltimer.design.ringSegments
import com.august.intervaltimer.metrics.LiveMetrics
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.ui.components.DialType
import com.august.intervaltimer.ui.components.SessionRing
import com.august.intervaltimer.workout.WorkoutSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun accentFor(type: IntervalType?): Color = when (type) {
    IntervalType.WORK -> WorkAccent
    IntervalType.REST -> RestAccent
    null -> ReadyAccent
}

/** "WORK 3/5": the phase and where we are in the session. */
private fun dialLabel(state: TimerUiState): String = when {
    state.isPaused -> "PAUSED"
    state.phase == TimerPhase.LEAD_IN -> "GET READY"
    else -> {
        val phase = if (state.currentInterval?.type == IntervalType.WORK) "WORK" else "REST"
        "$phase ${state.currentIndex + 1}/${state.intervals.size}"
    }
}

/** True on the screens that preview what is coming: rest and get-ready. */
private fun showsUpNext(state: TimerUiState): Boolean =
    state.phase == TimerPhase.LEAD_IN || state.currentInterval?.type == IntervalType.REST

@Composable
private fun Countdown(state: TimerUiState, format: CountdownFormat, color: Color) {
    val text = formatCountdown(state.remainingSeconds, format)
    Text(
        text = text,
        style = if (text.length > 3) DialType.countdownWide else DialType.countdown,
        color = color,
        maxLines = 1,
    )
}

/**
 * The running timer. The screen shows only information; tapping anywhere
 * brings up pause, skip, and end for a few seconds.
 */
@Composable
internal fun DialRunningContent(state: TimerUiState, live: LiveMetrics, format: CountdownFormat) {
    val accent by animateColorAsState(
        targetValue = if (state.phase == TimerPhase.LEAD_IN) ReadyAccent else accentFor(state.currentInterval?.type),
        animationSpec = tween(durationMillis = 400),
        label = "accent",
    )

    var controlsVisible by remember { mutableStateOf(false) }
    // Controls stay up while paused, otherwise they tuck away on their own.
    LaunchedEffect(controlsVisible, state.isPaused) {
        if (controlsVisible && !state.isPaused) {
            delay(4_000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        SessionRing(
            segments = state.ringSegments(),
            currentRemaining = state.currentRemainingFraction,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = dialLabel(state), style = DialType.label, color = accent)
            Countdown(state, format, MaterialTheme.colors.onBackground)
            if (showsUpNext(state)) {
                UpNext(state.nextInterval)
            } else {
                Text(
                    text = state.currentInterval?.name.orEmpty().uppercase(),
                    style = DialType.exercise,
                    color = MaterialTheme.colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val next = state.nextInterval
                Text(
                    text = if (next != null) {
                        "NEXT ${next.name.uppercase()} ${formatDuration(next.durationSeconds)}"
                    } else {
                        "LAST ONE"
                    },
                    style = DialType.caption,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${heartRate.roundToInt()}",
                        style = DialType.caption,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }

        if (controlsVisible || state.isPaused) {
            ControlsOverlay(
                isPaused = state.isPaused,
                onTogglePause = WorkoutSession::togglePause,
                onSkip = {
                    WorkoutSession.skip()
                    controlsVisible = false
                },
                onEnd = WorkoutSession::endEarly,
            )
        }
    }
}

/** "UP NEXT / BURPEES / 0:40": shown while resting and during get-ready. */
@Composable
private fun UpNext(next: Interval?) {
    if (next == null) {
        Text(text = "LAST ONE", style = DialType.exercise, color = MaterialTheme.colors.onBackground)
        return
    }
    Text(
        text = "UP NEXT",
        style = DialType.caption.copy(letterSpacing = DialType.label.letterSpacing),
        color = MaterialTheme.colors.onSurfaceVariant,
    )
    Text(
        text = next.name.uppercase(),
        style = DialType.exercise,
        color = accentFor(next.type),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = formatDuration(next.durationSeconds),
        style = DialType.caption,
        color = MaterialTheme.colors.onSurfaceVariant,
    )
}

@Composable
private fun ControlsOverlay(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldToEndButton(onEnd = onEnd)
            ControlButton(
                icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                description = if (isPaused) "Resume" else "Pause",
                large = true,
                onClick = onTogglePause,
            )
            ControlButton(
                icon = Icons.Filled.SkipNext,
                description = "Skip interval",
                large = false,
                onClick = onSkip,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (isPaused) "PAUSED · HOLD ■ TO END" else "HOLD ■ TO END",
            style = DialType.caption,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    large: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (large) 60.dp else 48.dp)
            .clip(CircleShape)
            .background(if (large) WorkAccent else MaterialTheme.colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (large) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
            modifier = Modifier.size(if (large) 28.dp else 22.dp),
        )
    }
}

/**
 * Ends the workout only after being held for a second, so a stray touch
 * mid-exercise can't throw the session away. A ring fills while held.
 */
@Composable
private fun HoldToEndButton(onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colors.surface)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val hold = scope.launch {
                            progress.animateTo(1f, tween(HOLD_MILLIS, easing = LinearEasing))
                            onEnd()
                        }
                        tryAwaitRelease()
                        hold.cancel()
                        progress.snapTo(0f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = progress.value,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = ReadyAccent,
            trackColor = Color.Transparent,
            strokeWidth = 3.dp,
        )
        Icon(
            imageVector = Icons.Filled.Stop,
            contentDescription = "Hold to end workout",
            tint = MaterialTheme.colors.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val HOLD_MILLIS = 1_000

/** The dimmed screen: thin outlines and the essentials, nothing lit. */
@Composable
internal fun DialAmbientContent(state: TimerUiState, format: CountdownFormat) {
    Box(modifier = Modifier.fillMaxSize()) {
        SessionRing(
            segments = state.ringSegments(),
            currentRemaining = state.currentRemainingFraction,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            outline = true,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = dialLabel(state), style = DialType.label, color = Color.Gray)
            Countdown(state, format, Color.LightGray)
            if (state.phase != TimerPhase.LEAD_IN) {
                Text(
                    text = state.currentInterval?.name.orEmpty().uppercase(),
                    style = DialType.exercise,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun DialFinishedContent(state: TimerUiState, live: LiveMetrics, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Completed intervals are lit; anything left after ending early is grey.
        SessionRing(
            segments = state.ringSegments(),
            currentRemaining = 0f,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            showTicks = false,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (state.endedEarly) "ENDED" else "COMPLETE",
                style = DialType.label,
                color = if (state.endedEarly) ReadyAccent else RestAccent,
            )
            Text(
                text = formatDuration(state.elapsedSecondsAtEnd),
                style = DialType.time,
                color = MaterialTheme.colors.onBackground,
            )
            Text(
                text = "${state.completedIntervals}/${state.intervals.size} INTERVALS",
                style = DialType.caption,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            val avg = live.averageHeartRate
            val max = live.maxHeartRate
            if (avg != null && max != null) {
                Text(
                    text = "♥ ${avg.roundToInt()} AVG · ${max.roundToInt()} MAX",
                    style = DialType.caption,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            if (live.steps > 0 || live.calories > 0) {
                Text(
                    text = "${live.steps} STEPS · ${live.calories.roundToInt()} KCAL",
                    style = DialType.caption,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Chip(
                onClick = onDone,
                label = { Text("DONE", style = DialType.button) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}

/** The centre of the preview screen: the session as a map, with Start. */
@Composable
internal fun DialPreviewHeader(name: String, intervals: List<Interval>, onStart: () -> Unit) {
    Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        SessionRing(
            // Every interval lit, so the ring reads as a map of the workout.
            segments = ringSegments(intervals, doneCount = intervals.size),
            currentRemaining = 0f,
            modifier = Modifier.fillMaxSize(),
            showTicks = false,
        )
        Column(
            modifier = Modifier.padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                style = DialType.name,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = intervalCountLabel(intervals.size),
                style = DialType.caption,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            Text(
                text = formatDuration(intervals.sumOf { it.durationSeconds }),
                style = DialType.time,
                color = MaterialTheme.colors.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Chip(
                onClick = onStart,
                label = { Text("START", style = DialType.button) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.height(36.dp),
            )
        }
    }
}
