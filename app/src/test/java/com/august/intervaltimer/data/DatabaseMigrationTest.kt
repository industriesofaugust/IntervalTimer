package com.august.intervaltimer.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.august.intervaltimer.sync.WorkoutResult
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
 * Builds a database exactly as an old version of the app left it, then opens
 * it with today's code. If an upgrade step is wrong, Room refuses to open the
 * database and the test fails, before a real phone loses its templates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DatabaseMigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createOldDatabase(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        // Version 1: templates and their intervals (Phase 2 of the project).
        db.execSQL(
            "CREATE TABLE `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE `intervals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`templateId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`durationSeconds` INTEGER NOT NULL, " +
                "FOREIGN KEY(`templateId`) REFERENCES `templates`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX `index_intervals_templateId` ON `intervals` (`templateId`)")
        if (version >= 2) {
            // Version 2 added workout results (Phase 5).
            db.execSQL(
                "CREATE TABLE `workout_results` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL, `templateName` TEXT, " +
                    "`totalSeconds` INTEGER NOT NULL, `workSeconds` INTEGER NOT NULL, " +
                    "`workIntervals` INTEGER NOT NULL, `avgHeartRate` REAL, " +
                    "`maxHeartRate` REAL, `steps` INTEGER, `calories` REAL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX `index_workout_results_completedAt` " +
                    "ON `workout_results` (`completedAt`)"
            )
        }
        fill(db)
        db.version = version
        db.close()
    }

    private fun openCurrent(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `a version 1 database upgrades and keeps its templates`() {
        createOldDatabase(version = 1) { db ->
            db.execSQL("INSERT INTO templates (id, name, createdAt) VALUES (1, 'Leg day', 1000)")
            db.execSQL(
                "INSERT INTO intervals (templateId, position, name, type, durationSeconds) " +
                    "VALUES (1, 0, 'Squats', 'WORK', 45), (1, 1, 'Rest', 'REST', 15)"
            )
        }

        val database = openCurrent()
        runBlocking {
            val template = database.templateDao().getTemplate(1)
            assertNotNull(template)
            assertEquals("Leg day", template!!.template.name)
            assertEquals(0, template.template.sortOrder)
            assertEquals(listOf("Squats", "Rest"), template.toDomainIntervals().map { it.name })

            // The tables added later exist and work.
            ResultsRepository(database.resultDao()).insert(
                WorkoutResult(
                    completedAt = 5_000,
                    totalSeconds = 60,
                    workSeconds = 45,
                    workIntervals = 1,
                )
            )
            assertEquals(1, database.resultDao().observeAll().first().size)
        }
        database.close()
    }

    @Test
    fun `a version 2 database upgrades and keeps its workout history`() {
        createOldDatabase(version = 2) { db ->
            db.execSQL(
                "INSERT INTO workout_results (completedAt, templateName, totalSeconds, " +
                    "workSeconds, workIntervals, avgHeartRate, maxHeartRate, steps, calories) " +
                    "VALUES (9000, 'Tabata', 230, 160, 8, 140.5, 171.0, 300, 47.5)"
            )
        }

        val database = openCurrent()
        runBlocking {
            val result = database.resultDao().observeAll().first().single()
            assertEquals("Tabata", result.templateName)
            assertEquals(140.5, result.avgHeartRate!!, 0.001)
            // The heart-rate series column was added in version 3, and the
            // interval list in version 5.
            assertNull(result.hrSeriesJson)
            assertNull(result.intervalsJson)
            assertEquals(0, result.intervals().size)
        }
        database.close()
    }

    @Test
    fun `reordering templates is saved`() {
        createOldDatabase(version = 1) { db ->
            db.execSQL(
                "INSERT INTO templates (id, name, createdAt) " +
                    "VALUES (1, 'A', 1000), (2, 'B', 2000), (3, 'C', 3000)"
            )
        }

        val database = openCurrent()
        runBlocking {
            database.templateDao().applyOrder(listOf(3L, 1L, 2L))

            val names = database.templateDao().observeTemplates().first().map { it.template.name }
            assertEquals(listOf("C", "A", "B"), names)
        }
        database.close()
    }
}
