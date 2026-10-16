package com.august.intervaltimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.PhoneLiveLink
import com.august.intervaltimer.data.ThemeMode
import com.august.intervaltimer.data.WatchRequest
import com.august.intervaltimer.data.WatchRequestNotification
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.timer.TimerViewModel
import com.august.intervaltimer.ui.ActivityScreen
import com.august.intervaltimer.ui.EditorScreen
import com.august.intervaltimer.ui.LibraryScreen
import com.august.intervaltimer.ui.PhoneSettingsScreen
import com.august.intervaltimer.ui.QuickBuildScreen
import com.august.intervaltimer.ui.TimerScreen
import com.august.intervaltimer.ui.WorkoutDetailScreen
import com.august.intervaltimer.ui.components.DialBottomBar
import com.august.intervaltimer.ui.theme.IntervalTimerTheme
import com.august.intervaltimer.ui.theme.isDial
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // A workout on the watch that the user chose to follow, from the notification.
    private var followRequest by mutableStateOf<WatchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation or theme change the old intent comes back; it was already handled.
        if (savedInstanceState == null) followRequest = WatchRequestNotification.requestFrom(intent)
        setContent {
            IntervalTimerApp(
                followRequest = followRequest,
                onFollowHandled = { followRequest = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WatchRequestNotification.requestFrom(intent)?.let { followRequest = it }
    }

    // Tells the listener service whether to show a banner or a notification.
    override fun onStart() {
        super.onStart()
        PhoneLiveLink.appVisible = true
    }

    override fun onStop() {
        super.onStop()
        PhoneLiveLink.appVisible = false
    }
}

private data class TopLevelPage(
    val label: String,
    val icon: ImageVector,
)

// In swipe order: the index here is the page number in the pager.
private val topLevelPages = listOf(
    TopLevelPage("Home", Icons.Filled.Home),
    TopLevelPage("Activity", Icons.Filled.BarChart),
    TopLevelPage("Settings", Icons.Filled.Settings),
)

@Composable
fun IntervalTimerApp(followRequest: WatchRequest? = null, onFollowHandled: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as TimerApp
    val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val navController = rememberNavController()
    // Lives out here so the bottom bar can read it, and so the page you were
    // on is still selected after visiting a detail screen and coming back.
    val pagerState = rememberPagerState { topLevelPages.size }
    val scope = rememberCoroutineScope()
    // One timer for the whole app: whichever screen starts a session hands
    // its intervals to this ViewModel, then navigates to the timer screen.
    val timerViewModel: TimerViewModel = viewModel()
    // Name of the workout being run, saved with it in History.
    var sessionName by rememberSaveable { mutableStateOf<String?>(null) }

    fun startWorkout(name: String, intervals: List<Interval>) {
        sessionName = name
        timerViewModel.start(intervals)
        // Asks the watch to run it too, unless "Use watch" is off.
        PhoneLiveLink.begin(app, timerViewModel, name, intervals, settings.useWatch)
    }

    // The user chose to join a workout that is running on the watch.
    fun followWatch(pending: WatchRequest) {
        WatchRequestNotification.cancel(app)
        if (PhoneLiveLink.follow(app, timerViewModel, pending)) {
            sessionName = pending.request.sessionName
            navController.navigate("timer") { launchSingleTop = true }
        }
    }

    LaunchedEffect(followRequest) {
        followRequest?.let {
            followWatch(it)
            onFollowHandled()
        }
    }

    // Asked when "Use watch" is on, so a request from the watch can show a notification.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        app.settingsRepository.settings.map { it.useWatch }.distinctUntilChanged().collect { useWatch ->
            val granted = ContextCompat.checkSelfPermission(
                app, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (useWatch && Build.VERSION.SDK_INT >= 33 && !granted) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == "home"

    // Where a request from the watch shows as a banner rather than a notification.
    val homeInView = showBottomBar && pagerState.currentPage == 0
    DisposableEffect(homeInView) {
        PhoneLiveLink.homeVisible = homeInView
        onDispose { PhoneLiveLink.homeVisible = false }
    }

    // Back on Activity or Settings returns to the first page. Only enabled on
    // "home", so it never takes back presses from the other screens.
    BackHandler(enabled = showBottomBar && pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    IntervalTimerTheme(darkTheme = darkTheme, style = settings.style) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar && isDial()) {
                    DialBottomBar(
                        labels = topLevelPages.map { it.label },
                        selectedIndex = pagerState.currentPage,
                        onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                    )
                } else if (showBottomBar) {
                    NavigationBar {
                        topLevelPages.forEachIndexed { index, page ->
                            NavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                icon = { Icon(page.icon, contentDescription = null) },
                                label = { Text(page.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // The screens add their own safeDrawingPadding(); this stops
                    // them re-adding the insets innerPadding already covers.
                    .consumeWindowInsets(innerPadding),
            ) {
                composable("home") {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (page) {
                            0 -> LibraryScreen(
                                onQuickSession = { navController.navigate("quickbuild") },
                                onNewTemplate = { navController.navigate("editor") },
                                onEditTemplate = { id -> navController.navigate("editor?templateId=$id") },
                                onPlayTemplate = { name, intervals ->
                                    startWorkout(name, intervals)
                                    navController.navigate("timer")
                                },
                                onFollowWatch = ::followWatch,
                            )
                            1 -> ActivityScreen(
                                onOpenWorkout = { id -> navController.navigate("workout/$id") },
                            )
                            else -> PhoneSettingsScreen()
                        }
                    }
                }
                composable("quickbuild") {
                    QuickBuildScreen(
                        onStart = { intervals ->
                            startWorkout("Quick session", intervals)
                            navController.navigate("timer")
                        },
                        onCustomize = { rounds, work, rest ->
                            navController.navigate("editor?rounds=$rounds&work=$work&rest=$rest")
                        },
                    )
                }
                composable(
                    route = "editor?templateId={templateId}&rounds={rounds}&work={work}&rest={rest}",
                    arguments = listOf(
                        navArgument("templateId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("rounds") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("work") { type = NavType.IntType; defaultValue = 40 },
                        navArgument("rest") { type = NavType.IntType; defaultValue = 20 },
                    ),
                ) {
                    EditorScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = {
                            navController.popBackStack(route = "home", inclusive = false)
                        },
                        onSaveAndStart = { name, intervals ->
                            startWorkout(name, intervals)
                            // Drop the editor, so finishing the workout returns Home.
                            navController.navigate("timer") { popUpTo("home") }
                        },
                    )
                }
                composable(
                    route = "workout/{resultId}",
                    arguments = listOf(
                        navArgument("resultId") { type = NavType.LongType },
                    ),
                ) {
                    WorkoutDetailScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("timer") {
                    TimerScreen(
                        viewModel = timerViewModel,
                        sessionName = sessionName,
                        onExit = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
