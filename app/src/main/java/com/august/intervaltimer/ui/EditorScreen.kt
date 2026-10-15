package com.august.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.design.intervalCountLabel
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.MAX_ROUNDS
import com.august.intervaltimer.model.commonDuration
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.model.lastDurationOf
import com.august.intervaltimer.model.starterTemplates
import com.august.intervaltimer.model.withGeneratedNames
import com.august.intervaltimer.model.workCount
import com.august.intervaltimer.ui.components.SessionStrip
import com.august.intervaltimer.ui.editor.EditorRow
import com.august.intervaltimer.ui.editor.EditorViewModel
import com.august.intervaltimer.ui.editor.InlineTextField
import com.august.intervaltimer.ui.editor.IntervalRow
import com.august.intervaltimer.ui.editor.PatternTile
import com.august.intervaltimer.ui.editor.buttonStyle
import com.august.intervaltimer.ui.editor.labelStyle
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.figureStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.nameStyle
import com.august.intervaltimer.ui.theme.typeColor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** What the duration wheels are about to set. */
private sealed interface PickerTarget {
    data class Pattern(val type: IntervalType) : PickerTarget
    data class Row(val key: Long) : PickerTarget
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSaveAndStart: (name: String, intervals: List<Interval>) -> Unit,
    viewModel: EditorViewModel = viewModel(),
) {
    // While typing, the bottom bar steps aside so the interval list gets
    // the space the keyboard didn't take.
    val imeVisible = WindowInsets.isImeVisible
    val listState = rememberLazyListState()
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var scrollToEnd by remember { mutableIntStateOf(0) }

    val rows = viewModel.rows
    val intervals = remember(rows) { rows.map { Interval(it.name, it.type, it.durationSeconds) } }
    val placeholders = remember(rows) { generatedNames(rows) }
    val lastWorkKey = rows.lastOrNull { it.type == IntervalType.WORK }?.key

    // The list starts with the starter chips (new templates only) and the
    // pattern tiles, so rows are numbered from after them.
    val leadingItems = if (viewModel.isPristine) 2 else 1
    fun listIndexOf(key: Long): Int = leadingItems + viewModel.rows.indexOfFirst { it.key == key }

    // Dragging reports list items by key. Only interval rows are in the
    // view model, so the starter chips, tiles and footer are ignored.
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? Long
        val toKey = to.key as? Long
        if (fromKey != null && toKey != null) viewModel.moveRow(fromKey, toKey)
    }

    // Opening a row's details: make sure all of them can be seen.
    LaunchedEffect(viewModel.expandedKey) {
        val key = viewModel.expandedKey ?: return@LaunchedEffect
        withFrameNanos { } // wait until the opened row has been measured
        listState.bringIntoView(listIndexOf(key), key)
    }
    // Keyboard Next may target a row that isn't on screen yet. Scroll to it;
    // once it exists, its own effect takes the focus.
    LaunchedEffect(viewModel.focusRequestKey) {
        val key = viewModel.focusRequestKey ?: return@LaunchedEffect
        listState.bringIntoView(listIndexOf(key), key)
    }
    // After adding intervals, keep the add buttons in reach.
    LaunchedEffect(scrollToEnd) {
        if (scrollToEnd > 0) {
            withFrameNanos { } // wait until the new rows are in the list
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        EditorHeader(
            viewModel = viewModel,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onBack = onBack,
            onSaved = onSaved,
        )

        // The shape of the workout, redrawn as intervals change.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)) {
            if (isDial()) {
                SessionStrip(
                    intervals = intervals,
                    modifier = Modifier.fillMaxWidth(),
                    height = 5.dp,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = summaryText(rows.size, viewModel.totalSeconds),
                style = captionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (viewModel.isPristine) {
                item(key = "presets") { StarterChips(viewModel) }
            }
            item(key = "pattern") {
                PatternTiles(
                    intervals = intervals,
                    viewModel = viewModel,
                    onPickDuration = { type -> picker = PickerTarget.Pattern(type) },
                )
            }
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                ReorderableItem(reorderState, key = row.key) { isDragging ->
                    IntervalRow(
                        row = row,
                        number = index + 1,
                        placeholder = placeholders.getOrElse(index) { "" },
                        imeAction = if (row.type == IntervalType.WORK && row.key != lastWorkKey) {
                            ImeAction.Next
                        } else {
                            ImeAction.Done
                        },
                        expanded = viewModel.expandedKey == row.key,
                        wantsFocus = viewModel.focusRequestKey == row.key,
                        canDelete = rows.size > 1,
                        isDragging = isDragging,
                        handleModifier = Modifier.draggableHandle(
                            onDragStarted = { viewModel.collapse() },
                        ),
                        viewModel = viewModel,
                        onEditDuration = { picker = PickerTarget.Row(row.key) },
                    )
                }
            }
            item(key = "footer") {
                FooterButtons(
                    canRepeat = workCount(intervals) * 2 <= MAX_ROUNDS,
                    onAdd = { type ->
                        viewModel.addRow(type)
                        scrollToEnd++
                    },
                    onRepeatAll = {
                        viewModel.repeatAll()
                        scrollToEnd++
                    },
                )
            }
        }

        if (!imeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Total".labelCase(),
                        style = captionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(viewModel.totalSeconds),
                        style = figureStyle(),
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.saveAndStart(onSaveAndStart) },
                    enabled = viewModel.canSave,
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(text = "Save & start".labelCase(), style = buttonStyle())
                }
            }
        }
    }

    picker?.let { target ->
        val initialSeconds = when (target) {
            is PickerTarget.Pattern ->
                commonDuration(intervals, target.type) ?: lastDurationOf(intervals, target.type)
            is PickerTarget.Row -> rows.firstOrNull { it.key == target.key }?.durationSeconds
        }
        if (initialSeconds != null) {
            DurationPickerDialog(
                initialSeconds = initialSeconds,
                onDismiss = { picker = null },
                onConfirm = { seconds ->
                    when (target) {
                        is PickerTarget.Pattern -> viewModel.setAllDurations(target.type, seconds)
                        is PickerTarget.Row -> viewModel.setRowDuration(target.key, seconds)
                    }
                    picker = null
                },
            )
        }
    }
}

