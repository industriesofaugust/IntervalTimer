package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import com.august.intervaltimer.haptics.HapticsPlayer
import com.august.intervaltimer.settings.WatchSettings
import com.august.intervaltimer.settings.WatchSettingsRepository
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.sync.WatchCueSettings
import com.august.intervaltimer.sync.WatchCueSettingsCodec
import com.august.intervaltimer.sync.soundVolumePercent
import com.august.intervaltimer.timer.SoundPlayer
import com.august.intervaltimer.timer.TimerCue
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WatchSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = WatchSettingsRepository(app)
    private val dataClient = Wearable.getDataClient(app)
    private val haptics = HapticsPlayer(app)

    val settings = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchSettings())

    fun setVibrationEnabled(enabled: Boolean) =
        updateCues(previewVibration = enabled) { it.copy(vibrationEnabled = enabled) }

    fun adjustVibration(delta: Int) = updateCues(previewVibration = true) {
        it.copy(vibrationLevel = (it.vibrationLevel + delta).coerceIn(1, 5))
    }

    fun setSoundEnabled(enabled: Boolean) =
        updateCues(previewSound = enabled) { it.copy(soundEnabled = enabled) }

    fun adjustSound(delta: Int) = updateCues(previewSound = true) {
        it.copy(soundLevel = (it.soundLevel + delta).coerceIn(1, 5))
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        viewModelScope.launch { repository.setKeepScreenOn(keepOn) }
    }

    private fun updateCues(
        previewVibration: Boolean = false,
        previewSound: Boolean = false,
        transform: (WatchCueSettings) -> WatchCueSettings,
    ) {
        viewModelScope.launch {
            val updated = transform(settings.value.cues)
            repository.setCues(updated)
            publishToPhone(updated)
            // Let the user feel/hear what they just picked.
            if (previewVibration && updated.vibrationEnabled) {
                haptics.play(TimerCue.WORK_START, updated.vibrationLevel)
            }
            if (previewSound && updated.soundEnabled) {
                val player = SoundPlayer(updated.soundVolumePercent)
                player.workStart()
                delay(600)
                player.release()
            }
        }
    }

    /** Mirrors settings to the Data Layer so the phone's Settings page stays in sync. */
    private suspend fun publishToPhone(cues: WatchCueSettings) {
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

@Composable
fun WatchSettingsScreen(viewModel: WatchSettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val cues = settings.cues

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Settings") } }
            item {
                SettingToggle(
                    label = "Vibrate",
                    checked = cues.vibrationEnabled,
                    onCheckedChange = viewModel::setVibrationEnabled,
                )
            }
            if (cues.vibrationEnabled) {
                item {
                    LevelBar(
                        level = cues.vibrationLevel,
                        accent = WorkAccent,
                        onAdjust = viewModel::adjustVibration,
                    )
                }
            }
            item {
                SettingToggle(
                    label = "Sound",
                    checked = cues.soundEnabled,
                    onCheckedChange = viewModel::setSoundEnabled,
                )
            }
            if (cues.soundEnabled) {
                item {
                    LevelBar(
                        level = cues.soundLevel,
                        accent = RestAccent,
                        onAdjust = viewModel::adjustSound,
                    )
                }
            }
            item {
                SettingToggle(
                    label = "Screen on",
                    checked = settings.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
            }
        }
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(label) },
        toggleControl = {
            Switch(checked = checked, enabled = true, onCheckedChange = null)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Five-step level control: minus and plus buttons around a thin bar that
 * fills as the level rises.
 */
@Composable
private fun LevelBar(
    level: Int,
    accent: Color,
    onAdjust: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onAdjust(-1) },
            enabled = level > 1,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Lower", modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(level.coerceIn(1, 5) / 5f)
                    .background(accent),
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onAdjust(+1) },
            enabled = level < 5,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Raise", modifier = Modifier.size(16.dp))
        }
    }
}
