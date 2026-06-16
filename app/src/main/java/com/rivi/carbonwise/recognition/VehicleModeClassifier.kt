package com.rivi.carbonwise.recognition

/** GPS-derived summary of a vehicle trip, used to lean toward a mode. */
data class TripFeatures(
    val distanceKm: Double,
    val durationMinutes: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val stopCount: Int,
    val gpsGaps: Int,
)

/**
 * Leans a detected vehicle trip toward car / bus / rail. This only *classifies* (picks an
 * activity-type name); the engine still computes the kilograms. The guess merely
 * pre-selects in the "Were you in a car?" prompt — the user always confirms or overrides.
 */
interface VehicleModeClassifier {
    suspend fun classify(features: TripFeatures): String
}

/**
 * Transparent heuristic with a **car prior** (the common, conservative default). Evidence
 * shifts it toward rail (GPS dropouts in tunnels, smooth high speed, few stops) or bus
 * (low average speed with frequent regular stops).
 */
object HeuristicVehicleClassifier : VehicleModeClassifier {
    override suspend fun classify(features: TripFeatures): String = when {
        // Rail: tunnels cause GPS gaps, or smooth + fast + barely any stops.
        features.gpsGaps >= 2 -> "metro"
        features.maxSpeedKmh >= 60 && features.stopCount <= 2 && features.avgSpeedKmh >= 35 -> "train"
        // Bus: crawls along with many regular stops.
        features.stopCount >= 4 && features.avgSpeedKmh <= 25 -> "bus"
        // Otherwise assume car (prior).
        else -> "car_petrol"
    }
}
