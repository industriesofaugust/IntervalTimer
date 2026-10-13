package com.august.intervaltimer.data

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.sync.HrSample
import com.august.intervaltimer.sync.HrSeriesCodec
import com.august.intervaltimer.sync.IntervalListCodec
import com.august.intervaltimer.sync.toDomainIntervals
import com.august.intervaltimer.sync.WorkoutResult
import kotlinx.coroutines.flow.Flow

class ResultsRepository(private val dao: ResultDao) {

    fun observeRecent(limit: Int): Flow<List<WorkoutResultEntity>> = dao.observeRecent(limit)

    fun observeAll(): Flow<List<WorkoutResultEntity>> = dao.observeAll()

    suspend fun getById(id: Long): WorkoutResultEntity? = dao.getById(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /** Stores the result and returns the entity, for anyone who needs it next. */
    suspend fun insert(result: WorkoutResult): WorkoutResultEntity {
        val entity = result.toEntity()
        dao.insert(entity)
        return entity
    }
}

/** The workout's intervals, or empty for results saved before they were recorded. */
fun WorkoutResultEntity.intervals(): List<Interval> =
    intervalsJson?.let { IntervalListCodec.decode(it).toDomainIntervals() } ?: emptyList()

fun WorkoutResultEntity.hrSeries(): List<HrSample> =
    hrSeriesJson?.let { HrSeriesCodec.decode(it) } ?: emptyList()

private fun WorkoutResult.toEntity() = WorkoutResultEntity(
    completedAt = completedAt,
    templateName = templateName,
    totalSeconds = totalSeconds,
    workSeconds = workSeconds,
    workIntervals = workIntervals,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    steps = steps,
    calories = calories,
    hrSeriesJson = hrSeries.takeIf { it.isNotEmpty() }?.let { HrSeriesCodec.encode(it) },
    intervalsJson = intervals.takeIf { it.isNotEmpty() }?.let { IntervalListCodec.encode(it) },
    completedIntervals = completedIntervals,
)