/** "15 INTERVALS · 7:40" in Dial; the same in lower case for Minimal. */
@Composable
private fun summaryText(count: Int, totalSeconds: Int): String {
    val countText = if (isDial()) {
        intervalCountLabel(count)
    } else {
        if (count == 1) "1 interval" else "$count intervals"
    }
    return "$countText · ${formatDuration(totalSeconds)}"
}

/**
 * What each row's name would be if it were blank. Reuses the renaming from
 * core on a copy of the list with every name emptied.
 */
private fun generatedNames(rows: List<EditorRow>): List<String> =
    withGeneratedNames(rows.map { Interval("", it.type, it.durationSeconds) }).map { it.name }

/**
 * Scrolls just far enough to show the whole item. An item that isn't on
 * screen at all is brought to the top.
 */
private suspend fun LazyListState.bringIntoView(index: Int, key: Any) {
    if (index < 0) return
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    if (item == null) {
        animateScrollToItem(index)
        return
    }
    val viewportTop = layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding
    val viewportBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
    when {
        item.offset < viewportTop -> animateScrollBy((item.offset - viewportTop).toFloat())
        item.offset + item.size > viewportBottom ->
            animateScrollBy((item.offset + item.size - viewportBottom).toFloat())
    }
}

/**
 * Always on screen, keyboard or not: back, the template's name, and SAVE.
 * Saving a copy and deleting live in a menu, for templates that already exist.
 */
