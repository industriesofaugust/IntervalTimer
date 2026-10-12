package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.QuickBuildConfig
import com.august.intervaltimer.model.buildSession
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.sync.CreateTemplateCodec
import com.august.intervaltimer.sync.CreateTemplateRequest
import com.august.intervaltimer.sync.SyncPaths
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CreateTemplateViewModel(app: Application) : AndroidViewModel(app) {
    private val dataClient = Wearable.getDataClient(app)

    /** Asks the phone to save this as a real template in the library. */
    fun saveToPhone(request: CreateTemplateRequest) {
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
    viewModel: CreateTemplateViewModel = viewModel(),
) {
    var rounds by rememberSaveable { mutableIntStateOf(8) }
    var workSeconds by rememberSaveable { mutableIntStateOf(40) }
    var restSeconds by rememberSaveable { mutableIntStateOf(20) }

    val name = "$rounds× ${formatDuration(workSeconds)}/${formatDuration(restSeconds)}"
    val totalSeconds = rounds * workSeconds + (rounds - 1).coerceAtLeast(0) * restSeconds

    fun intervals() = buildSession(QuickBuildConfig(rounds, workSeconds, restSeconds))

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("New workout") } }
            item {
                ValueStepper(
                    label = "Rounds",
                    value = "$rounds",
                    onDecrease = { rounds = (rounds - 1).coerceAtLeast(1) },
                    onIncrease = { rounds = (rounds + 1).coerceAtMost(50) },
                )
            }
            item {
                ValueStepper(
                    label = "Work",
                    value = formatDuration(workSeconds),
                    onDecrease = { workSeconds = (workSeconds - 5).coerceAtLeast(5) },
                    onIncrease = { workSeconds = (workSeconds + 5).coerceAtMost(600) },
                )
            }
            item {
                ValueStepper(
                    label = "Rest",
                    value = formatDuration(restSeconds),
                    onDecrease = { restSeconds = (restSeconds - 5).coerceAtLeast(5) },
                    onIncrease = { restSeconds = (restSeconds + 5).coerceAtMost(600) },
                )
            }
            item {
                Text(
                    text = "Total ${formatDuration(totalSeconds)}",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Chip(
                    onClick = { onStart(name, intervals()) },
                    label = { Text("Start now") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
            item {
                Chip(
                    onClick = {
                        viewModel.saveToPhone(
                            CreateTemplateRequest(name, rounds, workSeconds, restSeconds)
                        )
                        onStart(name, intervals())
                    },
                    label = { Text("Save & start") },
                    secondaryLabel = { Text("Adds to phone library") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ValueStepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onDecrease,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Less", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.onBackground,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onIncrease,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "More", modifier = Modifier.size(18.dp))
        }
    }
}
