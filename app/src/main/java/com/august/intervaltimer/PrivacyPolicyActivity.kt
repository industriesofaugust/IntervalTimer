package com.august.intervaltimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.ThemeMode
import com.august.intervaltimer.ui.theme.IntervalTimerTheme
import com.august.intervaltimer.ui.theme.PageTitle
import com.august.intervaltimer.ui.theme.SectionHeader

/**
 * Health Connect opens this from its permission screen, so people can see
 * what the app does with their health data before allowing it.
 */
class PrivacyPolicyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as TimerApp
            val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            IntervalTimerTheme(darkTheme = dark, style = settings.style) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
                        PageTitle("Privacy")
                        Spacer(Modifier.height(24.dp))
                        // The same policy as store/privacy-policy.md and the published
                        // web page, in shorter words. Keep the three in step.
                        Section(
                            "What is collected",
                            "Nothing. The app has no account, no ads, no analytics and no " +
                                "server. It does not ask for the internet permission, so it " +
                                "cannot send your data to us or to anyone else.",
                        )
                        Section(
                            "What is stored on your devices",
                            "The workouts you build, your workout history (with heart rate, " +
                                "steps and calories when the watch measured them), and your " +
                                "settings, including your age if you enter it.",
                        )
                        Section(
                            "Phone and watch",
                            "The two apps keep in step through Google's Wear OS data layer. " +
                                "Your templates, results and settings travel only between " +
                                "your own phone and your own watch.",
                        )
                        Section(
                            "Health Connect: what is written",
                            "Only if you link it. Each finished workout is saved there as an " +
                                "exercise session with its work, rest and pause segments, " +
                                "plus heart rate, steps and calories when the watch measured them.",
                        )
                        Section(
                            "Health Connect: what is read",
                            "Your most recent resting heart rate, only to draw heart-rate zones " +
                                "on a workout. Nothing else is read.",
                        )
                        Section(
                            "How health data is used",
                            "Only to show you your own workouts. It is never used for " +
                                "advertising, never sold and never shared.",
                        )
                        Section(
                            "Your control",
                            "Deleting a workout in the app also deletes its Health Connect " +
                                "records. You can stop sharing, or change what is allowed, in " +
                                "Settings in the app or in Health Connect. Uninstalling the " +
                                "app removes everything it stored on that device.",
                        )
                        Section(
                            "Voice and backups",
                            "With voice cues on, exercise names are read aloud by your " +
                                "phone's own text-to-speech engine. If Android backup is on, " +
                                "Android may keep a copy of the app's data in your Google " +
                                "account; the app's publisher never receives it.",
                        )
                        Section(
                            "Contact",
                            "industriesofaugust@gmail.com",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, text: String) {
    SectionHeader(title)
    Spacer(Modifier.height(6.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
}
