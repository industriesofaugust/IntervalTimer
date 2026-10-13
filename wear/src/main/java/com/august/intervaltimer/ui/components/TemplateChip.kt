package com.august.intervaltimer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.august.intervaltimer.design.intervalCountLabel
import com.august.intervaltimer.design.stripSegments
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.formatDuration

/** A section heading: capital mono label in Dial, the system header in Minimal. */
@Composable
fun SectionLabel(text: String) {
    if (LocalDial.current) {
        ListHeader {
            Text(
                text = text.uppercase(),
                style = DialType.label,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    } else {
        ListHeader { Text(text) }
    }
}

/**
 * One workout in a list. In Dial it shows the interval count, total time,
 * and the session strip; in Minimal just the name and total time.
 */
@Composable
fun TemplateChip(
    name: String,
    intervals: List<Interval>,
    onClick: () -> Unit,
    showCount: Boolean = true,
) {
    val dial = LocalDial.current
    val total = formatDuration(intervals.sumOf { it.durationSeconds })
    Chip(
        onClick = onClick,
        label = {
            Text(
                text = name,
                style = if (dial) DialType.name else MaterialTheme.typography.button,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            if (dial) {
                Column {
                    Text(
                        text = if (showCount) "${intervalCountLabel(intervals.size)} · $total" else total,
                        style = DialType.caption,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    SessionStrip(
                        segments = stripSegments(intervals),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(total)
            }
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
