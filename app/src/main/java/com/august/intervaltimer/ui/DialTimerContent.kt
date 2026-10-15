package com.august.intervaltimer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.design.CountdownFormat
import com.august.intervaltimer.design.currentRemainingFraction
import com.august.intervaltimer.design.formatCountdown
import com.august.intervaltimer.design.ringSegments
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.ui.components.SessionRing
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.readyColor
import com.august.intervaltimer.ui.theme.typeColor

/** Just "WORK" or "REST", so attention stays on the present interval. */
private fun dialLabel(state: TimerUiState): String = when {
    state.isPaused -> "PAUSED"
    state.phase == TimerPhase.LEAD_IN -> "GET READY"
    else -> {
        if (state.currentInterval?.type == IntervalType.WORK) "WORK" else "REST"
    }
}

/** True on the screens that preview what is coming: rest and get-ready. */
private fun showsUpNext(state: TimerUiState): Boolean =
    state.phase == TimerPhase.LEAD_IN || state.currentInterval?.type == IntervalType.REST

@Composable
private fun phaseAccent(state: TimerUiState): Color = when {
    state.phase == TimerPhase.LEAD_IN -> readyColor()
    else -> typeColor(state.currentInterval?.type ?: IntervalType.WORK)
}

/**
 * The running timer in the Dial style: the session ring, the countdown, and
 * only what is needed to finish the current interval.
 */
@Composable
internal fun DialRunningContent(
    state: TimerUiState,
    sessionName: String?,
    format: CountdownFormat,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
) {
    val accent by animateColorAsState(
        targetValue = phaseAccent(state),
        animationSpec = tween(durationMillis = 400),
        label = "accent",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = sessionName.orEmpty().uppercase(),
            style = DialType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            SessionRing(
                segments = state.ringSegments(),
                currentRemaining = state.currentRemainingFraction,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = dialLabel(state), style = DialType.label, color = accent)
                val countdown = formatCountdown(state.remainingSeconds, format)
                Text(
                    text = countdown,
                    style = if (countdown.length > 3) DialType.countdownWide else DialType.countdown,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                if (!showsUpNext(state)) {
                    Text(
                        text = state.currentInterval?.name.orEmpty().uppercase(),
                        style = DialType.exercise,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        // A fixed-height slot, so the ring does not jump between work and rest.
        Box(modifier = Modifier.height(76.dp), contentAlignment = Alignment.Center) {
            if (showsUpNext(state)) {
                UpNext(state.nextInterval)
            } else {
                val next = state.nextInterval
                Text(
                    text = if (next != null) {
                        "NEXT  ${next.name.uppercase()}  ${formatDuration(next.durationSeconds)}"
                    } else {
                        "LAST ONE"
                    },
                    style = DialType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundControl(
                icon = Icons.Filled.Stop,
                description = "End workout",
                size = 52.dp,
                background = MaterialTheme.colorScheme.surfaceVariant,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onEnd,
            )
            RoundControl(
                icon = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                description = if (state.isPaused) "Resume" else "Pause",
                size = 72.dp,
                background = accent,
                tint = MaterialTheme.colorScheme.background,
                onClick = onTogglePause,
            )
            RoundControl(
                icon = Icons.Filled.SkipNext,
                description = "Skip interval",
                size = 52.dp,
                background = MaterialTheme.colorScheme.surfaceVariant,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onSkip,
            )
        }
    }
}

/** "UP NEXT / BURPEES / 0:40": shown while resting and during get-ready. */
@Composable
private fun UpNext(next: Interval?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (next == null) {
            Text(
                text = "LAST ONE",
                style = DialType.exercise,
                color = MaterialTheme.colorScheme.onBackground,
            )
            return
        }
        Text(
            text = "UP NEXT",
            style = DialType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = next.name.uppercase(),
            style = DialType.exercise,
            color = typeColor(next.type),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDuration(next.durationSeconds),
            style = DialType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    description: String,
    size: Dp,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/** The finish screen: how far the ring got, and the totals. */
@Composable
internal fun DialFinishedContent(
    state: TimerUiState,
    sessionName: String?,
    onDone: () -> Unit,
) {
    val completed =
        if (state.endedEarly) state.intervals.take(state.completedIntervals) else state.intervals
    val workIntervals = completed.filter { it.type == IntervalType.WORK }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = sessionName.orEmpty().uppercase(),
            style = DialType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 300.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // Completed intervals are lit; anything left after ending early is grey.
            SessionRing(
                segments = state.ringSegments(),
                currentRemaining = 0f,
                modifier = Modifier.fillMaxSize(),
                showTicks = false,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.endedEarly) "ENDED" else "COMPLETE",
                    style = DialType.label,
                    color = if (state.endedEarly) readyColor() else typeColor(IntervalType.REST),
                )
                Text(
                    text = formatDuration(state.elapsedSecondsAtEnd),
                    style = DialType.countdownWide,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${state.completedIntervals}/${state.intervals.size} INTERVALS",
                    style = DialType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatLine("WORK INTERVALS", "${workIntervals.size}")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatLine("TIME WORKING", formatDuration(workIntervals.sumOf { it.durationSeconds }))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("DONE", style = DialType.button)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DialType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(text = value, style = DialType.figure, color = MaterialTheme.colorScheme.onBackground)
    }
}
