package com.rivi.carbonwise

import com.rivi.carbonwise.data.DetectedSegmentDao
import com.rivi.carbonwise.data.DetectedSegmentEntity
import com.rivi.carbonwise.data.EntryDao
import com.rivi.carbonwise.data.EntryEntity
import com.rivi.carbonwise.data.SegmentStatus
import com.rivi.carbonwise.domain.ParseResult
import com.rivi.carbonwise.domain.ParsedActivity
import com.rivi.carbonwise.parser.ActivityParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [EntryDao] for testing the repository without Room/Android. */
class FakeEntryDao : EntryDao {
    private val items = mutableListOf<EntryEntity>()
    private val flow = MutableStateFlow<List<EntryEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entry: EntryEntity): Long {
        val stored = entry.copy(id = nextId++)
        items.add(stored)
        publish()
        return stored.id
    }

    override fun observeAll(): Flow<List<EntryEntity>> = flow

    override suspend fun getById(id: Long): EntryEntity? = items.firstOrNull { it.id == id }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
        publish()
    }

    override suspend fun latest(): EntryEntity? = items.maxByOrNull { it.createdAt }

    private fun publish() {
        flow.value = items.sortedByDescending { it.createdAt }
    }
}

/** In-memory [DetectedSegmentDao] for testing the auto-tracking lifecycle. */
class FakeDetectedSegmentDao : DetectedSegmentDao {
    private val items = mutableListOf<DetectedSegmentEntity>()
    private val pending = MutableStateFlow<List<DetectedSegmentEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(segment: DetectedSegmentEntity): Long {
        val stored = segment.copy(id = nextId++)
        items.add(stored)
        publish()
        return stored.id
    }

    override suspend fun latestOpen(kind: String): DetectedSegmentEntity? =
        items.filter { it.kind == kind && it.status == SegmentStatus.OPEN }
            .maxByOrNull { it.startMillis }

    override suspend fun close(id: Long, endMillis: Long, status: String) =
        replace(id) { it.copy(endMillis = endMillis, status = status) }

    override suspend fun setStatus(id: Long, status: String) =
        replace(id) { it.copy(status = status) }

    override suspend fun updateMetrics(
        id: Long,
        distanceMeters: Double,
        avgSpeedKmh: Double,
        maxSpeedKmh: Double,
        stopCount: Int,
        gpsGaps: Int,
    ) = replace(id) {
        it.copy(
            distanceMeters = distanceMeters,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            stopCount = stopCount,
            gpsGaps = gpsGaps,
        )
    }

    override suspend fun setSuggestedType(id: Long, type: String) =
        replace(id) { it.copy(suggestedType = type) }

    override suspend fun getById(id: Long): DetectedSegmentEntity? = items.firstOrNull { it.id == id }

    override fun observePending(): Flow<List<DetectedSegmentEntity>> = pending

    override suspend fun pruneStaleOpen(cutoffMillis: Long) {
        items.removeAll { it.status == SegmentStatus.OPEN && it.startMillis < cutoffMillis }
        publish()
    }

    private fun replace(id: Long, transform: (DetectedSegmentEntity) -> DetectedSegmentEntity) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = transform(items[index])
        publish()
    }

    private fun publish() {
        pending.value = items.filter { it.status == SegmentStatus.PENDING }
            .sortedByDescending { it.endMillis ?: 0 }
    }
}

/** A parser that returns a fixed result, for deterministic repository tests. */
class FakeParser(private val result: ParseResult) : ActivityParser {
    constructor(vararg activities: ParsedActivity) : this(ParseResult(activities.toList()))
    override suspend fun parse(sentence: String): ParseResult = result
}
