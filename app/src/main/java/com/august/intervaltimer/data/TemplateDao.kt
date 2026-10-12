package com.august.intervaltimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.august.intervaltimer.model.Interval
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Transaction
    @Query("SELECT * FROM templates ORDER BY sortOrder ASC, createdAt DESC")
    fun observeTemplates(): Flow<List<TemplateWithIntervals>>

    @Query("UPDATE templates SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Rewrites all sort orders to match the given id sequence. */
    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplate(id: Long): TemplateWithIntervals?

    @Insert
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Query("UPDATE templates SET name = :name WHERE id = :id")
    suspend fun updateTemplateName(id: Long, name: String)

    @Insert
    suspend fun insertIntervals(intervals: List<IntervalEntity>)

    @Query("DELETE FROM intervals WHERE templateId = :templateId")
    suspend fun deleteIntervalsFor(templateId: Long)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    /**
     * Saves a template and its intervals in one atomic step. Passing id = 0
     * creates a new template; otherwise the existing one is overwritten.
     */
    @Transaction
    suspend fun saveTemplateWithIntervals(
        id: Long,
        name: String,
        intervals: List<Interval>,
    ): Long {
        val templateId = if (id == 0L) {
            insertTemplate(TemplateEntity(name = name, createdAt = System.currentTimeMillis()))
        } else {
            updateTemplateName(id, name)
            deleteIntervalsFor(id)
            id
        }
        insertIntervals(
            intervals.mapIndexed { index, interval ->
                IntervalEntity(
                    templateId = templateId,
                    position = index,
                    name = interval.name,
                    type = interval.type.name,
                    durationSeconds = interval.durationSeconds,
                )
            }
        )
        return templateId
    }
}
