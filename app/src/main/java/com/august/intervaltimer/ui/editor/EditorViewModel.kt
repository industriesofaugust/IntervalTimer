package com.august.intervaltimer.ui.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.model.DEFAULT_REST_SECONDS
import com.august.intervaltimer.model.DEFAULT_WORK_SECONDS
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.MAX_INTERVAL_SECONDS
import com.august.intervaltimer.model.MAX_ROUNDS
import com.august.intervaltimer.model.MIN_INTERVAL_SECONDS
import com.august.intervaltimer.model.QuickBuildConfig
import com.august.intervaltimer.model.StarterTemplate
import com.august.intervaltimer.model.buildSession
import com.august.intervaltimer.model.commonDuration
import com.august.intervaltimer.model.lastDurationOf
import com.august.intervaltimer.model.repeated
import com.august.intervaltimer.model.stepDuration
import com.august.intervaltimer.model.suggestedTemplateName
import com.august.intervaltimer.model.withAllDurations
import com.august.intervaltimer.model.withGeneratedNames
import com.august.intervaltimer.model.withRounds
import com.august.intervaltimer.model.workCount
import kotlinx.coroutines.launch

data class EditorRow(
    val key: Long,
    val name: String,
    val type: IntervalType,
    val durationSeconds: Int,
)

private fun EditorRow.toInterval() = Interval(name, type, durationSeconds)

/**
 * The editor's state is plain Compose state, not a flow, so a text field
 * and a drag see every change in the same frame.
 */
class EditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as TimerApp).templatesRepository
    private val templateId: Long = handle.get<Long>("templateId") ?: 0L
    private var nextKey = 0L

    var name by mutableStateOf("")

    /** Replaced as a whole on every change; never edited in place. */
    var rows by mutableStateOf<List<EditorRow>>(emptyList())
        private set

    /** The row whose details are open. Only one at a time. */
    var expandedKey by mutableStateOf<Long?>(null)
        private set

    /** The row whose name should take the keyboard focus next. */
    var focusRequestKey by mutableStateOf<Long?>(null)
        private set

    /** False while an existing template is still being read from the database. */
    var isLoaded by mutableStateOf(templateId == 0L)
        private set

    /** A new template nobody has edited yet: the starter chips are shown. */
    var isPristine by mutableStateOf(false)
        private set

    val isExisting: Boolean get() = templateId != 0L
    val canSave: Boolean get() = isLoaded && rows.isNotEmpty()
    val totalSeconds: Int get() = rows.sumOf { it.durationSeconds }

    /** What a blank name becomes. */
    val suggestedName: String get() = suggestedTemplateName(currentIntervals())

    // The name a starter chip last filled in, so another chip may replace it.
    private var starterName: String? = null

    init {
        val rounds = handle.get<Int>("rounds") ?: 0
        when {
            templateId != 0L -> viewModelScope.launch {
                repository.getTemplate(templateId)?.let { stored ->
                    name = stored.template.name
                    rows = stored.intervals.sortedBy { it.position }.map {
                        newRow(Interval(it.name, IntervalType.valueOf(it.type), it.durationSeconds))
                    }
                    isLoaded = true
                }
            }
            rounds > 0 -> {
                // From Quick session > Customize: already a deliberate setup.
                val work = handle.get<Int>("work") ?: DEFAULT_WORK_SECONDS
                val rest = handle.get<Int>("rest") ?: DEFAULT_REST_SECONDS
                rows = newRows(
                    withGeneratedNames(buildSession(QuickBuildConfig(rounds.coerceAtMost(MAX_ROUNDS), work, rest)))
                )
            }
            else -> {
                rows = newRows(
                    withGeneratedNames(
                        buildSession(QuickBuildConfig(8, DEFAULT_WORK_SECONDS, DEFAULT_REST_SECONDS))
                    )
                )
                isPristine = true
            }
        }
    }

    private fun newRow(interval: Interval) = EditorRow(
        key = nextKey++,
        name = interval.name,
        type = interval.type,
        durationSeconds = interval.durationSeconds,
    )

    private fun newRows(intervals: List<Interval>) = intervals.map { newRow(it) }

    private fun currentIntervals(): List<Interval> = rows.map { it.toInterval() }

    /**
     * Every structural change ends here. Generated names are renumbered, an
     * open row that no longer exists is closed, and the starter chips go away.
     */
    private fun setRows(newRows: List<EditorRow>, countsAsEdit: Boolean = true) {
        val names = withGeneratedNames(newRows.map { it.toInterval() }).map { it.name }
        rows = newRows.mapIndexed { index, row -> row.copy(name = names[index]) }
        if (rows.none { it.key == expandedKey }) expandedKey = null
        if (rows.none { it.key == focusRequestKey }) focusRequestKey = null
        if (countsAsEdit) isPristine = false
    }

    /**
     * Runs one of the whole-list edits from core. Those only change intervals
     * in place or add and remove them at the end, so each row keeps its key
     * by position and new ones get fresh keys.
     */
    private fun editIntervals(
        countsAsEdit: Boolean = true,
        edit: (List<Interval>) -> List<Interval>,
    ) {
        val before = rows
        val after = edit(before.map { it.toInterval() })
        setRows(
            after.mapIndexed { index, interval ->
                EditorRow(
                    key = before.getOrNull(index)?.key ?: nextKey++,
                    name = interval.name,
                    type = interval.type,
                    durationSeconds = interval.durationSeconds,
                )
            },
            countsAsEdit,
        )
    }

    private fun updateRow(key: Long, transform: (EditorRow) -> EditorRow) {
        rows = rows.map { if (it.key == key) transform(it) else it }
        isPristine = false
    }

    // The pattern tiles: these act on the whole list.

    fun stepRounds(delta: Int) = editIntervals { withRounds(it, workCount(it) + delta) }

    /** Steps every interval of a type; if they differ, from the last one's duration. */
    fun stepAllDurations(type: IntervalType, deltaSeconds: Int) = editIntervals {
        val from = commonDuration(it, type) ?: lastDurationOf(it, type)
        withAllDurations(it, type, stepDuration(from, deltaSeconds))
    }

    fun setAllDurations(type: IntervalType, seconds: Int) =
        editIntervals { withAllDurations(it, type, seconds) }

    fun repeatAll() = editIntervals { repeated(it, lastDurationOf(it, IntervalType.REST)) }

    fun applyStarter(starter: StarterTemplate) {
        setRows(newRows(starter.intervals()), countsAsEdit = false)
        if (name.isBlank() || name == starterName) name = starter.name
        starterName = starter.name
    }

    // One interval at a time.

    /** Typing: only this row changes, nothing is renumbered. */
    fun setRowName(key: Long, newName: String) = updateRow(key) { it.copy(name = newName) }

    fun setRowType(key: Long, newType: IntervalType) {
        setRows(rows.map { if (it.key == key) it.copy(type = newType) else it })
    }

    fun adjustRowDuration(key: Long, deltaSeconds: Int) = updateRow(key) {
        it.copy(durationSeconds = stepDuration(it.durationSeconds, deltaSeconds))
    }

    /** From the wheel picker: any exact value, down to a single second. */
    fun setRowDuration(key: Long, seconds: Int) = updateRow(key) {
        it.copy(durationSeconds = seconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS))
    }

    fun addRow(type: IntervalType) {
        val intervals = currentIntervals()
        val added = newRow(Interval("", type, lastDurationOf(intervals, type)))
        setRows(rows + added)
    }

    fun removeRow(key: Long) {
        if (rows.size > 1) setRows(rows.filter { it.key != key })
    }

    /** Inserts a copy of an interval directly below it. */
    fun duplicateRow(key: Long) {
        val index = rows.indexOfFirst { it.key == key }
        if (index < 0) return
        val copy = rows[index].copy(key = nextKey++)
        setRows(rows.toMutableList().apply { add(index + 1, copy) })
    }

    /** Drag and drop reports the two rows by key, because other items share the list. */
    fun moveRow(fromKey: Long, toKey: Long) {
        val from = rows.indexOfFirst { it.key == fromKey }
        val to = rows.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0 || from == to) return
        setRows(rows.toMutableList().apply { add(to, removeAt(from)) })
    }

    // Details and focus.

    fun toggleExpanded(key: Long) {
        expandedKey = if (expandedKey == key) null else key
    }

    fun collapse() {
        expandedKey = null
    }

    /** Keyboard Next on a name: go to the name of the next work interval. */
    fun focusNextWork(afterKey: Long) {
        val index = rows.indexOfFirst { it.key == afterKey }
        if (index < 0) return
        focusRequestKey = rows.drop(index + 1).firstOrNull { it.type == IntervalType.WORK }?.key
    }

    fun focusRequestHandled(key: Long) {
        if (focusRequestKey == key) focusRequestKey = null
    }

    // Saving.

    private fun finalName() = name.trim().ifBlank { suggestedName }

    /** What gets stored and run: blank names filled, generated ones numbered. */
    private fun finalIntervals(): List<Interval> = withGeneratedNames(currentIntervals())

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            repository.saveTemplate(templateId, finalName(), finalIntervals())
            onSaved()
        }
    }

    /** Saves, then hands the workout over to be run. */
    fun saveAndStart(onStart: (name: String, intervals: List<Interval>) -> Unit) {
        if (!canSave) return
        val workoutName = finalName()
        val intervals = finalIntervals()
        viewModelScope.launch {
            repository.saveTemplate(templateId, workoutName, intervals)
            onStart(workoutName, intervals)
        }
    }

    /** Saves what's on screen as a new template, leaving the original as it was. */
    fun saveAsCopy(onSaved: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            repository.saveTemplate(0, "${finalName()} copy", finalIntervals())
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
