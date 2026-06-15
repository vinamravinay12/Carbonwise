package com.rivi.carbonwise.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedSegmentDao {

    @Insert
    suspend fun insert(segment: DetectedSegmentEntity): Long

    /** The most recent still-open segment of a kind, to pair an EXIT with its ENTER. */
    @Query(
        "SELECT * FROM detected_segments WHERE kind = :kind AND status = 'OPEN' " +
            "ORDER BY startMillis DESC LIMIT 1",
    )
    suspend fun latestOpen(kind: String): DetectedSegmentEntity?

    @Query("UPDATE detected_segments SET endMillis = :endMillis, status = :status WHERE id = :id")
    suspend fun close(id: Long, endMillis: Long, status: String)

    @Query("UPDATE detected_segments SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("SELECT * FROM detected_segments WHERE id = :id")
    suspend fun getById(id: Long): DetectedSegmentEntity?

    @Query("SELECT * FROM detected_segments WHERE status = 'PENDING' ORDER BY endMillis DESC")
    fun observePending(): Flow<List<DetectedSegmentEntity>>

    /** Tidy up stale OPEN segments (e.g. an EXIT we never received) older than [cutoffMillis]. */
    @Query("DELETE FROM detected_segments WHERE status = 'OPEN' AND startMillis < :cutoffMillis")
    suspend fun pruneStaleOpen(cutoffMillis: Long)
}
