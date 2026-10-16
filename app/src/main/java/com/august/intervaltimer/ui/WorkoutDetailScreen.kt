package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.HealthConnectWriter
import com.august.intervaltimer.data.HealthPrefsRepository
import com.august.intervaltimer.data.WorkoutResultEntity
import com.august.intervaltimer.data.hrSeries
import com.august.intervaltimer.data.intervals
import com.august.intervaltimer.data.timing
import com.august.intervaltimer.health.HeartRateZones
import com.august.intervaltimer.health.timeInZones
import com.august.intervaltimer.health.maxHeartRateFor
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.ui.components.SessionStrip
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.SectionHeader
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.figureStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.typeColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt
class WorkoutDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repository = (app as TimerApp).resultsRepository
    private val resultId: Long = handle.get<Long>("resultId") ?: 0L

    private val healthConnect = HealthConnectWriter(app)
    private val healthPrefs = HealthPrefsRepository(app)

    var result by mutableStateOf<WorkoutResultEntity?>(null)
        private set

    /** Null when there is no maximum to work from, or no heart rate to place in zones. */
    var zones by mutableStateOf<HeartRateZones?>(null)
        private set

    /** Where the zones come from, in words: "MAX 190 FROM AGE · RESTING 58". */
    var zoneSource by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            result = repository.getById(resultId)
            loadZones()
        }
    }

    private suspend fun loadZones() {
        val age = healthPrefs.age.first()
        val highest = repository.highestHeartRate()
        val max = maxHeartRateFor(age, highest)
        val resting = healthConnect.latestRestingHeartRate()
        zones = HeartRateZones.create(max, resting)
        zoneSource = zones?.let { zoneSourceText(it, fromAge = maxHeartRateFor(age, null) != null) } ?: ""
    }

    /** Deletes the workout here and its records in Health Connect. */
    fun delete(onDeleted: () -> Unit) {
        val deleted = result
        viewModelScope.launch {
            repository.deleteById(resultId)
            if (deleted != null) healthConnect.delete(deleted)
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
                val timing = result.timing()
                // The real timing when it was recorded; the plan for older workouts.
                val bands = if (timing.spans.isNotEmpty()) timedBands(timing.spans, intervals)
                else plannedBands(intervals)
                val leadIn = (TimerUiState.LEAD_IN_MILLIS / 1000).toInt()
                val endSeconds = if (timing.spans.isNotEmpty()) {
                    ceil(bands.maxOfOrNull { it.endSeconds } ?: 0f).toInt()
                } else {
                    leadIn + result.totalSeconds
                }
                HrChart(
                    series = series,
                    bands = bands,
                    endSeconds = endSeconds,
                    zones = viewModel.zones,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                )
                viewModel.zones?.let { zones ->
                    Spacer(Modifier.height(20.dp))
                    ZoneBars(
                        seconds = timeInZones(series, zones),
                        source = viewModel.zoneSource,
                    )
                }
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

private fun zoneSourceText(zones: HeartRateZones, fromAge: Boolean): String {
    val max = "Max ${zones.maxHeartRate} " + if (fromAge) "from age" else "highest seen"
    val base = zones.restingHeartRate?.let { "resting $it" } ?: "% of max"
    return "$max · $base"
}

/** Time in each heart-rate zone: five small bars, each with its minutes. */
@Composable
private fun ZoneBars(seconds: List<Int>, source: String) {
    val longest = (seconds.maxOrNull() ?: 0).coerceAtLeast(1)
    val low = typeColor(IntervalType.REST)
    val high = typeColor(IntervalType.WORK)
    SectionHeader("Time in zones")
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seconds.forEachIndexed { index, zoneSeconds ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatDuration(zoneSeconds),
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                // A fixed-height slot, with the bar growing up from its bottom.
                Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((4 + 44 * zoneSeconds / longest.toFloat()).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                lerp(low, high, index / 4f).copy(alpha = if (zoneSeconds == 0) 0.25f else 1f)
                            ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Z${index + 1}",
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = source.labelCase(),
        style = captionStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
