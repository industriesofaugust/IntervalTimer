package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
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
import com.august.intervaltimer.data.intervals
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.ui.components.SessionStrip
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.SectionHeader
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.figureStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.typeColor
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

/**
 * One workout in full. Order, top to bottom: title and date, session strip,
 * six stat tiles, heart-rate chart, steps, delete.
 */
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = viewModel(),
) {
    val result = viewModel.result ?: return
    val series = result.hrSeries()
    val intervals = result.intervals()
    val dial = isDial()

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
                    style = if (dial) DialType.title.copy(fontSize = DialType.exercise.fontSize)
                    else MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = if (dial) formatDialDateTime(result.completedAt)
                    else formatResultDate(result.completedAt),
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(Modifier.padding(horizontal = 24.dp)) {
            if (dial && intervals.isNotEmpty()) {
                // Lit as far as the workout got, grey after that.
                SessionStrip(
                    intervals = intervals,
                    modifier = Modifier.fillMaxWidth(),
                    height = 5.dp,
                    completedCount = result.completedIntervals,
                )
                Spacer(Modifier.height(14.dp))
            }

            val done = result.completedIntervals
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailTile("Time", formatDuration(result.totalSeconds), Modifier.weight(1f))
                DetailTile("Work", formatDuration(result.workSeconds), Modifier.weight(1f))
                DetailTile(
                    label = "Done",
                    value = if (done != null && intervals.isNotEmpty()) "$done/${intervals.size}" else "--",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailTile("Avg", result.avgHeartRate?.roundToInt()?.toString() ?: "--", Modifier.weight(1f), heart = true)
                DetailTile("Max", result.maxHeartRate?.roundToInt()?.toString() ?: "--", Modifier.weight(1f), heart = true)
                DetailTile("Kcal", result.calories?.roundToInt()?.toString() ?: "--", Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Heart rate")
            Spacer(Modifier.height(12.dp))
            if (series.size >= 2) {
                HrChart(
                    series = series,
                    intervals = intervals,
                    totalSeconds = result.totalSeconds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                )
            } else {
                Text(
                    text = "No heart rate was recorded for this workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            result.steps?.let { steps ->
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "$steps steps".labelCase(),
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = { viewModel.delete(onDeleted = onBack) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = "Delete workout".labelCase(),
                    style = if (dial) DialType.label else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailTile(label: String, value: String, modifier: Modifier = Modifier, heart: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.labelCase(),
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (heart) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "heart rate",
                        tint = typeColor(IntervalType.WORK),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = value, style = figureStyle(), maxLines = 1)
        }
    }
}