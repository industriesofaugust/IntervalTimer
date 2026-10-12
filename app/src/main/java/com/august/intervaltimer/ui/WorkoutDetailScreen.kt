package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.WorkoutResultEntity
import com.august.intervaltimer.data.hrSeries
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.ui.theme.Coral
import com.august.intervaltimer.ui.theme.CoralLight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WorkoutDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repository = (app as TimerApp).resultsRepository
    private val resultId: Long = handle.get<Long>("resultId") ?: 0L

    var result by mutableStateOf<WorkoutResultEntity?>(null)
        private set

    init {
        viewModelScope.launch { result = repository.getById(resultId) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteById(resultId)
            onDeleted()
        }
    }
}

@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = viewModel(),
) {
    val result = viewModel.result ?: return
    val series = result.hrSeries()
    val accent = if (isSystemInDarkTheme()) CoralLight else Coral

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 24.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = result.templateName ?: "Workout",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = formatResultDate(result.completedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(Modifier.padding(horizontal = 24.dp)) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DetailRow("Total time", formatDuration(result.totalSeconds))
                    DetailRow("Time working", formatDuration(result.workSeconds))
                    DetailRow("Work intervals", "${result.workIntervals}")
                    result.avgHeartRate?.let {
                        DetailRow("Average heart rate", "${it.roundToInt()} bpm")
                    }
                    result.maxHeartRate?.let {
                        DetailRow("Max heart rate", "${it.roundToInt()} bpm")
                    }
                    result.steps?.let { DetailRow("Steps", "$it") }
                    result.calories?.let { DetailRow("Calories", "${it.roundToInt()} kcal") }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Heart rate",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            if (series.size >= 2) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HrChart(
                        series = series,
                        lineColor = accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(16.dp),
                    )
                }
            } else {
                Text(
                    text = "No heart rate was recorded for this workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = { viewModel.delete(onDeleted = onBack) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Delete workout", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
