package com.august.intervaltimer.ui

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.PrivacyPolicyActivity
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.ThemeMode
import com.august.intervaltimer.data.WatchLinkMode
import com.august.intervaltimer.data.WhenWatchJoins
import com.august.intervaltimer.data.soundVolumeFor
import com.august.intervaltimer.design.AppStyle
import com.august.intervaltimer.design.CountdownFormat
import com.august.intervaltimer.haptics.HapticsPlayer
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.sync.WatchCueSettings
import com.august.intervaltimer.sync.WatchCueSettingsCodec
import com.august.intervaltimer.timer.SoundPlayer
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.ui.settings.DevicesCard
import com.august.intervaltimer.ui.settings.HealthCard
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PhoneSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as TimerApp).settingsRepository
    private val haptics = HapticsPlayer(app)
    private var testJob: Job? = null
    private var beepJob: Job? = null

    val settings = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
        if (enabled) previewBeep(settings.value.soundLevel)
    }

    fun setSoundLevel(level: Int) {
        viewModelScope.launch { repository.setSoundLevel(level) }
        previewBeep(level)
    }

    /** Lets the user hear what they just picked. */
    private fun previewBeep(level: Int) {
        beepJob?.cancel()
        beepJob = viewModelScope.launch {
            val sound = SoundPlayer(soundVolumeFor(level))
            try {
                sound.workStart()
                delay(600)
            } finally {
                sound.release()
            }
        }
    }

    fun setVibrate(enabled: Boolean) {
        viewModelScope.launch { repository.setVibrate(enabled) }
        // Let the user feel what they just switched on.
        if (enabled) haptics.play(TimerCue.WORK_START, settings.value.vibrationLevel)
    }

    fun setVibrationLevel(level: Int) {
        viewModelScope.launch { repository.setVibrationLevel(level) }
        haptics.play(TimerCue.WORK_START, level)
    }

    /** Plays 3-2-1, work, rest, finish with the current phone settings. */
    fun testCues() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            val current = settings.value
            val sound = if (current.soundEnabled) SoundPlayer(current.soundVolume) else null
            try {
                fun cue(cue: TimerCue) {
                    if (current.vibrate) haptics.play(cue, current.vibrationLevel)
                    when (cue) {
                        TimerCue.COUNTDOWN_TICK -> sound?.countdownTick()
                        TimerCue.WORK_START -> sound?.workStart()
                        TimerCue.REST_START -> sound?.restStart()
                        TimerCue.FINISH -> sound?.finish()
                    }
                }

                repeat(3) {
                    cue(TimerCue.COUNTDOWN_TICK)
                    delay(700)
                }
                cue(TimerCue.WORK_START)
                delay(1300)
                cue(TimerCue.REST_START)
                delay(1300)
                cue(TimerCue.FINISH)
                delay(900)
            } finally {
                sound?.release()
            }
        }
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

    fun setWatchLink(mode: WatchLinkMode) {
        viewModelScope.launch { repository.setWatchLink(mode) }
    }

    fun setWhenWatchJoins(choice: WhenWatchJoins) {
        viewModelScope.launch { repository.setWhenWatchJoins(choice) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        PageTitle("Settings")
        Spacer(Modifier.height(24.dp))

        DevicesCard(
            watchLink = settings.watchLink,
            onWatchLinkChange = viewModel::setWatchLink,
            watchCues = watchCues,
        )

        Spacer(Modifier.height(24.dp))
        SectionHeader("Phone cues")
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            CueRow(
                title = "Sound",
                checked = settings.soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
                level = settings.soundLevel,
                color = typeColor(IntervalType.REST),
                onLevelChange = viewModel::setSoundLevel,
            )
            Spacer(Modifier.height(8.dp))
            CueRow(
                title = "Vibration",
                checked = settings.vibrate,
                onCheckedChange = viewModel::setVibrate,
                level = settings.vibrationLevel,
                color = typeColor(IntervalType.WORK),
                onLevelChange = viewModel::setVibrationLevel,
            )
            if (settings.watchLink != WatchLinkMode.OFF) {
                Spacer(Modifier.height(16.dp))
                LabeledChoice(
                    label = "When watch joins",
                    options = listOf("Quiet", "Sound", "Both"),
                    selectedIndex = settings.whenWatchJoins.ordinal,
                    onSelect = { index ->
                        viewModel.setWhenWatchJoins(WhenWatchJoins.entries[index])
                    },
                    caption = "Phone cues while the watch runs the workout with you",
                    labelOnTop = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            TestButton("Test cues", onClick = viewModel::testCues)
        }

        HealthCard()

        Spacer(Modifier.height(24.dp))
        SectionHeader("Appearance")
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            LabeledChoice(
                label = "Style",
                options = listOf("Dial", "Minimal"),
                selectedIndex = if (settings.style == AppStyle.DIAL) 0 else 1,
                onSelect = { index ->
                    viewModel.setStyle(if (index == 0) AppStyle.DIAL else AppStyle.MINIMAL)
                },
            )
            Spacer(Modifier.height(16.dp))
            LabeledChoice(
                label = "Countdown",
                options = listOf("90", "1:30"),
                selectedIndex = if (settings.countdownFormat == CountdownFormat.SECONDS) 0 else 1,
                onSelect = { index ->
                    viewModel.setCountdownFormat(
                        if (index == 0) CountdownFormat.SECONDS else CountdownFormat.MINUTES_SECONDS
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            LabeledChoice(
                label = "Theme",
                options = listOf("System", "Light", "Dark"),
                selectedIndex = settings.themeMode.ordinal,
                onSelect = { index -> viewModel.setThemeMode(ThemeMode.entries[index]) },
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("About")
        Spacer(Modifier.height(8.dp))
        AboutCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Interval Timer $version".labelCase(),
                style = if (isDial()) DialType.label else MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            SmallTextButton(
                label = "Privacy policy",
                onClick = { context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)) },
            )
        }
        Text(
            text = "No account, no server: your data stays on your devices.",
            style = captionStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
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

/** A label, a switch, and (when on) the five-step bar between them. */
@Composable
internal fun CueRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    level: Int,
    color: Color,
    onLevelChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title.labelCase(),
            style = if (isDial()) DialType.label else MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(LabelWidth),
        )
        if (checked) {
            LevelBar(
                level = level,
                color = color,
                onLevelChange = onLevelChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A small button for the actions at the foot of a card, like TEST CUES. */
@Composable
internal fun SmallTextButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = label.labelCase(),
            style = if (isDial()) DialType.label else MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun TestButton(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        SmallTextButton(label, onClick)
    }
}

/** Five tappable steps, filled up to the current level. Matches the bar on the watch. */
@Composable
internal fun LevelBar(
    level: Int,
    color: Color,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

/** Width of the label column, so the controls of different rows line up. */
internal val LabelWidth = 96.dp

/**
 * A label with a segmented choice beside it (or above it, for a long label)
 * and an optional one-line caption explaining the choice.
 */
@Composable
internal fun LabeledChoice(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    caption: String? = null,
    labelOnTop: Boolean = false,
) {
    val labelStyle = if (isDial()) DialType.label else MaterialTheme.typography.titleMedium
    val choices = options.map { it to null }
    if (labelOnTop) {
        Text(text = label.labelCase(), style = labelStyle)
        Spacer(Modifier.height(8.dp))
        SegmentedChoice(choices, selectedIndex, onSelect)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label.labelCase(), style = labelStyle, modifier = Modifier.width(LabelWidth))
            SegmentedChoice(choices, selectedIndex, onSelect, Modifier.weight(1f))
        }
    }
    if (caption != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = caption,
            style = captionStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A row of choices where one is selected, like a tab strip. */
@Composable
internal fun SegmentedChoice(
    options: List<Pair<String, String?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
