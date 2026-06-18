package com.rivi.carbonwise.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    /** The most recently logged entry, for trend-aware impact comparison. */
    @Query("SELECT * FROM entries ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): EntryEntity?

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
