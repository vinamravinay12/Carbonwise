package com.rivi.carbonwise.data

import com.rivi.carbonwise.recognition.DetectedKind

/** A finished, auto-detected movement awaiting the user's one-tap confirmation. */
data class DetectedTrip(
    val id: Long,
    val kind: DetectedKind,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMinutes: Long get() = ((endMillis - startMillis) / 60_000L).coerceAtLeast(1)
}
