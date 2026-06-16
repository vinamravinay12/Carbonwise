package com.rivi.carbonwise.data

import com.rivi.carbonwise.recognition.DetectedKind

/**
 * A finished, auto-detected movement awaiting the user's one-tap confirmation.
 * [distanceKm] is GPS-measured when available (else null → estimate/manual at confirm).
 * [suggestedType] is the classifier's pre-selected mode (car prior for vehicles).
 */
data class DetectedTrip(
    val id: Long,
    val kind: DetectedKind,
    val startMillis: Long,
    val endMillis: Long,
    val distanceKm: Double? = null,
    val suggestedType: String? = null,
) {
    val durationMinutes: Long get() = ((endMillis - startMillis) / 60_000L).coerceAtLeast(1)
}
