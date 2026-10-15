package com.august.intervaltimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import com.august.intervaltimer.settings.WatchStores
import com.august.intervaltimer.ui.WearTheme
import com.august.intervaltimer.ui.components.GroteskFamily
import com.august.intervaltimer.ui.components.MonoFamily
import com.august.intervaltimer.workout.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    // Ambient is the watch's dimmed, low-power screen state.
    private var isAmbient by mutableStateOf(false)

    // True once both fonts are loaded (see onCreate).
    private var fontsReady by mutableStateOf(false)

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

        // Loading a font on the main thread, in the middle of the first frame,
        // is a visible hitch. So both families load in the background here,
        // through the same resolver the screens then use. The time limit and
        // runCatching are a safety net: if loading stalls or fails, the
        // screens show anyway.
        val fontResolver = createFontFamilyResolver(this)
        lifecycleScope.launch(Dispatchers.Default) {
            withTimeoutOrNull(FONT_LOAD_LIMIT_MILLIS) {
                coroutineScope {
                    launch { runCatching { fontResolver.preload(MonoFamily) } }
                    launch { runCatching { fontResolver.preload(GroteskFamily) } }
                }
            }
            fontsReady = true
        }

        setContent {
            CompositionLocalProvider(LocalFontFamilyResolver provides fontResolver) {
                // Nothing is drawn (the screen stays black) until the settings
                // and fonts are loaded, so the first frame is already the real one.
                val settings = WatchStores.settings.collectAsState()
                val ready by remember { derivedStateOf { fontsReady && settings.value != null } }
                if (ready) {
                    WearTheme {
                        WearApp(isAmbient = isAmbient)
                    }
                }
            }
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

    private companion object {
        const val FONT_LOAD_LIMIT_MILLIS = 2_000L
    }
}
