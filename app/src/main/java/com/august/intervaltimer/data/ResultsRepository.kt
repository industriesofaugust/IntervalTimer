package com.august.intervaltimer.data

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.sync.HrSample
import com.august.intervaltimer.sync.HrSeriesCodec
import com.august.intervaltimer.sync.IntervalListCodec
import com.august.intervaltimer.sync.toDomainIntervals
import com.august.intervaltimer.sync.WorkoutResult
import com.august.intervaltimer.timer.WorkoutTiming
import com.august.intervaltimer.timer.WorkoutTimingCodec
import kotlinx.coroutines.flow.Flow

class ResultsRepository(private val dao: ResultDao) {

    fun observeRecent(limit: Int): Flow<List<WorkoutResultEntity>> = dao.observeRecent(limit)

    fun observeAll(): Flow<List<WorkoutResultEntity>> = dao.observeAll()

    suspend fun getAll(): List<WorkoutResultEntity> = dao.getAll()

    suspend fun getById(id: Long): WorkoutResultEntity? = dao.getById(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /** The highest heart rate in any saved workout, for working out heart-rate zones. */
    suspend fun highestHeartRate(): Double? = dao.highestHeartRate()

    /** Stores the result and returns the entity (with its id), for anyone who needs it next. */
    suspend fun insert(result: WorkoutResult): WorkoutResultEntity {
        val entity = result.toEntity()
        return entity.withId(dao.insert(entity))
    }

    /**
     * A result that came from the watch. If the phone already saved its own
     * copy of the same linked workout, this replaces it, since this one has
     * the heart rate.
     */
    suspend fun insertFromWatch(result: WorkoutResult): WorkoutResultEntity {
        val linkId = result.linkId ?: return insert(result)
        val entity = result.toEntity()
        return entity.withId(dao.replaceByLinkId(linkId, entity))
    }

    /**
     * The phone's own result of a workout. Returns null, saving nothing, if
     * the watch's result of the same linked workout is already here.
     */
    suspend fun insertFromPhone(result: WorkoutResult): WorkoutResultEntity? {
        val linkId = result.linkId ?: return insert(result)
        val entity = result.toEntity()
        val rowId = dao.insertUnlessLinked(linkId, entity) ?: return null
        return entity.withId(rowId)
    }

    // A rowId of -1 means a result with this completedAt was already saved; that one is the entity.
    private suspend fun WorkoutResultEntity.withId(rowId: Long): WorkoutResultEntity =
        if (rowId > 0) copy(id = rowId) else dao.getByCompletedAt(completedAt) ?: this
}

/** The workout's intervals, or empty for results saved before they were recorded. */
fun WorkoutResultEntity.intervals(): List<Interval> =
    intervalsJson?.let { IntervalListCodec.decode(it).toDomainIntervals() } ?: emptyList()

fun WorkoutResultEntity.hrSeries(): List<HrSample> =
    hrSeriesJson?.let { HrSeriesCodec.decode(it) } ?: emptyList()

/** When each interval really ran, or empty for results saved before version 7. */
fun WorkoutResultEntity.timing(): WorkoutTiming =
    timingJson?.let { WorkoutTimingCodec.decode(it) } ?: WorkoutTiming()

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
    linkId = linkId,
    timingJson = spans.takeIf { it.isNotEmpty() }
        ?.let { WorkoutTimingCodec.encode(WorkoutTiming(it, pauses)) },
)
