package com.august.intervaltimer.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.sync.RecentTemplate
import com.august.intervaltimer.sync.SyncedTemplate
import com.august.intervaltimer.sync.SyncedTemplatesStore
import com.august.intervaltimer.sync.publishWatchInfo
import com.august.intervaltimer.sync.toSyncedIntervals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The watch's settings and the templates synced from the phone, created once
 * for the whole app process and kept in memory. Screens read them from here,
 * so nothing waits for a load or shows default values for a moment.
 *
 * [IntervalTimerApp] calls [init] before any activity or service runs.
 */
object WatchStores {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: WatchSettingsRepository
        private set

    /** Null only until the first read from disk is done. */
    lateinit var settings: StateFlow<WatchSettings?>
        private set

    lateinit var templates: StateFlow<List<SyncedTemplate>>
        private set

    /** Safe to call any number of times; only the first call does anything. */
    fun init(context: Context) {
        if (::repository.isInitialized) return
        val app = context.applicationContext
        repository = WatchSettingsRepository(app)
        // Decoding the saved workouts happens off the main thread.
        settings = repository.settings
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, null)
        val store = SyncedTemplatesStore(app, scope)
        templates = store.templates
        // Tell the phone our version now, and again whenever templates arrive.
        // Waits for the first read so the phone never sees a made-up count of 0.
        scope.launch {
            store.loaded.first { it }
            combine(store.templates, store.receivedAt) { list, receivedAt -> list.size to receivedAt }
                .collect { (count, receivedAt) -> publishWatchInfo(app, count, receivedAt) }
        }
    }

    /** Remembers a workout the user just started, for the Home screen. */
    fun addRecent(name: String, intervals: List<Interval>) {
        scope.launch { repository.addRecent(RecentTemplate(name, intervals.toSyncedIntervals())) }
    }
}

/**
 * The settings for screens, which are only shown once they have loaded (the
 * root of the app draws nothing until then).
 */
@Composable
fun rememberWatchSettings(): State<WatchSettings> {
    val loaded = WatchStores.settings.collectAsState()
    return remember { derivedStateOf { loaded.value ?: WatchSettings() } }
}
