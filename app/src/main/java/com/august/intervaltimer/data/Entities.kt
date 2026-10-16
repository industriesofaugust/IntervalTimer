package com.august.intervaltimer.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "intervals",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("templateId")],
)
data class IntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val position: Int,
    val name: String,
    val type: String,
    val durationSeconds: Int,
)

data class TemplateWithIntervals(
    @Embedded val template: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val intervals: List<IntervalEntity>,
)

/**
 * A finished workout synced back from the watch. The unique index on
 * completedAt means the same result can never be stored twice.
 */
@Entity(
    tableName = "workout_results",
    indices = [Index(value = ["completedAt"], unique = true)],
)
data class WorkoutResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val completedAt: Long,
    val templateName: String?,
    val totalSeconds: Int,
    val workSeconds: Int,
    val workIntervals: Int,
    val avgHeartRate: Double?,
    val maxHeartRate: Double?,
    val steps: Long?,
    val calories: Double?,
    val hrSeriesJson: String? = null,
    // The workout's intervals and how many were finished, for drawing its
    // shape in History. Null on results saved before version 5.
    val intervalsJson: String? = null,
    val completedIntervals: Int? = null,
    // Ties the phone's own copy to the watch's copy of the same linked
    // workout. Null before version 6, and for workouts that were not linked.
    val linkId: String? = null,
)
