package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.august.intervaltimer.design.AppStyle
import com.august.intervaltimer.design.CountdownFormat
import com.august.intervaltimer.haptics.HapticsPlayer
import com.august.intervaltimer.settings.WatchSettings
import com.august.intervaltimer.settings.WatchStores
import com.august.intervaltimer.settings.rememberWatchSettings
import com.august.intervaltimer.sync.WatchCueSettings
import com.august.intervaltimer.sync.publishWatchCues
import com.august.intervaltimer.sync.soundVolumePercent
import com.august.intervaltimer.timer.SoundPlayer
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.ui.components.ScreenTitle
import com.august.intervaltimer.ui.components.transformedItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WatchSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = WatchStores.repository
    private val haptics = HapticsPlayer(app)

    // The settings are loaded before any screen is shown.
    private val current: WatchSettings get() = WatchStores.settings.value ?: WatchSettings()

    fun setVibrationEnabled(enabled: Boolean) =
        updateCues(previewVibration = enabled) { it.copy(vibrationEnabled = enabled) }

    fun setVibrationLevel(level: Int) = updateCues(previewVibration = true) {
        it.copy(vibrationLevel = level.coerceIn(1, 5))
    }

    fun setSoundEnabled(enabled: Boolean) =
        updateCues(previewSound = enabled) { it.copy(soundEnabled = enabled) }

    fun setSoundLevel(level: Int) = updateCues(previewSound = true) {
        it.copy(soundLevel = level.coerceIn(1, 5))
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        viewModelScope.launch { repository.setKeepScreenOn(keepOn) }
    }

    fun toggleCountdownFormat() {
        val next = if (current.countdownFormat == CountdownFormat.SECONDS) {
            CountdownFormat.MINUTES_SECONDS
        } else {
            CountdownFormat.SECONDS
        }
        viewModelScope.launch { repository.setCountdownFormat(next) }
    }

    fun toggleStyle() {
        val next = if (current.style == AppStyle.DIAL) AppStyle.MINIMAL else AppStyle.DIAL
        viewModelScope.launch { repository.setStyle(next) }
    }

    private fun updateCues(
        previewVibration: Boolean = false,
        previewSound: Boolean = false,
        transform: (WatchCueSettings) -> WatchCueSettings,
    ) {
        viewModelScope.launch {
            val updated = transform(current.cues)
            repository.setCues(updated)
            publishWatchCues(getApplication(), updated)
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
}

@Composable
fun WatchSettingsScreen(viewModel: WatchSettingsViewModel = viewModel()) {
    val columnState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val settingsState: State<WatchSettings> = rememberWatchSettings()
    val settings = settingsState.value
    val cues = settings.cues

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
            item(key = "title") { ScreenTitle("SETTINGS", spec) }

            item(key = "vibrate") {
                SettingSwitch("VIBRATE", cues.vibrationEnabled, spec, viewModel::setVibrationEnabled)
            }
            if (cues.vibrationEnabled) {
                item(key = "vibrate-level") {
                    LevelSlider(cues.vibrationLevel, spec, viewModel::setVibrationLevel)
                }
            }

            item(key = "sound") {
                SettingSwitch("SOUND", cues.soundEnabled, spec, viewModel::setSoundEnabled)
            }
            if (cues.soundEnabled) {
                item(key = "sound-level") {
                    LevelSlider(cues.soundLevel, spec, viewModel::setSoundLevel)
                }
            }

            item(key = "screen-on") {
                SettingSwitch("KEEP SCREEN ON", settings.keepScreenOn, spec, viewModel::setKeepScreenOn)
            }
            item(key = "countdown") {
                SettingChoice(
                    label = "COUNTDOWN",
                    choice = if (settings.countdownFormat == CountdownFormat.SECONDS) {
                        "Seconds (90)"
                    } else {
                        "Minutes (1:30)"
                    },
                    spec = spec,
                    onClick = viewModel::toggleCountdownFormat,
                )
            }
            item(key = "face") {
                SettingChoice(
                    label = "TIMER FACE",
                    choice = if (settings.style == AppStyle.DIAL) "Dial" else "Minimal",
                    spec = spec,
                    onClick = viewModel::toggleStyle,
                )
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.SettingSwitch(
    label: String,
    checked: Boolean,
    spec: TransformationSpec,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(label)
    }
}

/** A button that steps through a short list of choices each time it is tapped. */
@Composable
private fun TransformingLazyColumnItemScope.SettingChoice(
    label: String,
    choice: String,
    spec: TransformationSpec,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        secondaryLabel = { Text(choice) },
        transformation = SurfaceTransformation(spec),
    ) {
        Text(label, maxLines = 1)
    }
}

/** Five steps, from subtle (1) to maximum (5). */
@Composable
private fun TransformingLazyColumnItemScope.LevelSlider(
    level: Int,
    spec: TransformationSpec,
    onLevelChange: (Int) -> Unit,
) {
    Slider(
        value = level,
        onValueChange = onLevelChange,
        valueProgression = 1..5,
        segmented = true,
        modifier = Modifier.fillMaxWidth().transformedItem(this, spec),
    )
}