@Composable
private fun EditorHeader(
    viewModel: EditorViewModel,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp),
        ) {
            InlineTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                placeholder = viewModel.suggestedName,
                textStyle = nameStyle(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (viewModel.isExisting) {
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    DropdownMenuItem(
                        text = { Text("Save copy".labelCase(), style = labelStyle()) },
                        enabled = viewModel.canSave,
                        onClick = {
                            onMenuOpenChange(false)
                            viewModel.saveAsCopy(onSaved = onSaved)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete".labelCase(),
                                style = labelStyle(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onMenuOpenChange(false)
                            viewModel.delete(onDeleted = onSaved)
                        },
                    )
                }
            }
        }
        TextButton(
            onClick = { viewModel.save(onSaved = onSaved) },
            enabled = viewModel.canSave,
        ) {
            Text(text = "Save".labelCase(), style = buttonStyle())
        }
    }
}

/** TABATA / HIIT / STRENGTH: replace the list with a ready-made workout. */
@Composable
private fun StarterChips(viewModel: EditorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        starterTemplates.forEach { starter ->
            FilterChip(
                selected = viewModel.name == starter.name,
                onClick = { viewModel.applyStarter(starter) },
                label = { Text(starter.shortName.labelCase(), style = labelStyle()) },
            )
        }
    }
}

/** ROUNDS, WORK and REST: change the whole list at once. */
@Composable
private fun PatternTiles(
    intervals: List<Interval>,
    viewModel: EditorViewModel,
    onPickDuration: (IntervalType) -> Unit,
) {
    val rounds = workCount(intervals)
    val roundsEditable = rounds <= MAX_ROUNDS // a longer list would be cut short

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PatternTile(
            label = "Rounds",
            value = "$rounds",
            valueColor = MaterialTheme.colorScheme.onSurface,
            decreaseDescription = "Fewer rounds",
            increaseDescription = "More rounds",
            canDecrease = roundsEditable && rounds > 1,
            canIncrease = rounds < MAX_ROUNDS,
            onDecrease = { viewModel.stepRounds(-1) },
            onIncrease = { viewModel.stepRounds(+1) },
            modifier = Modifier.weight(1f),
        )
        DurationTile(IntervalType.WORK, "Work", intervals, viewModel, onPickDuration, Modifier.weight(1f))
        DurationTile(IntervalType.REST, "Rest", intervals, viewModel, onPickDuration, Modifier.weight(1f))
    }
}

@Composable
private fun DurationTile(
    type: IntervalType,
    label: String,
    intervals: List<Interval>,
    viewModel: EditorViewModel,
    onPickDuration: (IntervalType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val any = intervals.any { it.type == type }
    val common = commonDuration(intervals, type)
    PatternTile(
        label = label,
        value = when {
            common != null -> formatDuration(common)
            !any -> "--"
            else -> "Mixed".labelCase()
        },
        valueColor = typeColor(type),
        decreaseDescription = "Shorter ${label.lowercase()}",
        increaseDescription = "Longer ${label.lowercase()}",
        canDecrease = any,
        canIncrease = any,
        onDecrease = { viewModel.stepAllDurations(type, -5) },
        onIncrease = { viewModel.stepAllDurations(type, +5) },
        onValueClick = if (any) ({ onPickDuration(type) }) else null,
        modifier = modifier,
    )
}

/** + WORK, + REST, REPEAT ALL. */
@Composable
private fun FooterButtons(
    canRepeat: Boolean,
    onAdd: (IntervalType) -> Unit,
    onRepeatAll: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FooterButton("+ Work", typeColor(IntervalType.WORK), true, { onAdd(IntervalType.WORK) }, Modifier.weight(1f))
        FooterButton("+ Rest", typeColor(IntervalType.REST), true, { onAdd(IntervalType.REST) }, Modifier.weight(1f))
        FooterButton(
            text = "Repeat all",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            enabled = canRepeat,
            onClick = onRepeatAll,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun FooterButton(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(text = text.labelCase(), style = labelStyle(), color = color, maxLines = 1)
    }
}
