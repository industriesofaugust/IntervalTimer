package com.august.intervaltimer.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep a delete from spreading: one gesture deletes once, each
 * batch has its own clock, and undo brings back exactly what was asked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingDeletesTest {

    @Test
    fun `items are hidden at once and deleted only after the undo time`() = runTest {
        val deleted = mutableListOf<Set<Long>>()
        val pending = PendingDeletes(backgroundScope, undoMillis = 5_000) { deleted += it }

        pending.request(setOf(1L))
        runCurrent()
        assertEquals(setOf(1L), pending.hidden.value)
        advanceTimeBy(4_999)
        assertTrue(deleted.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf(setOf(1L)), deleted)
        assertTrue(pending.hidden.value.isEmpty())
    }

    @Test
    fun `undo brings the batch back and nothing is deleted`() = runTest {
        val deleted = mutableListOf<Set<Long>>()
        val pending = PendingDeletes(backgroundScope, undoMillis = 5_000) { deleted += it }

        val batch = pending.request(setOf(1L, 2L))
        advanceTimeBy(3_000)
        assertTrue(pending.undo(batch))
        assertTrue(pending.hidden.value.isEmpty())

        advanceTimeBy(10_000)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `asking again for an item that is already waiting does nothing`() = runTest {
        val deleted = mutableListOf<Set<Long>>()
        val pending = PendingDeletes(backgroundScope, undoMillis = 5_000) { deleted += it }

        pending.request(setOf(1L))
        advanceTimeBy(2_000)
        // The same swipe reporting a second and third time.
        assertTrue(pending.request(setOf(1L)).isEmpty())
        assertTrue(pending.request(setOf(1L)).isEmpty())

        // The clock was not restarted: it still runs out 5 seconds after the first request.
        advanceTimeBy(3_001)
        assertEquals(listOf(setOf(1L)), deleted)
        advanceTimeBy(10_000)
        assertEquals(1, deleted.size)
    }

    @Test
    fun `a newer delete does not finish or restart an older one`() = runTest {
        val deleted = mutableListOf<Set<Long>>()
        val pending = PendingDeletes(backgroundScope, undoMillis = 5_000) { deleted += it }

        val first = pending.request(setOf(1L))
        advanceTimeBy(3_000)
        val second = pending.request(setOf(2L))
        runCurrent()
        // Starting the second one deleted nothing.
        assertTrue(deleted.isEmpty())

        // The first can still be undone on its own.
        assertTrue(pending.undo(first))
        assertEquals(setOf(2L), pending.hidden.value)

        advanceTimeBy(5_001)
        assertEquals(listOf(second), deleted)
    }

    @Test
    fun `undo after the time has run out is refused`() = runTest {
        val deleted = mutableListOf<Set<Long>>()
        val pending = PendingDeletes(backgroundScope, undoMillis = 5_000) { deleted += it }

        val batch = pending.request(setOf(1L))
        advanceTimeBy(5_001)
        assertFalse(pending.undo(batch))
        assertEquals(listOf(setOf(1L)), deleted)
    }

    @Test
    fun `a batch only holds the items that were not already waiting`() = runTest {
        val deleted = mutableListOf<Set<Long>>()
        val pending = PendingDeletes(backgroundScope, undoMillis = 5_000) { deleted += it }

        pending.request(setOf(1L))
        val second = pending.request(setOf(1L, 2L, 3L))
        assertEquals(setOf(2L, 3L), second)

        advanceTimeBy(5_001)
        assertEquals(setOf(1L, 2L, 3L), deleted.flatten().toSet())
        assertEquals(3, deleted.sumOf { it.size })
    }

    @Test
    fun `closing down hands back everything still waiting and stops the clocks`() = runTest {
        val deleted = mutableListOf<Set<Long>>()
        val pending = PendingDeletes(backgroundScope, undoMillis = 5_000) { deleted += it }

        pending.request(setOf(1L))
        pending.request(setOf(2L))
        assertEquals(setOf(1L, 2L), pending.takeAll())

        advanceTimeBy(10_000)
        assertTrue(deleted.isEmpty())
    }
}
