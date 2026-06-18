package com.rivi.carbonwise.recognition

import android.util.Log
import com.rivi.carbonwise.ai.GeminiClient
import com.rivi.carbonwise.ai.GeminiTurn
import com.rivi.carbonwise.domain.EmissionFactors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Refines the heuristic's call with Gemini, which is good at fuzzy pattern judgement over
 * the trip summary. It outputs only a mode *name* (never a number); anything off-list or
 * any failure falls back to the [fallback] heuristic. This is the "hybrid" classifier.
 */
class GeminiVehicleClassifier(
    apiKey: String,
    private val fallback: VehicleModeClassifier = HeuristicVehicleClassifier,
    modelName: String = "gemini-2.5-flash",
) : VehicleModeClassifier {

    private val client = GeminiClient(apiKey = apiKey, model = modelName)

    private val json = Json { ignoreUnknownKeys = true }
    private val allowed = DetectedKind.VEHICLE.candidateTypes

    override suspend fun classify(features: TripFeatures): String {
        return try {
            val text = client.generate(
                turns = listOf(GeminiTurn("user", buildPrompt(features))),
                jsonOutput = true,
                temperature = 0.1,
            )
            val guess = json.decodeFromString<Guess>(text).mode
            if (guess in allowed && EmissionFactors.byType(guess) != null) guess
            else fallback.classify(features)
        } catch (e: Exception) {
            Log.w("CarbonWise", "Vehicle classifier fell back to heuristic: ${e.message}")
            fallback.classify(features)
        }
    }

    private fun buildPrompt(f: TripFeatures): String = """
        Classify the most likely transport mode for a trip, from its movement summary only.
        Do NOT output any carbon/CO2 number — only the mode name.

        Trip summary:
        - distance: ${"%.1f".format(f.distanceKm)} km
        - duration: ${f.durationMinutes} min
        - average speed: ${"%.0f".format(f.avgSpeedKmh)} km/h
        - top speed: ${"%.0f".format(f.maxSpeedKmh)} km/h
        - stops (near-zero-speed dwells): ${f.stopCount}
        - GPS signal dropouts (e.g. tunnels): ${f.gpsGaps}

        Guidance: buses crawl with many regular stops; metro/train is smooth, fast, with
        few far-apart stops and GPS dropouts in tunnels; cars are irregular. When genuinely
        unsure, prefer "car_petrol".

        Choose exactly one of: ${allowed.joinToString(", ")}
        Respond with ONLY: {"mode":"car_petrol"}
    """.trimIndent()

    @Serializable
    private data class Guess(val mode: String)
}
