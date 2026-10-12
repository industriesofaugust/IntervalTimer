package com.brian.intervaltimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
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
import com.brian.intervaltimer.metrics.MetricsViewModel
import com.brian.intervaltimer.model.Interval
import com.brian.intervaltimer.settings.WatchSettingsRepository
import com.brian.intervaltimer.sync.RecentTemplate
import com.brian.intervaltimer.sync.toSyncedIntervals
import com.brian.intervaltimer.timer.TimerViewModel
import com.brian.intervaltimer.ui.CreateScreen
import com.brian.intervaltimer.ui.CustomScreen
import com.brian.intervaltimer.ui.PreviewScreen
import com.brian.intervaltimer.ui.StartersScreen
import com.brian.intervaltimer.ui.TemplateListScreen
import com.brian.intervaltimer.ui.WatchSettingsScreen
import com.brian.intervaltimer.ui.WearColors
import com.brian.intervaltimer.ui.WearTimerScreen
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
