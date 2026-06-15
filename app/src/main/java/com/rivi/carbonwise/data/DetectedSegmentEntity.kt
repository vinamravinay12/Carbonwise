package com.rivi.carbonwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A movement segment detected by the Activity Recognition API. The API tells us the
 * *kind* of activity and when it started/ended — never a distance or a precise mode.
 * So a segment is born OPEN (entered), becomes PENDING when it ends (awaiting the user's
 * one-tap confirmation of mode + amount), then CONFIRMED (turned into a real log entry)
 * or DISMISSED (a false positive). No carbon number is ever derived from a guess.
 */
@Entity(tableName = "detected_segments")
data class DetectedSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,            // DetectedKind name: VEHICLE / BICYCLE / WALK / RUN
    val startMillis: Long,
    val endMillis: Long?,        // null while still OPEN
    val status: String,          // SegmentStatus name
)

object SegmentStatus {
    const val OPEN = "OPEN"
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val DISMISSED = "DISMISSED"
}
