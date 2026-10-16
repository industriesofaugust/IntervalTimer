package com.august.intervaltimer.ui

import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.august.intervaltimer.design.CountdownFormat
import com.august.intervaltimer.design.formatCountdown
import com.august.intervaltimer.metrics.LiveMetrics
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.sync.LinkState
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.ui.components.ButtonIcon
import com.august.intervaltimer.ui.components.DialType
import com.august.intervaltimer.ui.components.HoldToEndButton
import com.august.intervaltimer.ui.components.PhoneGlyph
import com.august.intervaltimer.ui.components.PhoneLinkChip
import com.august.intervaltimer.ui.components.SessionRing
import com.august.intervaltimer.workout.WorkoutSession
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun accentFor(type: IntervalType?): Color = when (type) {
    IntervalType.WORK -> WorkAccent
    IntervalType.REST -> RestAccent
    null -> ReadyAccent
}

/** "WORK 3/5": the phase and where we are in the session. */
private fun dialLabel(view: TimerView): String = when {
    view.isPaused -> "PAUSED"
    view.phase == TimerPhase.LEAD_IN -> "GET READY"
    else -> {
        if (view.currentInterval?.type == IntervalType.WORK) "WORK" else "REST"
    }
}

/** True on the screens that preview what is coming: rest and get-ready. */
private fun showsUpNext(view: TimerView): Boolean =
    view.phase == TimerPhase.LEAD_IN || view.currentInterval?.type == IntervalType.REST

/**
 * The big number. It is its own function so that only this recomposes when
 * the second changes; the rest of the face waits for the interval to change.
 */
@Composable
private fun Countdown(view: TimerView, format: CountdownFormat, color: Color) {
    val text = formatCountdown(view.remainingSeconds, format)
    Text(
        text = text,
        style = if (text.length > 3) DialType.countdownWide else DialType.countdown,
        color = color,
        maxLines = 1,
    )
}

