package com.august.intervaltimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.august.intervaltimer.settings.WatchStores
import com.august.intervaltimer.sync.toIntervals
import com.august.intervaltimer.ui.components.Caption
import com.august.intervaltimer.ui.components.ScreenTitle
import com.august.intervaltimer.ui.components.SectionTitle
import com.august.intervaltimer.ui.components.WorkoutCard

/** Every workout in one list: the ones from the phone, then the starters. */
@Composable
fun WorkoutsScreen(onOpenWorkout: (WorkoutItem) -> Unit) {
    val columnState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    val synced by WatchStores.templates.collectAsState()
    val mine = remember(synced) { synced.map { WorkoutItem(it.name, it.toIntervals()) } }

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
            item(key = "title") { ScreenTitle("WORKOUTS", spec) }

            item(key = "mine") { SectionTitle("MINE", spec) }
            if (mine.isEmpty()) {
                item(key = "mine-hint") { Caption("Build templates on your phone", spec) }
            } else {
                items(mine.size, key = { "mine-$it" }) { index ->
                    WorkoutCard(mine[index], spec) { onOpenWorkout(mine[index]) }
                }
            }

            item(key = "starters") { SectionTitle("STARTERS", spec) }
            items(starterWorkouts.size, key = { "starter-$it" }) { index ->
                WorkoutCard(starterWorkouts[index], spec) { onOpenWorkout(starterWorkouts[index]) }
            }
        }
    }
}
