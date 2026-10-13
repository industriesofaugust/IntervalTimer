package com.august.intervaltimer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.sync.SyncedTemplatesViewModel
import com.august.intervaltimer.sync.toIntervals
import com.august.intervaltimer.ui.components.SectionLabel
import com.august.intervaltimer.ui.components.TemplateChip

/** The three built-in starter workouts. */
@Composable
fun StartersScreen(onSelect: (String, List<Interval>) -> Unit) {
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SectionLabel("Starters") }
            builtInTemplates.forEach { template ->
                item {
                    TemplateChip(
                        name = template.name,
                        intervals = template.intervals,
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
            item { SectionLabel("Custom") }
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
                        val intervals = template.toIntervals()
                        TemplateChip(
                            name = template.name,
                            intervals = intervals,
                            onClick = { onSelect(template.name, intervals) },
                        )
                    }
                }
            }
        }
    }
}
