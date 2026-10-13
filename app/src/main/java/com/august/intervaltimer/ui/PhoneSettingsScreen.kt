package com.august.intervaltimer.ui

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.HealthConnectWriter
import com.august.intervaltimer.data.ThemeMode
import com.august.intervaltimer.design.AppStyle
import com.august.intervaltimer.design.CountdownFormat
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.sync.WatchCueSettings
import com.august.intervaltimer.sync.WatchCueSettingsCodec
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.PageTitle
import com.august.intervaltimer.ui.theme.SectionHeader
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.typeColor
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

    fun setStyle(style: AppStyle) {
        viewModelScope.launch { repository.setStyle(style) }
    }

    fun setCountdownFormat(format: CountdownFormat) {
        viewModelScope.launch { repository.setCountdownFormat(format) }
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
        PageTitle("Settings")
        Spacer(Modifier.height(24.dp))

        SectionHeader("Style")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StyleOption(
                label = "Dial",
                segmented = true,
                selected = settings.style == AppStyle.DIAL,
                onClick = { viewModel.setStyle(AppStyle.DIAL) },
                modifier = Modifier.weight(1f),
            )
            StyleOption(
                label = "Minimal",
                segmented = false,
                selected = settings.style == AppStyle.MINIMAL,
                onClick = { viewModel.setStyle(AppStyle.MINIMAL) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("Countdown")
        Spacer(Modifier.height(8.dp))
        SegmentedChoice(
            options = listOf("Seconds" to "90", "Minutes" to "1:30"),
            selectedIndex = if (settings.countdownFormat == CountdownFormat.SECONDS) 0 else 1,
            onSelect = { index ->
                viewModel.setCountdownFormat(
                    if (index == 0) CountdownFormat.SECONDS else CountdownFormat.MINUTES_SECONDS
                )
            },
        )

        Spacer(Modifier.height(24.dp))
        SectionHeader("Watch cues")
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            if (!watchCues.loaded) {
                Text(
                    text = "Open Interval Timer on your watch once to load its settings here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ToggleRow(
                    title = "Vibration",
                    checked = watchCues.cues.vibrationEnabled,
                    onCheckedChange = { on -> watchCues.update { it.copy(vibrationEnabled = on) } },
                )
                if (watchCues.cues.vibrationEnabled) {
                    LevelBar(
                        level = watchCues.cues.vibrationLevel,
                        color = typeColor(IntervalType.WORK),
                        onLevelChange = { level -> watchCues.update { it.copy(vibrationLevel = level) } },
                    )
                }
                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    title = "Sound",
                    checked = watchCues.cues.soundEnabled,
                    onCheckedChange = { on -> watchCues.update { it.copy(soundEnabled = on) } },
                )
                if (watchCues.cues.soundEnabled) {
                    LevelBar(
                        level = watchCues.cues.soundLevel,
                        color = typeColor(IntervalType.REST),
                        onLevelChange = { level -> watchCues.update { it.copy(soundLevel = level) } },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = watchCues::testOnWatch,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Test on watch".labelCase(),
                    style = if (isDial()) DialType.label else MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("Phone sound")
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            ToggleRow(
                title = "Sound cues",
                checked = settings.soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
            )
            if (settings.soundEnabled) {
                Spacer(Modifier.height(8.dp))
                SegmentedChoice(
                    options = listOf("Low" to null, "Medium" to null, "High" to null),
                    selectedIndex = when {
                        settings.soundVolume <= 50 -> 0
                        settings.soundVolume <= 85 -> 1
                        else -> 2
                    },
                    onSelect = { index -> viewModel.setSoundVolume(listOf(50, 85, 100)[index]) },
                )
            }
        }

        if (viewModel.healthConnectAvailable) {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Health Connect")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = (if (viewModel.healthConnectLinked) "Linked" else "Not linked").labelCase(),
                            style = if (isDial()) DialType.label else MaterialTheme.typography.titleMedium,
                            color = if (viewModel.healthConnectLinked) typeColor(IntervalType.REST)
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
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
                            Text("Link".labelCase())
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("Theme")
        Spacer(Modifier.height(8.dp))
        SegmentedChoice(
            options = listOf("System" to null, "Light" to null, "Dark" to null),
            selectedIndex = settings.themeMode.ordinal,
            onSelect = { index -> viewModel.setThemeMode(ThemeMode.entries[index]) },
        )

        Spacer(Modifier.height(24.dp))
        SectionHeader("About")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Interval Timer 0.12. Your templates and workouts live on your devices: " +
                "no account, no server. Health Connect sharing is optional.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title.labelCase(),
            style = if (isDial()) DialType.label else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Five tappable steps, filled up to the current level. Matches the bar on the watch. */
@Composable
private fun LevelBar(level: Int, color: Color, onLevelChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (step in 1..5) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clickable { onLevelChange(step) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(if (step <= level) color else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

/** A row of choices where one is selected, like a tab strip. */
@Composable
private fun SegmentedChoice(
    options: List<Pair<String, String?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, (label, example) ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.outline else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label.labelCase(),
                    style = if (isDial()) DialType.label else MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (example != null) {
                    Text(
                        text = example,
                        style = captionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One of the two style choices, with a small picture of its ring. */
@Composable
private fun StyleOption(
    label: String,
    segmented: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val work = typeColor(IntervalType.WORK)
    val rest = typeColor(IntervalType.REST)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(modifier = Modifier.size(44.dp)) {
                val stroke = 6.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                if (segmented) {
                    // Five arcs with gaps: work, rest, work, rest, work.
                    val sweeps = listOf(88f, 44f, 88f, 44f, 76f)
                    var start = -88f
                    sweeps.forEachIndexed { index, sweep ->
                        drawArc(
                            color = if (index % 2 == 0) work else rest,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke),
                        )
                        start += sweep + 4f
                    }
                } else {
                    drawArc(
                        color = work,
                        startAngle = -90f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = label.labelCase(),
                style = if (isDial()) DialType.label else MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}