package com.august.intervaltimer.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchInfoTest {

    @Test
    fun `watch info survives the trip to the phone`() {
        val info = WatchInfo("0.16", 17, templateCount = 5, templatesReceivedAtEpochMillis = 1_760_000_000_000)

        assertEquals(info, WatchInfoCodec.decode(WatchInfoCodec.encode(info)))
    }

    @Test
    fun `a watch that has received nothing yet has no receive time`() {
        val info = WatchInfo("0.16", 17, templateCount = 0)

        assertNull(WatchInfoCodec.decode(WatchInfoCodec.encode(info))?.templatesReceivedAtEpochMillis)
    }

    @Test
    fun `fields added by a newer watch are ignored`() {
        val json = """{"appVersionName":"0.17","appVersionCode":18,"templateCount":2,"somethingNew":1}"""

        assertEquals(2, WatchInfoCodec.decode(json)?.templateCount)
    }

    @Test
    fun `damaged watch info reads as null instead of crashing`() {
        assertNull(WatchInfoCodec.decode("not json"))
        assertNull(WatchInfoCodec.decode("""{"appVersionName":"0.16"}"""))
    }

    @Test
    fun `a template list from an older phone has no publish time`() {
        val json = """{"templates":[]}"""

        assertEquals(0L, TemplateSyncCodec.decode(json).publishedAtEpochMillis)
    }

    @Test
    fun `the publish time makes an identical template list a different payload`() {
        val first = TemplateSyncCodec.encode(SyncedTemplateList(emptyList(), publishedAtEpochMillis = 1))
        val second = TemplateSyncCodec.encode(SyncedTemplateList(emptyList(), publishedAtEpochMillis = 2))

        assertNotEquals(first, second)
    }
}
