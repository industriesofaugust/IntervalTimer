package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.TemplateWithIntervals
import com.august.intervaltimer.data.toDomainIntervals
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as TimerApp).templatesRepository

    val templates = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { repository.deleteTemplate(id) }
    }

    fun applyOrder(orderedIds: List<Long>) {
        viewModelScope.launch { repository.applyOrder(orderedIds) }
    }
}

@Composable
fun LibraryScreen(
    onQuickSession: () -> Unit,
    onNewTemplate: () -> Unit,
    onEditTemplate: (Long) -> Unit,
    onPlayTemplate: (String, List<Interval>) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val templates by viewModel.templates.collectAsState()

    // Drag state: while a card is held, we reorder a local copy of the list
    // and only save when the finger lifts.
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOrder by remember { mutableStateOf<List<TemplateWithIntervals>?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var draggedCardTop by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    var trashBounds by remember { mutableStateOf<Rect?>(null) }
    var overTrash by remember { mutableStateOf(false) }
    val gapPx = with(LocalDensity.current) { 12.dp.toPx() }

    val displayTemplates = dragOrder ?: templates

    fun endDrag() {
        val id = draggingId
        val order = dragOrder
        if (id != null) {
            if (overTrash) {
                viewModel.deleteTemplate(id)
            } else if (order != null) {
                viewModel.applyOrder(order.map { it.template.id })
            }
        }
        draggingId = null
        dragOrder = null
        dragOffsetY = 0f
        overTrash = false
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Spacer(Modifier.height(40.dp))
                    Text(
                        text = "Interval timer",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Pick a template, or run a quick session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onQuickSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Quick session", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Templates",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onNewTemplate) {
                            Text("+ New")
                        }
                    }
                    if (templates.size > 1) {
                        Text(
                            text = "Hold and drag to reorder or delete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (templates.isEmpty()) {
                item {
                    Text(
                        text = "Nothing saved yet. Build a template once, reuse it every workout.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(displayTemplates, key = { "template-${it.template.id}" }) { template ->
                    val id = template.template.id
                    val isDragging = draggingId == id
                    TemplateCard(
                        template = template,
                        onEdit = { onEditTemplate(id) },
                        onPlay = onPlayTemplate,
                        highlighted = isDragging,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                            }
                            .onGloballyPositioned { coordinates ->
                                if (isDragging) {
                                    draggedCardTop = coordinates.boundsInRoot().top
                                    cardHeightPx = coordinates.size.height.toFloat()
                                }
                            }
                            .then(
                                if (!isDragging) Modifier.animateItem() else Modifier
                            )
                            .pointerInputReorder(
                                id = id,
                                onDragStart = {
                                    draggingId = id
                                    dragOrder = templates
                                    dragOffsetY = 0f
                                },
                                onDrag = { deltaY ->
                                    dragOffsetY += deltaY
                                    val order = dragOrder ?: return@pointerInputReorder
                                    val from = order.indexOfFirst { it.template.id == id }
                                    if (from < 0) return@pointerInputReorder
                                    val slot = cardHeightPx + gapPx
                                    if (dragOffsetY > slot * 0.6f && from < order.lastIndex) {
                                        dragOrder = order.swap(from, from + 1)
                                        dragOffsetY -= slot
                                    } else if (dragOffsetY < -slot * 0.6f && from > 0) {
                                        dragOrder = order.swap(from, from - 1)
                                        dragOffsetY += slot
                                    }
                                    val cardCenter = draggedCardTop + dragOffsetY + cardHeightPx / 2
                                    overTrash = trashBounds?.let { cardCenter > it.top } ?: false
                                },
                                onDragEnd = { endDrag() },
                            ),
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }

        // The trash target appears only while something is being dragged.
        if (draggingId != null) {
            Surface(
                color = if (overTrash) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .onGloballyPositioned { trashBounds = it.boundsInRoot() },
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (overTrash) "Release to delete" else "Drag here to delete",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (overTrash) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Long-press starts the drag; vertical movement is reported in pixels. */
private fun Modifier.pointerInputReorder(
    id: Long,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(id) {
    detectDragGesturesAfterLongPress(
        onDragStart = { onDragStart() },
        onDrag = { change, dragAmount ->
            change.consume()
            onDrag(dragAmount.y)
        },
        onDragEnd = onDragEnd,
        onDragCancel = onDragEnd,
    )
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> {
    val mutable = toMutableList()
    val tmp = mutable[a]
    mutable[a] = mutable[b]
    mutable[b] = tmp
    return mutable
}

@Composable
private fun TemplateCard(
    template: TemplateWithIntervals,
    onEdit: () -> Unit,
    onPlay: (String, List<Interval>) -> Unit,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = template.intervals.sumOf { it.durationSeconds }
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (highlighted) 8.dp else 0.dp,
        shadowElevation = if (highlighted) 8.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = template.template.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${template.intervals.size} intervals · ${formatDuration(totalSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledIconButton(
                onClick = {
                    val intervals = template.toDomainIntervals()
                    if (intervals.isNotEmpty()) onPlay(template.template.name, intervals)
                },
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Start ${template.template.name}",
                )
            }
        }
    }
}
