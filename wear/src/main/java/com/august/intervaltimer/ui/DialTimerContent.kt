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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.august.intervaltimer.design.currentRemainingFraction
import com.august.intervaltimer.design.ringSegments
import com.august.intervaltimer.metrics.LiveMetrics
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.ui.components.DialType
import com.august.intervaltimer.ui.components.SessionRing
import com.august.intervaltimer.workout.WorkoutSession
import kotlin.math.roundToInt

/** "WORK 04/15": the phase and where we are in the session. */
private fun dialLabel(state: TimerUiState): String = when {
    state.isPaused -> "PAUSED"
    state.phase == TimerPhase.LEAD_IN -> "GET READY"
    else -> {
        val phase = if (state.currentInterval?.type == IntervalType.WORK) "WORK" else "REST"
        "$phase %02d/%02d".format(state.currentIndex + 1, state.intervals.size)
    }
}

@Composable
internal fun DialRunningContent(state: TimerUiState, live: LiveMetrics) {
    val accent by animateColorAsState(
        targetValue = when {
            state.phase == TimerPhase.LEAD_IN -> ReadyAccent
            state.currentInterval?.type == IntervalType.WORK -> WorkAccent
            else -> RestAccent
        },
        animationSpec = tween(durationMillis = 400),
        label = "accent",
    )

    Box(modifier = Modifier.fillMaxSize()) {
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
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = dialLabel(state), style = DialType.label, color = accent)
            Text(
                text = formatDuration(state.remainingSeconds),
                style = DialType.time,
                color = MaterialTheme.colors.onBackground,
            )
            Text(
                text = when (state.phase) {
                    TimerPhase.LEAD_IN -> "${state.intervals.size} intervals"
                    else -> state.currentInterval?.name.orEmpty()
                },
                style = DialType.name,
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
                Spacer(Modifier.height(3.dp))
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
                        style = DialType.figure,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The dimmed screen: thin gray outlines and the essentials, nothing lit. */
@Composable
internal fun DialAmbientContent(state: TimerUiState) {
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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = dialLabel(state), style = DialType.label, color = Color.Gray)
            Text(
                text = formatDuration(state.remainingSeconds),
                style = DialType.time,
                color = Color.LightGray,
            )
            if (state.phase != TimerPhase.LEAD_IN) {
                Text(
                    text = state.currentInterval?.name.orEmpty(),
                    style = DialType.name,
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
        // What was completed stays faded on the ring; anything skipped by
        // ending early is still lit, so the ring shows how far you got.
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
                .padding(horizontal = 28.dp),
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
                text = "%02d/%02d INTERVALS".format(state.completedIntervals, state.intervals.size),
                style = DialType.caption,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            val avg = live.averageHeartRate
            val max = live.maxHeartRate
            if (avg != null && max != null) {
                Text(
                    text = "HR ${avg.roundToInt()} AVG ${max.roundToInt()} MAX",
                    style = DialType.caption,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            if (live.steps > 0 || live.calories > 0) {
                Text(
                    text = "${live.steps} STEPS ${live.calories.roundToInt()} KCAL",
                    style = DialType.caption,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Chip(
                onClick = onDone,
                label = { Text("Done", style = DialType.name) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}
