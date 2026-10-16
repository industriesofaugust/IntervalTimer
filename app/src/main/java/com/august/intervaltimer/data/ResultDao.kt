package com.august.intervaltimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {

    /** Returns the new row id, or -1 if a result with the same completedAt is already saved. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(result: WorkoutResultEntity): Long

    @Query("SELECT * FROM workout_results ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WorkoutResultEntity>>

    @Query("SELECT * FROM workout_results ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<WorkoutResultEntity>>

    @Query("SELECT * FROM workout_results ORDER BY completedAt DESC")
    suspend fun getAll(): List<WorkoutResultEntity>

    @Query("SELECT * FROM workout_results WHERE completedAt = :completedAt")
    suspend fun getByCompletedAt(completedAt: Long): WorkoutResultEntity?

    /** The highest heart rate in any saved workout, or null if there is none. */
    @Query("SELECT MAX(maxHeartRate) FROM workout_results")
    suspend fun highestHeartRate(): Double?

    @Query("SELECT * FROM workout_results WHERE id = :id")
    suspend fun getById(id: Long): WorkoutResultEntity?

    @Query("DELETE FROM workout_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM workout_results WHERE linkId = :linkId")
    suspend fun countByLinkId(linkId: String): Int

    @Query("DELETE FROM workout_results WHERE linkId = :linkId")
    suspend fun deleteByLinkId(linkId: String)

    /** The watch's result takes the place of the phone's own copy of the same workout. */
    @Transaction
    suspend fun replaceByLinkId(linkId: String, result: WorkoutResultEntity): Long {
        deleteByLinkId(linkId)
        return insert(result)
    }

    /** Saves the phone's own copy, unless the watch's result is already here. Returns null if it was not saved. */
    @Transaction
    suspend fun insertUnlessLinked(linkId: String, result: WorkoutResultEntity): Long? {
        if (countByLinkId(linkId) > 0) return null
        return insert(result)
    }
}
