package com.august.intervaltimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.august.intervaltimer.metrics.MetricsViewModel
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.settings.WatchSettingsRepository
import com.august.intervaltimer.sync.RecentTemplate
import com.august.intervaltimer.sync.publishWatchCues
import com.august.intervaltimer.sync.toSyncedIntervals
import com.august.intervaltimer.timer.TimerViewModel
import com.august.intervaltimer.ui.CreateScreen
import com.august.intervaltimer.ui.CustomScreen
import com.august.intervaltimer.ui.PreviewScreen
import com.august.intervaltimer.ui.StartersScreen
import com.august.intervaltimer.ui.TemplateListScreen
import com.august.intervaltimer.ui.WatchSettingsScreen
import com.august.intervaltimer.ui.WearColors
import com.august.intervaltimer.ui.WearTimerScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    val navController = rememberSwipeDismissableNavController()
    val timerViewModel: TimerViewModel = viewModel()
    val metricsViewModel: MetricsViewModel = viewModel()

    // The workout the user is looking at in the preview screen.
    var pendingName by remember { mutableStateOf("") }
    var pendingIntervals by remember { mutableStateOf<List<Interval>>(emptyList()) }

    val context = LocalContext.current
    val settingsRepository = remember { WatchSettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // Tell the phone what the watch's cue settings really are, so its
    // Settings page never edits from stale defaults.
    LaunchedEffect(Unit) {
        publishWatchCues(context.applicationContext, settingsRepository.settings.first().cues)
    }

    val startWorkout: (String, List<Interval>) -> Unit = { name, intervals ->
        scope.launch {
            settingsRepository.addRecent(RecentTemplate(name, intervals.toSyncedIntervals()))
        }
        metricsViewModel.prepareNew(name)
        timerViewModel.start(intervals)
        navController.navigate("timer")
    }

    MaterialTheme(colors = WearColors) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "templates",
        ) {
            composable("templates") {
                TemplateListScreen(
                    timerViewModel = timerViewModel,
                    onSelect = { name, intervals ->
                        pendingName = name
                        pendingIntervals = intervals
                        navController.navigate("preview")
                    },
                    onResume = { navController.navigate("timer") },
                    onCreate = { navController.navigate("create") },
                    onSettings = { navController.navigate("settings") },
                    onStarters = { navController.navigate("starters") },
                    onCustom = { navController.navigate("custom") },
                )
            }
            composable("starters") {
                StartersScreen(
                    onSelect = { name, intervals ->
                        pendingName = name
                        pendingIntervals = intervals
                        navController.navigate("preview")
                    },
                )
            }
            composable("custom") {
                CustomScreen(
                    onSelect = { name, intervals ->
                        pendingName = name
                        pendingIntervals = intervals
                        navController.navigate("preview")
                    },
                )
            }
            composable("preview") {
                PreviewScreen(
                    name = pendingName,
                    intervals = pendingIntervals,
                    onStart = { startWorkout(pendingName, pendingIntervals) },
                )
            }
            composable("create") {
                CreateScreen(onStart = startWorkout)
            }
            composable("settings") {
                WatchSettingsScreen()
            }
            composable("timer") {
                WearTimerScreen(
                    viewModel = timerViewModel,
                    metricsViewModel = metricsViewModel,
                    onDone = {
                        timerViewModel.stop()
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
