package com.august.intervaltimer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TemplateEntity::class, IntervalEntity::class, WorkoutResultEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun resultDao(): ResultDao

    companion object {
        /**
         * Version 1 → 2: adds the workout_results table. A migration tells
         * Room how to upgrade an existing database without losing the
         * templates already stored in it.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_results` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, " +
                        "`templateName` TEXT, " +
                        "`totalSeconds` INTEGER NOT NULL, " +
                        "`workSeconds` INTEGER NOT NULL, " +
                        "`workIntervals` INTEGER NOT NULL, " +
                        "`avgHeartRate` REAL, " +
                        "`maxHeartRate` REAL, " +
                        "`steps` INTEGER, " +
                        "`calories` REAL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_workout_results_completedAt` " +
                        "ON `workout_results` (`completedAt`)"
                )
            }
        }

        /** Version 2 → 3: results gain the heart-rate series for charts. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_results` ADD COLUMN `hrSeriesJson` TEXT")
            }
        }

        /** Version 3 → 4: templates become manually orderable. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `templates` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
