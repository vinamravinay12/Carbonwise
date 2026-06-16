package com.rivi.carbonwise.recognition

import com.google.android.gms.location.DetectedActivity

/**
 * The movement kinds we track via Activity Recognition. Crucially, the API can tell us
 * the kind but NOT the precise transport mode — so [VEHICLE] offers the user a choice of
 * concrete modes to confirm, rather than the app guessing car vs bus.
 */
enum class DetectedKind(
    val label: String,
    /** Candidate factor types the user confirms from; first is the default selection. */
    val candidateTypes: List<String>,
    /** Typical speed (km/h) for estimating distance from duration; null when too variable. */
    val typicalSpeedKmh: Double?,
) {
    VEHICLE(
        "In a vehicle",
        listOf("car_petrol", "bus", "metro", "train", "tram", "auto_rickshaw", "motorbike", "ferry"),
        null,
    ),
    BICYCLE("Cycling", listOf("bicycle"), 15.0),
    WALK("Walking", listOf("walk"), 5.0),
    RUN("Running", listOf("walk"), 9.0);

    val defaultType: String get() = candidateTypes.first()

    /**
     * A transparent distance estimate from a detected duration, using [typicalSpeedKmh].
     * Vehicle speed is too variable to estimate, so it returns null (the user enters it).
     */
    fun estimatedDistanceKm(durationMinutes: Long): Double? {
        val speed = typicalSpeedKmh ?: return null
        val km = speed * (durationMinutes / 60.0)
        return (km * 10).toLong() / 10.0 // one decimal
    }

    companion object {
        /** Maps Google's DetectedActivity constant to our kind, or null if we ignore it. */
        fun fromDetectedActivity(activityType: Int): DetectedKind? = when (activityType) {
            DetectedActivity.IN_VEHICLE -> VEHICLE
            DetectedActivity.ON_BICYCLE -> BICYCLE
            DetectedActivity.WALKING -> WALK
            DetectedActivity.RUNNING -> RUN
            else -> null // STILL, TILTING, UNKNOWN, ON_FOOT (covered by WALKING/RUNNING)
        }

        /** The set we register transitions for. */
        val tracked: List<Int> = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
        )

        fun valueOfOrNull(name: String): DetectedKind? =
            entries.firstOrNull { it.name == name }
    }
}
