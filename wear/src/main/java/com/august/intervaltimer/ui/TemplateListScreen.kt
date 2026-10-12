package com.august.intervaltimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.QuickBuildConfig
import com.august.intervaltimer.model.buildSession
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.settings.WatchSettings
import com.august.intervaltimer.settings.WatchSettingsRepository
import com.august.intervaltimer.sync.toIntervals
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerViewModel

/** Built-in workouts, always available even before the first sync. */
data class BuiltInTemplate(val name: String, val intervals: List<Interval>)

val builtInTemplates = listOf(
    BuiltInTemplate("Tabata 8×20/10", buildSession(QuickBuildConfig(8, 20, 10))),
    BuiltInTemplate("HIIT 8×40/20", buildSession(QuickBuildConfig(8, 40, 20))),
    BuiltInTemplate("Strength 5×60/90", buildSession(QuickBuildConfig(5, 60, 90))),
)

@Composable
fun TemplateListScreen(
    timerViewModel: TimerViewModel,
    onSelect: (String, List<Interval>) -> Unit,
    onResume: () -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    onStarters: () -> Unit,
    onCustom: () -> Unit,
) {
    val state by timerViewModel.uiState.collectAsState()
    // Protects against an accidental swipe-away mid-workout.
    val inProgress = state.intervals.isNotEmpty() && state.phase != TimerPhase.FINISHED

    val context = LocalContext.current
    val settingsRepository = remember { WatchSettingsRepository(context.applicationContext) }
    val settings by settingsRepository.settings.collectAsState(initial = WatchSettings())

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            if (inProgress) {
                item {
                    Chip(
                        onClick = onResume,
                        label = { Text("Resume session") },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onCreate,
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New workout",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Button(
                        onClick = onSettings,
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (settings.recents.isNotEmpty()) {
                item { ListHeader { Text("Recents") } }
                settings.recents.take(2).forEach { recent ->
                    item {
                        Chip(
                            onClick = { onSelect(recent.name, recent.toIntervals()) },
                            label = {
                                Text(
                                    text = recent.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            secondaryLabel = {
                                Text(
                                    formatDuration(recent.intervals.sumOf { it.durationSeconds })
                                )
                            },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CompactChip(
                        onClick = onStarters,
                        label = { Text("Starters") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                    CompactChip(
                        onClick = onCustom,
                        label = { Text("Custom") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
