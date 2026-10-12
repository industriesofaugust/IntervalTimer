package com.august.intervaltimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.ThemeMode
import com.august.intervaltimer.timer.TimerViewModel
import com.august.intervaltimer.ui.ActivityScreen
import com.august.intervaltimer.ui.EditorScreen
import com.august.intervaltimer.ui.LibraryScreen
import com.august.intervaltimer.ui.PhoneSettingsScreen
import com.august.intervaltimer.ui.QuickBuildScreen
import com.august.intervaltimer.ui.TimerScreen
import com.august.intervaltimer.ui.WorkoutDetailScreen
import com.august.intervaltimer.ui.theme.IntervalTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IntervalTimerApp()
        }
    }
}

private data class TopLevelPage(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelPages = listOf(
    TopLevelPage("home", "Home", Icons.Filled.Home),
    TopLevelPage("activity", "Activity", Icons.Filled.BarChart),
    TopLevelPage("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun IntervalTimerApp() {
    val app = LocalContext.current.applicationContext as TimerApp
    val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val navController = rememberNavController()
    // One timer for the whole app: whichever screen starts a session hands
    // its intervals to this ViewModel, then navigates to the timer screen.
    val timerViewModel: TimerViewModel = viewModel()
    // Name of the workout being run, saved with it in History.
    var sessionName by rememberSaveable { mutableStateOf<String?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelPages.any { it.route == currentRoute }

    IntervalTimerTheme(darkTheme = darkTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        topLevelPages.forEach { page ->
                            NavigationBarItem(
                                selected = currentRoute == page.route,
                                onClick = { navController.navigateTopLevel(page.route) },
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
                    .padding(innerPadding),
            ) {
                composable("home") {
                    LibraryScreen(
                        onQuickSession = { navController.navigate("quickbuild") },
                        onNewTemplate = { navController.navigate("editor") },
                        onEditTemplate = { id -> navController.navigate("editor?templateId=$id") },
                        onPlayTemplate = { name, intervals ->
                            sessionName = name
                            timerViewModel.start(intervals)
                            navController.navigate("timer")
                        },
                    )
                }
                composable("activity") {
                    ActivityScreen(
                        onOpenWorkout = { id -> navController.navigate("workout/$id") },
                    )
                }
                composable("settings") {
                    PhoneSettingsScreen()
                }
                composable("quickbuild") {
                    QuickBuildScreen(
                        onStart = { intervals ->
                            sessionName = "Quick session"
                            timerViewModel.start(intervals)
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

/** Standard bottom-bar navigation: one copy of each page, state preserved. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
