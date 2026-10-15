package com.august.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.august.intervaltimer.design.ringSegments
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.ui.components.DialType
import com.august.intervaltimer.ui.components.SessionRing
import com.august.intervaltimer.ui.components.transformedItem

/**
 * Shows what a workout contains before the user commits to starting it: the
 * session ring as a map, then every interval in order. Start sits inside the
 * ring, so it can be tapped straight away, and again at the end of the list.
 */
@Composable
fun PreviewScreen(workout: WorkoutItem, onStart: () -> Unit) {
    val columnState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = columnState,
        edgeButton = {
            EdgeButton(onClick = onStart, buttonSize = EdgeButtonSize.Medium) {
                Text("START")
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
            item(key = "ring") { PreviewRing(workout, spec, onStart) }
            items(workout.intervals.size, key = { "interval-$it" }) { index ->
                IntervalRow(workout.intervals[index], spec)
            }
        }
    }
}

/** The workout as a map: every arc lit, with name, count and total inside. */
@Composable
private fun TransformingLazyColumnItemScope.PreviewRing(
    workout: WorkoutItem,
    spec: TransformationSpec,
    onStart: () -> Unit,
) {
    val segments = remember(workout) {
        ringSegments(workout.intervals, doneCount = workout.intervals.size)
    }

    Box(
        modifier = Modifier.transformedItem(this, spec).size(RING_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        SessionRing(
            segments = segments,
            currentRemaining = { 0f },
            modifier = Modifier.fillMaxSize(),
            showTicks = false,
        )
        Column(
            modifier = Modifier.padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = workout.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = workout.countLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(text = workout.totalLabel, style = DialType.time)
            CompactButton(
                onClick = onStart,
                label = { Text("START") },
                colors = ButtonDefaults.buttonColors(),
            )
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.IntervalRow(interval: Interval, spec: TransformationSpec) {
    val accent = when (interval.type) {
        IntervalType.WORK -> WorkAccent
        IntervalType.REST -> RestAccent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .transformedItem(this, spec)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = interval.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(interval.durationSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RING_SIZE = 172.dp
