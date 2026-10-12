package com.august.intervaltimer.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.ui.theme.Coral
import com.august.intervaltimer.ui.theme.CoralLight
import com.august.intervaltimer.ui.theme.Teal
import com.august.intervaltimer.ui.theme.TealLight
import kotlinx.coroutines.launch

data class EditorRow(
    val key: Long,
    val name: String,
    val type: IntervalType,
    val durationSeconds: Int,
)

class EditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as TimerApp).templatesRepository
    private val templateId: Long = handle.get<Long>("templateId") ?: 0L
    private var nextKey = 0L

    var name by mutableStateOf("")
    val rows = mutableStateListOf<EditorRow>()
    val isExisting: Boolean get() = templateId != 0L
    val totalSeconds: Int get() = rows.sumOf { it.durationSeconds }

    init {
        val rounds = handle.get<Int>("rounds") ?: 0
        when {
            templateId != 0L -> viewModelScope.launch {
                repository.getTemplate(templateId)?.let { stored ->
                    name = stored.template.name
                    rows.addAll(
                        stored.intervals.sortedBy { it.position }.map {
                            newRow(it.name, IntervalType.valueOf(it.type), it.durationSeconds)
                        }
                    )
                }
            }
            rounds > 0 -> {
                val work = handle.get<Int>("work") ?: 40
                val rest = handle.get<Int>("rest") ?: 20
                repeat(rounds) { round ->
                    rows.add(newRow("Work", IntervalType.WORK, work))
                    if (round < rounds - 1) rows.add(newRow("Rest", IntervalType.REST, rest))
                }
            }
            else -> rows.add(newRow("Work", IntervalType.WORK, 40))
        }
    }

    private fun newRow(name: String, type: IntervalType, seconds: Int) =
        EditorRow(key = nextKey++, name = name, type = type, durationSeconds = seconds)

    private fun updateRow(key: Long, transform: (EditorRow) -> EditorRow) {
        val index = rows.indexOfFirst { it.key == key }
        if (index >= 0) rows[index] = transform(rows[index])
    }

    fun setRowName(key: Long, newName: String) = updateRow(key) { it.copy(name = newName) }

    fun setRowType(key: Long, newType: IntervalType) = updateRow(key) { row ->
        // If the name is still the default, swap it to match the new type.
        val newName = when (row.name) {
            "Work", "Rest" -> if (newType == IntervalType.WORK) "Work" else "Rest"
            else -> row.name
        }
        row.copy(type = newType, name = newName)
    }

    fun adjustRowDuration(key: Long, deltaSeconds: Int) = updateRow(key) {
        it.copy(durationSeconds = (it.durationSeconds + deltaSeconds).coerceIn(5, 600))
    }

    /** From the wheel picker: any exact value, down to a single second. */
    fun setRowDuration(key: Long, seconds: Int) = updateRow(key) {
        it.copy(durationSeconds = seconds.coerceIn(1, 3599))
    }

    fun addRow(type: IntervalType) {
        val lastOfType = rows.lastOrNull { it.type == type }
        val seconds = lastOfType?.durationSeconds ?: if (type == IntervalType.WORK) 40 else 20
        rows.add(newRow(if (type == IntervalType.WORK) "Work" else "Rest", type, seconds))
    }

    fun removeRow(key: Long) {
        if (rows.size > 1) rows.removeAll { it.key == key }
    }

    fun moveRow(key: Long, delta: Int) {
        val from = rows.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in rows.indices) return
        val row = rows.removeAt(from)
        rows.add(to, row)
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveTemplate(
                id = templateId,
                name = name.ifBlank { "Workout" },
                intervals = rows.map {
                    Interval(
                        name = it.name.ifBlank { if (it.type == IntervalType.WORK) "Work" else "Rest" },
                        type = it.type,
                        durationSeconds = it.durationSeconds,
                    )
                },
            )
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (templateId == 0L) return
        viewModelScope.launch {
            repository.deleteTemplate(templateId)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditorViewModel = viewModel(),
) {
    // While typing, the bottom bar steps aside so the interval list gets
    // the space the keyboard didn't take.
    val imeVisible = WindowInsets.isImeVisible
    var durationPickerKey by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                placeholder = { Text("Template name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(viewModel.rows, key = { _, row -> row.key }) { index, row ->
                IntervalEditorCard(
                    row = row,
                    canMoveUp = index > 0,
                    canMoveDown = index < viewModel.rows.lastIndex,
                    canDelete = viewModel.rows.size > 1,
                    viewModel = viewModel,
                    onDurationClick = { durationPickerKey = row.key },
                    modifier = Modifier.animateItem(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.addRow(IntervalType.WORK) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("+ Work")
                    }
                    OutlinedButton(
                        onClick = { viewModel.addRow(IntervalType.REST) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("+ Rest")
                    }
                }
            }
        }

        if (!imeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(viewModel.totalSeconds),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (viewModel.isExisting) {
                    TextButton(onClick = { viewModel.delete(onDeleted = onSaved) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    onClick = { viewModel.save(onSaved = onSaved) },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Save")
                }
            }
        }
    }

    durationPickerKey?.let { key ->
        val row = viewModel.rows.firstOrNull { it.key == key }
        if (row != null) {
            DurationPickerDialog(
                initialSeconds = row.durationSeconds,
                onDismiss = { durationPickerKey = null },
                onConfirm = { seconds ->
                    viewModel.setRowDuration(key, seconds)
                    durationPickerKey = null
                },
            )
        }
    }
}

@Composable
private fun IntervalEditorCard(
    row: EditorRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    viewModel: EditorViewModel,
    onDurationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val accent = when (row.type) {
        IntervalType.WORK -> if (dark) CoralLight else Coral
        IntervalType.REST -> if (dark) TealLight else Teal
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { viewModel.setRowName(row.key, it) },
                    placeholder = { Text(if (row.type == IntervalType.WORK) "Work" else "Rest") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { viewModel.moveRow(row.key, -1) },
                    enabled = canMoveUp,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(
                    onClick = { viewModel.moveRow(row.key, +1) },
                    enabled = canMoveDown,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(
                    onClick = { viewModel.removeRow(row.key) },
                    enabled = canDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete interval")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = row.type == IntervalType.WORK,
                    onClick = { viewModel.setRowType(row.key, IntervalType.WORK) },
                    label = { Text("Work") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = row.type == IntervalType.REST,
                    onClick = { viewModel.setRowType(row.key, IntervalType.REST) },
                    label = { Text("Rest") },
                )
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { viewModel.adjustRowDuration(row.key, -5) },
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("−")
                }
                Text(
                    text = formatDuration(row.durationSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDurationClick)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                FilledTonalButton(
                    onClick = { viewModel.adjustRowDuration(row.key, +5) },
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("+")
                }
            }
        }
    }
}
