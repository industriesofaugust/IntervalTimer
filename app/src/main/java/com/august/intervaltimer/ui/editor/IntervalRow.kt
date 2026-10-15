package com.august.intervaltimer.ui.editor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.model.isGeneratedName
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.figureStyle
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.nameStyle
import com.august.intervaltimer.ui.theme.typeColor

private val CollapsedRowHeight = 52.dp

/**
 * One interval as a single line: coloured edge, number, name, duration, and a
 * handle to drag it. Tapping the number or duration opens the details below.
 * The name is its own text field, so tapping that edits it instead.
 *
 * [handleModifier] comes from the reorderable list and makes the handle
 * draggable.
 */
@Composable
fun IntervalRow(
    row: EditorRow,
    number: Int,
    placeholder: String,
    imeAction: ImeAction,
    expanded: Boolean,
    wantsFocus: Boolean,
    canDelete: Boolean,
    isDragging: Boolean,
    handleModifier: Modifier,
    viewModel: EditorViewModel,
    onEditDuration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = typeColor(row.type)
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
    val toggle = { viewModel.toggleExpanded(row.key) }

    // Keyboard Next on the row above asks for focus here.
    LaunchedEffect(wantsFocus) {
        if (wantsFocus) {
            focusRequester.requestFocus()
            viewModel.focusRequestHandled(row.key)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = elevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            // The coloured edge marks the interval as work or rest.
            modifier = Modifier.drawBehind {
                drawRect(
                    color = accent,
                    topLeft = Offset(0f, 8.dp.toPx()),
                    size = Size(3.dp.toPx(), size.height - 16.dp.toPx()),
                )
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CollapsedRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .clickable(onClickLabel = "Show details", onClick = toggle),
                ) {
                    Text(
                        text = "$number",
                        style = captionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                InlineTextField(
                    value = row.name,
                    onValueChange = { viewModel.setRowName(row.key, it) },
                    placeholder = placeholder,
                    textStyle = nameStyle(),
                    // A generated name is muted, like the placeholder it turns into.
                    textColor = if (isGeneratedName(row.name)) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { viewModel.focusNextWork(row.key) },
                        onDone = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focus ->
                            // Start typing over a generated name instead of
                            // deleting it first; the placeholder shows the same text.
                            if (focus.isFocused && isGeneratedName(row.name)) {
                                viewModel.setRowName(row.key, "")
                            }
                        },
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(onClickLabel = "Show details", onClick = toggle)
                        .padding(horizontal = 10.dp),
                ) {
                    Text(
                        text = formatDuration(row.durationSeconds),
                        style = figureStyle(),
                        color = accent,
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .then(handleModifier),
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                RowDetails(
                    row = row,
                    canDelete = canDelete,
                    viewModel = viewModel,
                    onEditDuration = onEditDuration,
                )
            }
        }
    }
}

/** Work or rest, a finer time, duplicate and delete: what a single interval can change. */
@Composable
private fun RowDetails(
    row: EditorRow,
    canDelete: Boolean,
    viewModel: EditorViewModel,
    onEditDuration: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 14.dp, end = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeChip(IntervalType.WORK, selected = row.type == IntervalType.WORK) {
                viewModel.setRowType(row.key, IntervalType.WORK)
            }
            Spacer(Modifier.width(8.dp))
            TypeChip(IntervalType.REST, selected = row.type == IntervalType.REST) {
                viewModel.setRowType(row.key, IntervalType.REST)
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.duplicateRow(row.key) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate interval")
            }
            IconButton(
                onClick = { viewModel.removeRow(row.key) },
                enabled = canDelete,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete interval")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                symbol = "−",
                description = "Shorter",
                enabled = true,
                onStep = { viewModel.adjustRowDuration(row.key, -5) },
                modifier = Modifier.size(width = 52.dp, height = 40.dp),
            )
            Text(
                text = formatDuration(row.durationSeconds),
                style = figureStyle(),
                color = typeColor(row.type),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(76.dp)
                    .height(40.dp)
                    .clickable(onClickLabel = "Set exact time", onClick = onEditDuration)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
            StepButton(
                symbol = "+",
                description = "Longer",
                enabled = true,
                onStep = { viewModel.adjustRowDuration(row.key, +5) },
                modifier = Modifier.size(width = 52.dp, height = 40.dp),
            )
        }
    }
}

@Composable
private fun TypeChip(type: IntervalType, selected: Boolean, onClick: () -> Unit) {
    val color = typeColor(type)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = (if (type == IntervalType.WORK) "Work" else "Rest").labelCase(),
                style = labelStyle(),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color,
        ),
    )
}
