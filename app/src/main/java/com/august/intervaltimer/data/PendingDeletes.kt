package com.august.intervaltimer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deletes that can still be undone. Asking to delete only hides the items;
 * the real delete happens when the undo time runs out.
 *
 * Every request is its own batch with its own clock. Nothing on screen can
 * finish a batch early or restart it: a second swipe, a newer message, or
 * leaving the screen never touches a batch that is already waiting. An item
 * that is already waiting is ignored if it is asked for again, so one gesture
 * that reports twice still deletes once.
 */
class PendingDeletes(
    private val scope: CoroutineScope,
    private val undoMillis: Long = UNDO_MILLIS,
    private val delete: suspend (Set<Long>) -> Unit,
) {
    private val _hidden = MutableStateFlow<Set<Long>>(emptySet())

    /** Everything that is waiting to be deleted; the list leaves these out. */
    val hidden: StateFlow<Set<Long>> = _hidden.asStateFlow()

    private val timers = mutableMapOf<Set<Long>, Job>()

    /**
     * Hides [ids] and deletes them after the undo time. Returns the batch
     * that was started: the ids that were not already waiting. An empty
     * batch means there was nothing new to do.
     */
    fun request(ids: Set<Long>): Set<Long> {
        val batch = ids - _hidden.value
        if (batch.isEmpty()) return batch
        _hidden.value += batch
        timers[batch] = scope.launch {
            delay(undoMillis)
            // From here on it can no longer be undone.
            timers.remove(batch)
            withContext(NonCancellable) { delete(batch) }
            _hidden.value -= batch
        }
        return batch
    }

    /** Brings a batch back untouched. Returns false if its time had already run out. */
    fun undo(batch: Set<Long>): Boolean {
        val timer = timers.remove(batch) ?: return false
        timer.cancel()
        _hidden.value -= batch
        return true
    }

    /**
     * Stops every clock and returns what was still waiting, for the owner to
     * delete straight away when it is being closed down.
     */
    fun takeAll(): Set<Long> {
        val waiting = timers.keys.flatten().toSet()
        timers.values.forEach { it.cancel() }
        timers.clear()
        return waiting
    }

    companion object {
        const val UNDO_MILLIS = 5_000L
    }
}
