package com.brian.intervaltimer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.brian.intervaltimer.model.Interval
import com.brian.intervaltimer.model.formatDuration
import com.brian.intervaltimer.sync.SyncedTemplatesViewModel
import com.brian.intervaltimer.sync.toIntervals
import com.brian.intervaltimer.sync.totalSeconds

/** The three built-in starter workouts. */
@Composable
fun StartersScreen(onSelect: (String, List<Interval>) -> Unit) {
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Starters") } }
            builtInTemplates.forEach { template ->
                item {
                    TemplateChip(
                        name = template.name,
                        totalSeconds = template.intervals.sumOf { it.durationSeconds },
                        onClick = { onSelect(template.name, template.intervals) },
                    )
                }
            }
        }
    }
}

/** Templates built on the phone, synced over the Data Layer. */
@Composable
fun CustomScreen(
    onSelect: (String, List<Interval>) -> Unit,
    syncedViewModel: SyncedTemplatesViewModel = viewModel(),
) {
    val templates by syncedViewModel.templates.collectAsState()

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Custom") } }
            if (templates.isEmpty()) {
                item {
                    Text(
                        text = "Open Interval Timer on your phone to sync your templates",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else {
                templates.forEach { template ->
                    item {
                        TemplateChip(
                            name = template.name,
                            totalSeconds = template.totalSeconds,
                            onClick = { onSelect(template.name, template.toIntervals()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateChip(
    name: String,
    totalSeconds: Int,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = { Text(formatDuration(totalSeconds)) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
