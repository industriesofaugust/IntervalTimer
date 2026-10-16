package com.august.intervaltimer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TemplateEntity::class, IntervalEntity::class, WorkoutResultEntity::class],
    version = 7,
    exportSchema = true,
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

        /** Version 4 → 5: results remember their intervals, for strips and chart bands. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_results` ADD COLUMN `intervalsJson` TEXT")
                db.execSQL("ALTER TABLE `workout_results` ADD COLUMN `completedIntervals` INTEGER")
            }
        }

        /** Version 5 → 6: results remember which live link they came from. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_results` ADD COLUMN `linkId` TEXT")
            }
        }

        /** Version 6 → 7: results remember when each interval really ran. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_results` ADD COLUMN `timingJson` TEXT")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        )
    }
}
