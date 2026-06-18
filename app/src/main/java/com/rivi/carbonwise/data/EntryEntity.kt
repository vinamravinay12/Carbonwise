package com.rivi.carbonwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One logged day, stored locally. The full computed footprint and best swap are kept as
 * JSON so a past entry renders identically to when it was logged, with no recomputation.
 * [epochDay] groups entries into calendar days for the trend view.
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val createdAt: Long,
    val sentence: String,
    val totalKg: Double,
    val footprintJson: String,
    val swapJson: String?,
    val unrecognizedJson: String,
    val impactNarrative: String? = null,
)