/** The heart rate line, when there is one. Changes in the reading only touch this. */
@Composable
private fun HeartRateLine(live: State<LiveMetrics>) {
    val heartRate = live.value.currentHeartRate ?: return
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The running timer. The screen shows only information; tapping anywhere
 * brings up pause, skip, and end for a few seconds.
 */
@Composable
internal fun DialRunningContent(view: TimerView, live: State<LiveMetrics>, format: CountdownFormat) {
    val accent by animateColorAsState(
        targetValue = if (view.phase == TimerPhase.LEAD_IN) ReadyAccent else accentFor(view.currentInterval?.type),
        animationSpec = tween(durationMillis = 400),
        label = "accent",
    )

    val linkState by WorkoutSession.linkState.collectAsState()

    var controlsVisible by remember { mutableStateOf(false) }
    // Each tap on the link chip counts, so STOP SYNC? is not hidden while it waits.
    var linkChipTaps by remember { mutableIntStateOf(0) }
    // Controls stay up while paused, otherwise they tuck away on their own.
    LaunchedEffect(controlsVisible, view.isPaused, linkChipTaps) {
        if (controlsVisible && !view.isPaused) {
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
            segments = view.rememberRingSegments(),
            currentRemaining = { view.remainingFraction() },
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
            Text(text = dialLabel(view), style = DialType.label, color = accent)
            Countdown(view, format, MaterialTheme.colorScheme.onBackground)
            val next = view.nextInterval
            if (showsUpNext(view)) {
                UpNext(next)
            } else {
                Text(
                    text = view.currentInterval?.name.orEmpty().uppercase(),
                    style = DialType.exercise,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (next != null) {
                        "NEXT ${next.name.uppercase()} ${formatDuration(next.durationSeconds)}"
                    } else {
                        "LAST ONE"
                    },
                    style = DialType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HeartRateLine(live)
        }
        // Over the bottom of the dial so the centred text doesn't move.
        PhoneGlyph(
            state = linkState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp),
        )

        AnimatedVisibility(
            visible = controlsVisible || view.isPaused,
            enter = fadeIn(tween(CONTROLS_FADE_MILLIS)),
            exit = fadeOut(tween(CONTROLS_FADE_MILLIS)),
        ) {
            ControlsOverlay(
                isPaused = view.isPaused,
                linkState = linkState,
                onLinkChipTap = { linkChipTaps++ },
                onSkip = {
                    WorkoutSession.skip()
                    controlsVisible = false
                },
            )
        }
    }
}

private const val CONTROLS_FADE_MILLIS = 150

/** "UP NEXT / BURPEES / 0:40": shown while resting and during get-ready. */
@Composable
private fun UpNext(next: Interval?) {
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
        style = DialType.caption.copy(letterSpacing = DialType.label.letterSpacing),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Pause, skip and end over a dark scrim, with the phone link chip above them. */
@Composable
private fun ControlsOverlay(
    isPaused: Boolean,
    linkState: LinkState,
    onLinkChipTap: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PhoneLinkChip(linkState, onTap = onLinkChipTap)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldToEndButton(onEnd = WorkoutSession::endEarly)
            FilledIconButton(
                onClick = WorkoutSession::togglePause,
                modifier = Modifier.size(IconButtonDefaults.LargeButtonSize),
            ) {
                ButtonIcon(
                    icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    description = if (isPaused) "Resume" else "Pause",
                    buttonSize = IconButtonDefaults.LargeButtonSize,
                )
            }
            FilledTonalIconButton(
                onClick = onSkip,
                modifier = Modifier.size(IconButtonDefaults.DefaultButtonSize),
            ) {
                ButtonIcon(Icons.Filled.SkipNext, "Skip interval", IconButtonDefaults.DefaultButtonSize)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (isPaused) "PAUSED · HOLD ■ TO END" else "HOLD ■ TO END",
            style = DialType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The dimmed screen: thin outlines and the essentials, nothing lit. */
@Composable
internal fun DialAmbientContent(view: TimerView, format: CountdownFormat) {
    Box(modifier = Modifier.fillMaxSize()) {
        SessionRing(
            segments = view.rememberRingSegments(),
            currentRemaining = { view.remainingFraction() },
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
            Text(text = dialLabel(view), style = DialType.label, color = Color.Gray)
            Countdown(view, format, Color.LightGray)
            if (view.phase != TimerPhase.LEAD_IN) {
                Text(
                    text = view.currentInterval?.name.orEmpty().uppercase(),
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
internal fun DialFinishedContent(view: TimerView, live: State<LiveMetrics>, onDone: () -> Unit) {
    val elapsed = remember(view.elapsedSecondsAtEnd) { formatDuration(view.elapsedSecondsAtEnd) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Completed intervals are lit; anything left after ending early is grey.
        SessionRing(
            segments = view.rememberRingSegments(),
            currentRemaining = { 0f },
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
                text = if (view.endedEarly) "ENDED" else "COMPLETE",
                style = DialType.label,
                color = if (view.endedEarly) ReadyAccent else RestAccent,
            )
            Text(text = elapsed, style = DialType.time, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = "${view.completedIntervals}/${view.intervals.size} INTERVALS",
                style = DialType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FinishedStats(live)
            Spacer(Modifier.height(6.dp))
            CompactButton(onClick = onDone, modifier = Modifier.width(DONE_BUTTON_WIDTH), colors = ButtonDefaults.buttonColors()) {
                Text("DONE", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

private val DONE_BUTTON_WIDTH = 88.dp

/** Heart rate, steps and calories, for the lines under the summary. */
@Composable
private fun FinishedStats(live: State<LiveMetrics>) {
    val metrics = live.value
    val avg = metrics.averageHeartRate
    val max = metrics.maxHeartRate
    if (avg != null && max != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = WorkAccent,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${avg.roundToInt()} AVG · ${max.roundToInt()} MAX",
                style = DialType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (metrics.steps > 0 || metrics.calories > 0) {
        Text(
            text = "${metrics.steps} STEPS · ${metrics.calories.roundToInt()} KCAL",
            style = DialType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
