package com.august.intervaltimer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundLevelTest {

    @Test
    fun `old volumes map to levels`() {
        assertEquals(2, levelFromOldVolume(50))
        assertEquals(4, levelFromOldVolume(85))
        assertEquals(5, levelFromOldVolume(100))
    }

    @Test
    fun `loudest level is as loud as the old HIGH`() {
        assertTrue(soundVolumeFor(5) >= 100)
        assertEquals(85, soundVolumeFor(4))
        assertEquals(30, soundVolumeFor(0))
    }
}
