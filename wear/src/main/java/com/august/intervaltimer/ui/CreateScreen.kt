package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.MAX_INTERVAL_SECONDS
import com.august.intervaltimer.model.MAX_ROUNDS
import com.august.intervaltimer.model.MIN_INTERVAL_SECONDS
import com.august.intervaltimer.model.QuickBuildConfig
import com.august.intervaltimer.model.buildSession
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.model.stepDuration
import com.august.intervaltimer.sync.CreateTemplateCodec
import com.august.intervaltimer.sync.CreateTemplateRequest
import com.august.intervaltimer.sync.SyncPaths
import com.august.intervaltimer.ui.components.ButtonIcon
import com.august.intervaltimer.ui.components.Caption
import com.august.intervaltimer.ui.components.ScreenTitle
import com.august.intervaltimer.ui.components.transformedItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalTime

/**
 * What the New screen is editing. It belongs to the New screen's place in the
 * back stack, so the time picker (a screen of its own) can change the values
 * and swiping back from the picker leaves them as they were.
 */
class CreateViewModel(app: Application) : AndroidViewModel(app) {
    private val dataClient = Wearable.getDataClient(app)

    var rounds by mutableIntStateOf(8)
    var workSeconds by mutableIntStateOf(40)
    var restSeconds by mutableIntStateOf(20)
    var saveToPhone by mutableStateOf(true)

    /** Asks the phone to save this as a real template in the library. */
    fun sendToPhone(request: CreateTemplateRequest) {
        viewModelScope.launch {
            runCatching {
                val put = PutDataMapRequest
                    .create("${SyncPaths.CREATE_TEMPLATE_PATH_PREFIX}/${System.currentTimeMillis()}")
                    .apply {
                        dataMap.putString(
                            SyncPaths.CREATE_TEMPLATE_KEY,
                            CreateTemplateCodec.encode(request),
                        )
                    }
                    .asPutDataRequest()
                    .setUrgent()
                dataClient.putDataItem(put).await()
            }
        }
    }
}

/** Build a basic session on the watch: rounds × (work, rest). */
@Composable
fun CreateScreen(
    onStart: (name: String, intervals: List<Interval>) -> Unit,
    onPickWork: () -> Unit,
    onPickRest: () -> Unit,
    viewModel: CreateViewModel = viewModel(),
) {
    val columnState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    val rounds = viewModel.rounds
    val work = viewModel.workSeconds
    val rest = viewModel.restSeconds

    val name = remember(rounds, work, rest) {
        "$rounds× ${formatDuration(work)}/${formatDuration(rest)}"
    }
    val total = remember(rounds, work, rest) {
        formatDuration(rounds * work + (rounds - 1).coerceAtLeast(0) * rest)
    }

    ScreenScaffold(
        scrollState = columnState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    if (viewModel.saveToPhone) {
                        viewModel.sendToPhone(CreateTemplateRequest(name, rounds, work, rest))
                    }
                    onStart(name, buildSession(QuickBuildConfig(rounds, work, rest)))
                },
                buttonSize = EdgeButtonSize.Medium,
            ) {
                Text("START")
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
            item(key = "title") { ScreenTitle("NEW WORKOUT", spec) }
            item(key = "rounds") {
                StepperRow(
                    label = "ROUNDS",
                    value = "$rounds",
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    spec = spec,
                    onDecrease = { viewModel.rounds = (rounds - 1).coerceAtLeast(1) },
                    onIncrease = { viewModel.rounds = (rounds + 1).coerceAtMost(MAX_ROUNDS) },
                )
            }
            item(key = "work") {
                StepperRow(
                    label = "WORK",
                    value = formatDuration(work),
                    labelColor = WorkAccent,
                    spec = spec,
                    onDecrease = { viewModel.workSeconds = stepDuration(work, -5) },
                    onIncrease = { viewModel.workSeconds = stepDuration(work, 5) },
                    onValueClick = onPickWork,
                )
            }
            item(key = "rest") {
                StepperRow(
                    label = "REST",
                    value = formatDuration(rest),
                    labelColor = RestAccent,
                    spec = spec,
                    onDecrease = { viewModel.restSeconds = stepDuration(rest, -5) },
                    onIncrease = { viewModel.restSeconds = stepDuration(rest, 5) },
                    onValueClick = onPickRest,
                )
            }
            item(key = "total") { Caption("TOTAL $total", spec) }
            item(key = "save") {
                SwitchButton(
                    checked = viewModel.saveToPhone,
                    onCheckedChange = { viewModel.saveToPhone = it },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text("SAVE TO PHONE")
                }
            }
        }
    }
}

/** A minus button, a label with its value, and a plus button. */
@Composable
private fun TransformingLazyColumnItemScope.StepperRow(
    label: String,
    value: String,
    labelColor: Color,
    spec: TransformationSpec,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onValueClick: (() -> Unit)? = null,
) {
    val buttonSize = IconButtonDefaults.SmallButtonSize
    Row(
        modifier = Modifier.fillMaxWidth().transformedItem(this, spec),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilledTonalIconButton(onClick = onDecrease, modifier = Modifier.size(buttonSize)) {
            ButtonIcon(Icons.Filled.Remove, "Less $label", buttonSize)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(enabled = onValueClick != null) { onValueClick?.invoke() }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text(text = value, style = MaterialTheme.typography.numeralExtraSmall)
        }
        FilledTonalIconButton(onClick = onIncrease, modifier = Modifier.size(buttonSize)) {
            ButtonIcon(Icons.Filled.Add, "More $label", buttonSize)
        }
    }
}

/**
 * Minutes and seconds wheels for setting an exact duration such as 0:07.
 * Picking one hands the seconds back; swiping away picks nothing.
 */
@Composable
fun DurationPickerScreen(initialSeconds: Int, onPicked: (Int) -> Unit) {
    val initialTime = remember(initialSeconds) {
        LocalTime.of(0, (initialSeconds / 60).coerceIn(0, 59), initialSeconds % 60)
    }
    TimePicker(
        initialTime = initialTime,
        onTimePicked = { time ->
            val seconds = time.minute * 60 + time.second
            onPicked(seconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS))
        },
        timePickerType = TimePickerType.MinutesSeconds,
    )
}
