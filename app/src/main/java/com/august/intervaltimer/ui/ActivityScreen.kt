package com.august.intervaltimer.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.HealthConnectWriter
import com.august.intervaltimer.data.WorkoutResultEntity
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.PageTitle
import com.august.intervaltimer.ui.theme.SectionHeader
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ActivityViewModel(app: Application) : AndroidViewModel(app) {
    private val resultsRepository = (app as TimerApp).resultsRepository
    private val healthConnect = HealthConnectWriter(app)

    // Workouts the user deleted that can still be undone: hidden, not yet removed.
    private val pendingIds = MutableStateFlow<Set<Long>>(emptySet())

    val results = combine(resultsRepository.observeAll(), pendingIds) { all, pending ->
        all.filter { it.id !in pending }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun hide(ids: Set<Long>) {
        pendingIds.value += ids
    }

    /** Undo: the workouts come back and nothing was touched. */
    fun restore(ids: Set<Long>) {
        pendingIds.value -= ids
    }

    /** The same delete as the detail page: the workout here, then its records in Health Connect. */
    fun deleteNow(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { id ->
                val result = resultsRepository.getById(id)
                resultsRepository.deleteById(id)
                if (result != null) healthConnect.delete(result)
            }
            pendingIds.value -= ids
        }
    }
}

/** The span of time the first tile and the bar chart cover. */
private enum class ActivityRange(val label: String) {
    TODAY("Today"),
    WEEK("This week"),
    MONTH("This month"),
}

private fun WorkoutResultEntity.localDate(): LocalDate =
    Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** "24:10" under an hour, "1:42:10" above. */
private fun formatLongDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

