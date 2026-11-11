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
    fun `versions differ when the version names differ`() {
        val info = com.august.intervaltimer.sync.WatchInfo("1.0.0", 181, 3)

        assertTrue(WatchDeviceState(info = info, phoneVersionName = "1.0.1").versionMismatch)
        // The codes always differ (Play needs a different one for the watch); the names decide.
        assertFalse(WatchDeviceState(info = info, phoneVersionName = "1.0.0", phoneVersionCode = 180).versionMismatch)
        assertFalse(WatchDeviceState(phoneVersionName = "1.0.0").versionMismatch)
    }
}
