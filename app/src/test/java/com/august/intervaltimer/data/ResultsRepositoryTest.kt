package com.august.intervaltimer.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.august.intervaltimer.sync.WorkoutResult
import com.august.intervaltimer.timer.IntervalSpan
import com.august.intervaltimer.timer.PauseSpan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A linked workout can be saved twice: by the phone when the watch goes
 * quiet, and by the watch if it comes back. These check that only one copy
 * is left, and that it is the watch's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ResultsRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ResultsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ResultsRepository(database.resultDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun result(completedAt: Long, linkId: String?, heartRate: Double? = null) =
        WorkoutResult(
            completedAt = completedAt,
            totalSeconds = 60,
            workSeconds = 40,
            workIntervals = 2,
            avgHeartRate = heartRate,
            linkId = linkId,
        )

    private fun saved(): List<WorkoutResultEntity> =
        runBlocking { database.resultDao().observeAll().first() }

    @Test
    fun `the watch result replaces the phone copy of the same workout`() = runBlocking {
        assertNotNull(repository.insertFromPhone(result(1_000, "link-1")))

        repository.insertFromWatch(result(2_000, "link-1", heartRate = 140.0))

        val only = saved().single()
        assertEquals(2_000, only.completedAt)
        assertEquals(140.0, only.avgHeartRate!!, 0.001)
        assertEquals("link-1", only.linkId)
    }

    @Test
    fun `the phone skips its copy when the watch result is already saved`() = runBlocking {
        repository.insertFromWatch(result(2_000, "link-1", heartRate = 140.0))

        assertNull(repository.insertFromPhone(result(1_000, "link-1")))

        assertEquals(2_000, saved().single().completedAt)
    }

    @Test
    fun `other workouts are left alone`() = runBlocking {
        repository.insertFromPhone(result(1_000, "link-1"))
        repository.insertFromPhone(result(3_000, null))
        repository.insertFromPhone(result(4_000, "link-2"))

        repository.insertFromWatch(result(2_000, "link-1", heartRate = 140.0))

        assertEquals(listOf(4_000L, 3_000L, 2_000L), saved().map { it.completedAt })
    }

    @Test
    fun `results without a link id are simply added`() = runBlocking {
        repository.insertFromWatch(result(1_000, null))
        repository.insertFromPhone(result(2_000, null))

        assertEquals(2, saved().size)
    }

    @Test
    fun `a saved result comes back with its row id`() = runBlocking {
        val first = repository.insert(result(1_000, null))
        val second = repository.insertFromWatch(result(2_000, "link-1"))
        val third = repository.insertFromPhone(result(3_000, "link-2"))!!

        val ids = saved().associate { it.completedAt to it.id }
        assertEquals(ids[1_000L], first.id)
        assertEquals(ids[2_000L], second.id)
        assertEquals(ids[3_000L], third.id)
        assertEquals(3, ids.values.toSet().size)
    }

    @Test
    fun `interval timing is stored with the result`() = runBlocking {
        val timed = result(1_000, null).copy(
            spans = listOf(IntervalSpan(0, 100, 5_100)),
            pauses = listOf(PauseSpan(2_000, 3_000)),
        )

        repository.insert(timed)

        val timing = saved().single().timing()
        assertEquals(timed.spans, timing.spans)
        assertEquals(timed.pauses, timing.pauses)
    }

    @Test
    fun `a result without timing stores none`() = runBlocking {
        repository.insert(result(1_000, null))

        assertNull(saved().single().timingJson)
    }

    @Test
    fun `the highest heart rate is the best across all workouts`() = runBlocking {
        assertNull(repository.highestHeartRate())
        repository.insert(result(1_000, null).copy(maxHeartRate = 171.0))
        repository.insert(result(2_000, null).copy(maxHeartRate = 183.0))
        repository.insert(result(3_000, null))

        assertEquals(183.0, repository.highestHeartRate()!!, 0.001)
    }
}
