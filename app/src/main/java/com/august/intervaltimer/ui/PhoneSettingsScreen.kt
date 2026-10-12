package com.august.intervaltimer.ui

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.HealthConnectWriter
import com.august.intervaltimer.data.ThemeMode
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.sync.WatchCueSettings
import com.august.intervaltimer.sync.WatchCueSettingsCodec
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

class PhoneSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as TimerApp).settingsRepository
    private val resultsRepository = (app as TimerApp).resultsRepository
    private val healthConnect = HealthConnectWriter(app)

    val settings = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val healthConnectAvailable = healthConnect.isAvailable()
    val healthConnectPermissions = healthConnect.permissions
    var healthConnectLinked by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { healthConnectLinked = healthConnect.hasAllPermissions() }
    }

    /** After the user grants access, copy the existing history over too. */
    fun backfillHealthConnect() {
        viewModelScope.launch {
            healthConnectLinked = healthConnect.hasAllPermissions()
            if (!healthConnectLinked) return@launch
            resultsRepository.observeAll().first().forEach { healthConnect.write(it) }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
    }

    fun setSoundVolume(volume: Int) {
        viewModelScope.launch { repository.setSoundVolume(volume) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }
}

/**
 * Remote control for the watch's cue settings: reads the shared Data Layer
 * document, listens for changes made on the watch, and writes edits back.
 */
class WatchCuesViewModel(app: Application) :
    AndroidViewModel(app), DataClient.OnDataChangedListener {

    private val dataClient = Wearable.getDataClient(app)

    var cues by mutableStateOf(WatchCueSettings())
        private set

    /**
     * True once the watch's real settings have arrived. Until then the phone
     * only has defaults, and saving those would overwrite the watch.
     */
    var loaded by mutableStateOf(false)
        private set

    init {
        dataClient.addListener(this)
        viewModelScope.launch { loadCurrent() }
    }

    private suspend fun loadCurrent() {
        runCatching {
            val buffer = dataClient.dataItems.await()
            try {
                buffer.forEach { item -> readItem(item) }
            } finally {
                buffer.release()
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) readItem(event.dataItem)
        }
    }

    private fun readItem(item: DataItem) {
        if (item.uri.path != SyncPaths.WATCH_SETTINGS_PATH) return
        runCatching {
            val json = DataMapItem.fromDataItem(item)
                .dataMap.getString(SyncPaths.WATCH_SETTINGS_KEY)
            if (json != null) {
                cues = WatchCueSettingsCodec.decode(json)
                loaded = true
            }
        }
    }

    fun update(transform: (WatchCueSettings) -> WatchCueSettings) {
        if (!loaded) return
        cues = transform(cues)
        viewModelScope.launch {
            runCatching {
                val request = PutDataMapRequest.create(SyncPaths.WATCH_SETTINGS_PATH)
                    .apply {
                        dataMap.putString(
                            SyncPaths.WATCH_SETTINGS_KEY,
                            WatchCueSettingsCodec.encode(cues),
                        )
                    }
                    .asPutDataRequest()
                    .setUrgent()
                dataClient.putDataItem(request).await()
            }
        }
    }

    /** Asks the watch to buzz/beep through the full cue sequence. */
    fun testOnWatch() {
        viewModelScope.launch {
            runCatching {
                val request = PutDataMapRequest
                    .create("${SyncPaths.WATCH_TEST_PATH_PREFIX}/${System.currentTimeMillis()}")
                    .apply { dataMap.putString(SyncPaths.WATCH_TEST_KEY, "go") }
                    .asPutDataRequest()
                    .setUrgent()
                dataClient.putDataItem(request).await()
            }
        }
    }

    override fun onCleared() {
        dataClient.removeListener(this)
    }
}

@Composable
fun PhoneSettingsScreen(
    viewModel: PhoneSettingsViewModel = viewModel(),
    watchCues: WatchCuesViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.backfillHealthConnect()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(24.dp))

        SettingsSection(title = "Watch cues") {
            if (!watchCues.loaded) {
                Text(
                    text = "Open Interval Timer on your watch once to load its settings here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
            ToggleRow(
                title = "Vibration",
                subtitle = "Buzzes for 3-2-1, work, rest, and finish",
                checked = watchCues.cues.vibrationEnabled,
                onCheckedChange = { on ->
                    watchCues.update { it.copy(vibrationEnabled = on) }
                },
            )
            if (watchCues.cues.vibrationEnabled) {
                LevelSlider(
                    level = watchCues.cues.vibrationLevel,
                    onLevelChange = { level ->
                        watchCues.update { it.copy(vibrationLevel = level) }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Sound",
                subtitle = "Beeps from the watch speaker",
                checked = watchCues.cues.soundEnabled,
                onCheckedChange = { on ->
                    watchCues.update { it.copy(soundEnabled = on) }
                },
            )
            if (watchCues.cues.soundEnabled) {
                LevelSlider(
                    level = watchCues.cues.soundLevel,
                    onLevelChange = { level ->
                        watchCues.update { it.copy(soundLevel = level) }
                    },
                )
            }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = watchCues::testOnWatch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test on watch")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Plays the 3-2-1, work, rest, and finish cues on your watch " +
                    "with these settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsSection(title = "Phone sound") {
            ToggleRow(
                title = "Sound cues",
                subtitle = "Beeps when the timer runs on this phone",
                checked = settings.soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
            )
            if (settings.soundEnabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeChip("Low", 50, settings.soundVolume, viewModel::setSoundVolume)
                    VolumeChip("Medium", 85, settings.soundVolume, viewModel::setSoundVolume)
                    VolumeChip("High", 100, settings.soundVolume, viewModel::setSoundVolume)
                }
            }
        }

        if (viewModel.healthConnectAvailable) {
            Spacer(Modifier.height(16.dp))
            SettingsSection(title = "Health Connect") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (viewModel.healthConnectLinked) "Linked" else "Not linked",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Save workouts where other fitness apps can see them",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!viewModel.healthConnectLinked) {
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = {
                            healthConnectLauncher.launch(viewModel.healthConnectPermissions)
                        }) {
                            Text("Link")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSection(title = "Appearance") {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip("System", ThemeMode.SYSTEM, settings.themeMode, viewModel::setThemeMode)
                ThemeChip("Light", ThemeMode.LIGHT, settings.themeMode, viewModel::setThemeMode)
                ThemeChip("Dark", ThemeMode.DARK, settings.themeMode, viewModel::setThemeMode)
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSection(title = "About") {
            Text(
                text = "Interval Timer 0.10",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your templates and workouts live on your devices — no account, " +
                    "no server. Health Connect sharing is optional.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A 5-step slider; the value only commits when the finger lifts. */
@Composable
private fun LevelSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
) {
    var sliderValue by remember(level) { mutableFloatStateOf(level.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onLevelChange(sliderValue.roundToInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${sliderValue.roundToInt()}/5",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VolumeChip(
    label: String,
    volume: Int,
    current: Int,
    onSelect: (Int) -> Unit,
) {
    FilterChip(
        selected = current == volume,
        onClick = { onSelect(volume) },
        label = { Text(label) },
    )
}

@Composable
private fun ThemeChip(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
    )
}