@Composable
fun ActivityScreen(
    onOpenWorkout: (Long) -> Unit,
    viewModel: ActivityViewModel = viewModel(),
) {
    val results by viewModel.results.collectAsState()
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = "Undo".labelCase()

    // Leaves selection mode before the app goes back a page.
    BackHandler(enabled = selecting) { selected = emptySet() }

    // Hides the workouts now; they are only deleted once the snackbar runs out or is dismissed.
    fun deleteWithUndo(ids: Set<Long>) {
        selected = selected - ids
        viewModel.hide(ids)
        val message = if (ids.size == 1) "Workout deleted" else "${ids.size} workouts deleted"
        scope.launch {
            // Dismissing an older snackbar finishes its delete.
            snackbarHost.currentSnackbarData?.dismiss()
            var undone = false
            try {
                val answer = withTimeoutOrNull(5_000) {
                    snackbarHost.showSnackbar(message, undoLabel, duration = SnackbarDuration.Indefinite)
                }
                undone = answer == SnackbarResult.ActionPerformed
            } finally {
                // Also runs when the screen is left, so a pending delete is never forgotten.
                if (undone) viewModel.restore(ids) else viewModel.deleteNow(ids)
            }
        }
    }

    var rangeIndex by rememberSaveable { mutableIntStateOf(ActivityRange.WEEK.ordinal) }
    val range = ActivityRange.entries[rangeIndex]

    val today = LocalDate.now()
    val weekStart = today.with(DayOfWeek.MONDAY)
    val monthStart = today.withDayOfMonth(1)
    val inRange = results.filter {
        val date = it.localDate()
        when (range) {
            ActivityRange.TODAY -> date == today
            ActivityRange.WEEK -> !date.isBefore(weekStart)
            ActivityRange.MONTH -> !date.isBefore(monthStart)
        }
    }

    // One bar per workout today, per weekday, or per day of the month.
    val bars: List<Int> = when (range) {
        ActivityRange.TODAY -> inRange.sortedBy { it.completedAt }.map { it.totalSeconds }
        ActivityRange.WEEK -> (0..6).map { offset ->
            inRange.filter { it.localDate() == weekStart.plusDays(offset.toLong()) }
                .sumOf { it.totalSeconds }
        }
        ActivityRange.MONTH -> (1..today.lengthOfMonth()).map { day ->
            inRange.filter { it.localDate().dayOfMonth == day }.sumOf { it.totalSeconds }
        }
    }
    val todayBar: Int? = when (range) {
        ActivityRange.TODAY -> null
        ActivityRange.WEEK -> today.dayOfWeek.value - 1
        ActivityRange.MONTH -> today.dayOfMonth - 1
    }

    val latest = results.maxByOrNull { it.completedAt }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Spacer(Modifier.height(12.dp))
                    if (selecting) {
                        SelectionHeader(
                            count = selected.size,
                            onDelete = { deleteWithUndo(selected) },
                            onCancel = { selected = emptySet() },
                        )
                    } else {
                        PageTitle("Activity")
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Tap to step through today, this week, this month.
                        StatTile(
                            title = range.label,
                            figure = "${inRange.size}",
                            footer = formatLongDuration(inRange.sumOf { it.totalSeconds }),
                            modifier = Modifier.weight(1f),
                            highlighted = true,
                            pagerPosition = rangeIndex,
                            pagerCount = ActivityRange.entries.size,
                            onClick = { rangeIndex = (rangeIndex + 1) % ActivityRange.entries.size },
                        )
                        StatTile(
                            title = "Days off",
                            figure = latest?.let {
                                "${ChronoUnit.DAYS.between(it.localDate(), today)}"
                            } ?: "--",
                            footer = latest?.let { formatDialDate(it.completedAt) } ?: "--",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    if (range == ActivityRange.TODAY && bars.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No workouts today".labelCase(),
                                style = captionStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        ActivityBars(
                            values = bars,
                            todayIndex = todayBar,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    BarLabels(range = range, today = today, barCount = bars.size)
                }
            }

            item {
                SectionHeader("History", modifier = Modifier.padding(top = 8.dp))
            }

            if (results.isEmpty()) {
                item {
                    Text(
                        text = "No workouts yet. Finish a session and it will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // History always lists everything; the range only drives the tile and chart.
                items(results, key = { it.id }) { result ->
                    // Swiping only works in selection mode, so the pager keeps its swipes otherwise.
                    SwipeToDeleteBox(
                        enabled = selecting,
                        onDelete = { deleteWithUndo(setOf(result.id)) },
                        modifier = Modifier.animateItem(),
                    ) {
                        ResultCard(
                            result = result,
                            selecting = selecting,
                            selected = result.id in selected,
                            onClick = {
                                if (selecting) selected = selected.toggled(result.id)
                                else onOpenWorkout(result.id)
                            },
                            onLongClick = { if (!selecting) selected = setOf(result.id) },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

private fun Set<Long>.toggled(id: Long): Set<Long> = if (id in this) this - id else this + id

/** Takes the place of the page title in selection mode. */
@Composable
private fun SelectionHeader(count: Int, onDelete: () -> Unit, onCancel: () -> Unit) {
    val dial = isDial()
    val buttonStyle = if (dial) DialType.label else MaterialTheme.typography.labelLarge
    val buttonPadding = PaddingValues(horizontal = 8.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$count selected".labelCase(),
            style = if (dial) DialType.title.copy(fontSize = 22.sp) else MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDelete, contentPadding = buttonPadding) {
            Text("Delete".labelCase(), style = buttonStyle, color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onCancel, contentPadding = buttonPadding) {
            Text("Cancel".labelCase(), style = buttonStyle)
        }
    }
}

/** Swipe left to delete, with a DELETE panel revealed behind the card. */
@Composable
private fun SwipeToDeleteBox(
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = enabled,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Delete".labelCase(),
                        style = if (isDial()) DialType.label else MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        },
    ) {
        content()
    }
}

@Composable
private fun StatTile(
    title: String,
    figure: String,
    footer: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    pagerPosition: Int = 0,
    pagerCount: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val dial = isDial()
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (highlighted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title.labelCase(),
                style = if (dial) DialType.label else MaterialTheme.typography.bodyMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = figure,
                style = if (dial) DialType.figureLarge else MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = footer,
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Dots showing which range is selected.
                repeat(pagerCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(5.dp)
                            .background(
                                color = if (index == pagerPosition) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/** Training time as bars. Empty slots get a short stub so the row keeps its rhythm. */
@Composable
private fun ActivityBars(values: List<Int>, todayIndex: Int?, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val stubColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        val peak = values.max().coerceAtLeast(1)
        val stub = 2.dp.toPx()

        values.forEachIndexed { index, value ->
            val left = slot * index + (slot - barWidth) / 2f
            if (value > 0) {
                val height = (size.height * value / peak).coerceAtLeast(4.dp.toPx())
                drawRect(barColor, Offset(left, size.height - height), Size(barWidth, height))
            } else {
                drawRect(stubColor, Offset(left, size.height - stub), Size(barWidth, stub))
            }
        }

        // A dotted line marks today.
        if (todayIndex != null && todayIndex in values.indices && values.size > 7) {
            val x = slot * todayIndex + slot / 2f
            drawLine(
                color = markerColor,
                start = Offset(x + barWidth, 0f),
                end = Offset(x + barWidth, size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
            )
        }
    }
}

@Composable
private fun BarLabels(range: ActivityRange, today: LocalDate, barCount: Int) {
    val style = captionStyle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (range) {
        ActivityRange.TODAY -> if (barCount > 0) {
            Text(
                text = (if (barCount == 1) "1 workout today" else "$barCount workouts today").labelCase(),
                style = style,
                color = muted,
            )
        }
        ActivityRange.WEEK -> Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = day,
                        style = style,
                        color = if (index == today.dayOfWeek.value - 1) MaterialTheme.colorScheme.primary
                        else muted,
                    )
                }
            }
        }
        ActivityRange.MONTH -> Row(Modifier.fillMaxWidth()) {
            Text(text = "1", style = style, color = muted)
            Spacer(Modifier.weight(1f))
            Text(
                text = "Today ${today.dayOfMonth}".labelCase(),
                style = style,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(text = "${today.lengthOfMonth()}", style = style, color = muted)
        }
    }
}
