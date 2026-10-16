package com.august.intervaltimer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.settings.WatchStores
import com.august.intervaltimer.sync.publishWatchCues
import com.august.intervaltimer.ui.CreateScreen
import com.august.intervaltimer.ui.CreateViewModel
import com.august.intervaltimer.ui.DurationPickerScreen
import com.august.intervaltimer.ui.HomeScreen
import com.august.intervaltimer.ui.PreviewScreen
import com.august.intervaltimer.ui.WatchSettingsScreen
import com.august.intervaltimer.ui.WearTimerScreen
import com.august.intervaltimer.ui.WorkoutItem
import com.august.intervaltimer.ui.WorkoutsScreen
import com.august.intervaltimer.ui.rememberWorkoutRunning
import com.august.intervaltimer.workout.WorkoutSession

private object Route {
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val PREVIEW = "preview"
    const val CREATE = "create"
    const val PICK_WORK = "pick-work"
    const val PICK_REST = "pick-rest"
    const val SETTINGS = "settings"
    const val TIMER = "timer"
}

/** Sensor and notification access, asked for before a workout starts. */
private val workoutPermissions = arrayOf(
    Manifest.permission.BODY_SENSORS,
    Manifest.permission.ACTIVITY_RECOGNITION,
    Manifest.permission.POST_NOTIFICATIONS,
)

/** The workout the user is looking at in the preview screen. Survives going back. */
class PendingWorkoutViewModel : ViewModel() {
    var workout by mutableStateOf<WorkoutItem?>(null)
}

/** Opens a screen, unless it is already the one on top (a double tap). */
private fun NavController.open(route: String) {
    navigate(route) { launchSingleTop = true }
}

@Composable
fun WearApp(isAmbient: Boolean) {
    val context = LocalContext.current
    val navController = rememberSwipeDismissableNavController()
    val pending: PendingWorkoutViewModel = viewModel()

    // Tell the phone what the watch's cue settings really are, so its
    // Settings page never edits from stale defaults.
    LaunchedEffect(Unit) {
        WatchStores.settings.value?.let { publishWatchCues(context.applicationContext, it.cues) }
    }

    // Opening the app while a workout is running goes straight to the timer.
    LaunchedEffect(Unit) {
        if (WorkoutSession.inProgress) navController.open(Route.TIMER)
    }

    // The phone owns the workout and syncing stopped: this copy is gone, so
    // leave its timer. Anywhere else the user stays where they are.
    LaunchedEffect(Unit) {
        WorkoutSession.stepOut.collect {
            if (navController.currentDestination?.route == Route.TIMER) {
                navController.popBackStack(Route.HOME, inclusive = false)
            }
        }
    }

    fun beginWorkout(name: String, intervals: List<Interval>) {
        WatchStores.addRecent(name, intervals)
        WorkoutSession.start(context, name, intervals)
        // Back from the timer goes to Home, not through the screens that led here.
        navController.navigate(Route.TIMER) {
            popUpTo(Route.HOME)
            launchSingleTop = true
        }
    }

    // Permissions are asked before the workout starts, because the service
    // that keeps the timer alive needs to know whether sensors are allowed.
    // Saying no still runs the timer, just without heart rate or steps.
    var queuedStart by remember { mutableStateOf<Pair<String, List<Interval>>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        queuedStart?.let { (name, intervals) -> beginWorkout(name, intervals) }
        queuedStart = null
    }
    val startWorkout: (String, List<Interval>) -> Unit = { name, intervals ->
        val missing = workoutPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            beginWorkout(name, intervals)
        } else {
            queuedStart = name to intervals
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val openWorkout: (WorkoutItem) -> Unit = { workout ->
        pending.workout = workout
        navController.open(Route.PREVIEW)
    }

    // During a workout the timer can't be swiped away by accident. The ways
    // out are End, or the crown, which leaves the workout running.
    val workoutRunning by rememberWorkoutRunning()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val swipeAllowed = !(backStackEntry?.destination?.route == Route.TIMER && workoutRunning)

    // The clock sits at the top of every screen except the timer and the time
    // pickers, which need the whole circle.
    val route = backStackEntry?.destination?.route
    val showClock = route != Route.TIMER && route != Route.PICK_WORK && route != Route.PICK_REST

    AppScaffold(timeText = { if (showClock) TimeText() }) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Route.HOME,
            userSwipeEnabled = swipeAllowed,
        ) {
            composable(Route.HOME) {
                HomeScreen(
                    onResume = { navController.open(Route.TIMER) },
                    onOpenWorkout = openWorkout,
                    onWorkouts = { navController.open(Route.WORKOUTS) },
                    onNew = { navController.open(Route.CREATE) },
                    onSettings = { navController.open(Route.SETTINGS) },
                )
            }
            composable(Route.WORKOUTS) {
                WorkoutsScreen(onOpenWorkout = openWorkout)
            }
            composable(Route.PREVIEW) {
                // Empty only if the app was restored without its memory.
                pending.workout?.let { workout ->
                    PreviewScreen(
                        workout = workout,
                        onStart = { startWorkout(workout.name, workout.intervals) },
                    )
                }
            }
            composable(Route.CREATE) {
                CreateScreen(
                    onStart = startWorkout,
                    onPickWork = { navController.open(Route.PICK_WORK) },
                    onPickRest = { navController.open(Route.PICK_REST) },
                )
            }
            composable(Route.PICK_WORK) { entry ->
                // The values live with the New screen, so swiping back from
                // here simply leaves them as they were.
                val create: CreateViewModel =
                    viewModel(remember(entry) { navController.getBackStackEntry(Route.CREATE) })
                DurationPickerScreen(
                    initialSeconds = create.workSeconds,
                    onPicked = { seconds ->
                        create.workSeconds = seconds
                        navController.popBackStack()
                    },
                )
            }
            composable(Route.PICK_REST) { entry ->
                val create: CreateViewModel =
                    viewModel(remember(entry) { navController.getBackStackEntry(Route.CREATE) })
                DurationPickerScreen(
                    initialSeconds = create.restSeconds,
                    onPicked = { seconds ->
                        create.restSeconds = seconds
                        navController.popBackStack()
                    },
                )
            }
            composable(Route.SETTINGS) {
                WatchSettingsScreen()
            }
            composable(Route.TIMER) {
                WearTimerScreen(
                    isAmbient = isAmbient,
                    onDone = {
                        // Leave first; the timer keeps showing the finished
                        // summary while it slides away, then the workout is cleared.
                        navController.popBackStack(Route.HOME, inclusive = false)
                        WorkoutSession.clear()
                    },
                )
            }
        }
    }
}
