package com.august.intervaltimer

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.august.intervaltimer.design.AppStyle
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.settings.WatchSettings
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.ui.components.LocalDial
import com.august.intervaltimer.settings.WatchSettingsRepository
import com.august.intervaltimer.sync.RecentTemplate
import com.august.intervaltimer.sync.publishWatchCues
import com.august.intervaltimer.sync.toSyncedIntervals
import com.august.intervaltimer.ui.CreateScreen
import com.august.intervaltimer.ui.CustomScreen
import com.august.intervaltimer.ui.PreviewScreen
import com.august.intervaltimer.ui.StartersScreen
import com.august.intervaltimer.ui.TemplateListScreen
import com.august.intervaltimer.ui.WatchSettingsScreen
import com.august.intervaltimer.ui.WearColors
import com.august.intervaltimer.ui.WearTimerScreen
import com.august.intervaltimer.workout.WorkoutSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Ambient is the watch's dimmed, low-power screen state.
    private var isAmbient by mutableStateOf(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
            WorkoutSession.setSmoothTicks(false)
        }

        override fun onExitAmbient() {
            isAmbient = false
            WorkoutSession.setSmoothTicks(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkoutSession.init(this)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        setContent {
            WearApp(isAmbient = isAmbient)
        }
    }

    override fun onResume() {
        super.onResume()
        WorkoutSession.setSmoothTicks(!isAmbient)
    }

    override fun onPause() {
        WorkoutSession.setSmoothTicks(false)
        super.onPause()
    }
}

@Composable
fun WearApp(isAmbient: Boolean) {
    val navController = rememberSwipeDismissableNavController()

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

    // Opening the app while a workout is running goes straight to the timer.
    LaunchedEffect(Unit) {
        if (WorkoutSession.inProgress) navController.navigate("timer")
    }

    // Permissions are asked before the workout starts, because the service
    // that keeps the timer alive needs to know whether sensors are allowed.
    // Saying no still runs the timer, just without heart rate or steps.
    var queuedStart by remember { mutableStateOf<Pair<String, List<Interval>>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        queuedStart?.let { (name, intervals) ->
            scope.launch {
                settingsRepository.addRecent(RecentTemplate(name, intervals.toSyncedIntervals()))
            }
            WorkoutSession.start(context, name, intervals)
            navController.navigate("timer")
        }
        queuedStart = null
    }
    val startWorkout: (String, List<Interval>) -> Unit = { name, intervals ->
        queuedStart = name to intervals
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        )
    }

    val settings by settingsRepository.settings.collectAsState(initial = WatchSettings())
    val session by WorkoutSession.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // During a workout the timer can't be swiped away by accident. The ways
    // out are End, or the crown, which leaves the workout running.
    val workoutRunning = session.intervals.isNotEmpty() && session.phase != TimerPhase.FINISHED
    val swipeAllowed = !(backStackEntry?.destination?.route == "timer" && workoutRunning)

    MaterialTheme(colors = WearColors) {
      CompositionLocalProvider(LocalDial provides (settings.style == AppStyle.DIAL)) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "templates",
            userSwipeEnabled = swipeAllowed,
        ) {
            composable("templates") {
                TemplateListScreen(
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
                    isAmbient = isAmbient,
                    onDone = {
                        WorkoutSession.clear()
                        navController.popBackStack()
                    },
                )
            }
        }
      }
    }
}
