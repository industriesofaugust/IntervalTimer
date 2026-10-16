package com.august.intervaltimer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LastSavedTextTest {

    private val minute = 60_000L

    @Test
    fun `under a minute is just now`() {
        assertEquals("just now", lastSavedText(30_000))
    }

    @Test
    fun `minutes, hours and days are counted`() {
        assertEquals("last 2 min ago", lastSavedText(2 * minute + 5_000))
        assertEquals("last 3 h ago", lastSavedText(3 * 60 * minute))
        assertEquals("last 1 day ago", lastSavedText(24 * 60 * minute))
        assertEquals("last 4 days ago", lastSavedText(4 * 24 * 60 * minute))
    }
}
