package com.august.intervaltimer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDeviceTest {

    @Test
    fun `no node and no app means no watch`() {
        assertEquals(WatchConnection.NO_WATCH, watchConnection(false, false, false))
    }

    @Test
    fun `a connected node without the app means the app is missing`() {
        assertEquals(WatchConnection.APP_MISSING, watchConnection(true, false, false))
    }

    @Test
    fun `a watch with the app that cannot be reached is not reachable`() {
        assertEquals(WatchConnection.NOT_REACHABLE, watchConnection(true, true, false))
        // Out of range: no longer a connected node, but we know it has the app.
        assertEquals(WatchConnection.NOT_REACHABLE, watchConnection(false, true, false))
    }

    @Test
    fun `a reachable watch with the app is connected`() {
        assertEquals(WatchConnection.CONNECTED, watchConnection(true, true, true))
    }

    @Test
    fun `versions differ when the version codes differ`() {
        val info = com.august.intervaltimer.sync.WatchInfo("0.15", 15, 3)

        assertTrue(WatchDeviceState(info = info, phoneVersionCode = 16).versionMismatch)
        assertFalse(WatchDeviceState(info = info, phoneVersionCode = 15).versionMismatch)
        assertFalse(WatchDeviceState(phoneVersionCode = 15).versionMismatch)
    }
}
