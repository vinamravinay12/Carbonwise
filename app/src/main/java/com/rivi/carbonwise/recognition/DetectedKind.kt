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
) {
    VEHICLE("In a vehicle", listOf("car_petrol", "bus", "metro", "auto_rickshaw", "motorbike")),
    BICYCLE("Cycling", listOf("bicycle")),
    WALK("Walking", listOf("walk")),
    RUN("Running", listOf("walk"));

    val defaultType: String get() = candidateTypes.first()

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
