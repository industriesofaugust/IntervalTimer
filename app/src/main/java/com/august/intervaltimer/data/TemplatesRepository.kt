package com.august.intervaltimer.data

import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import kotlinx.coroutines.flow.Flow

class TemplatesRepository(private val dao: TemplateDao) {

    fun observeTemplates(): Flow<List<TemplateWithIntervals>> = dao.observeTemplates()

    suspend fun getTemplate(id: Long): TemplateWithIntervals? = dao.getTemplate(id)

    suspend fun saveTemplate(id: Long, name: String, intervals: List<Interval>): Long =
        dao.saveTemplateWithIntervals(id, name, intervals)

    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)

    /** Persists a full new ordering, e.g. after a drag-and-drop. */
    suspend fun applyOrder(orderedIds: List<Long>) = dao.applyOrder(orderedIds)
}

/** Converts a stored template into the interval list the timer runs. */
fun TemplateWithIntervals.toDomainIntervals(): List<Interval> =
    intervals
        .sortedBy { it.position }
        .map { Interval(it.name, IntervalType.valueOf(it.type), it.durationSeconds) }
