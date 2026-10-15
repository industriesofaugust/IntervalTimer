package com.august.intervaltimer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.august.intervaltimer.settings.rememberWatchSettings
import com.august.intervaltimer.sync.toIntervals
import com.august.intervaltimer.ui.components.MenuButton
import com.august.intervaltimer.ui.components.WorkoutCard

/**
 * Home: resume a running workout, start one of the last two again, or go
 * to the workout list, the new-workout screen, or settings.
 */
@Composable
fun HomeScreen(
    onResume: () -> Unit,
    onOpenWorkout: (WorkoutItem) -> Unit,
    onWorkouts: () -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val columnState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val running by rememberWorkoutRunning()

    val recents = rememberWatchSettings().value.recents
    val recentWorkouts = remember(recents) {
        recents.map { WorkoutItem(it.name, it.toIntervals()) }
    }

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
            if (running) {
                item(key = "resume") {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        icon = {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                        },
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text("RESUME", maxLines = 1)
                    }
                }
            }
            recentWorkouts.forEachIndexed { index, workout ->
                item(key = "recent-$index") {
                    WorkoutCard(workout, spec) { onOpenWorkout(workout) }
                }
            }
            item(key = "workouts") {
                MenuButton("WORKOUTS", Icons.AutoMirrored.Filled.List, spec, onWorkouts)
            }
            item(key = "new") {
                MenuButton("NEW", Icons.Filled.Add, spec, onNew)
            }
            item(key = "settings") {
                MenuButton("SETTINGS", Icons.Filled.Settings, spec, onSettings)
            }
        }
    }
}
