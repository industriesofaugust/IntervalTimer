package com.august.intervaltimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(result: WorkoutResultEntity)

    @Query("SELECT * FROM workout_results ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WorkoutResultEntity>>

    @Query("SELECT * FROM workout_results ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<WorkoutResultEntity>>

    @Query("SELECT * FROM workout_results WHERE id = :id")
    suspend fun getById(id: Long): WorkoutResultEntity?

    @Query("DELETE FROM workout_results WHERE id = :id")
    suspend fun deleteById(id: Long)
}
