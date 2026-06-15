package com.rivi.carbonwise.advisor

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.rivi.carbonwise.domain.EmissionFactors
import com.rivi.carbonwise.domain.Footprint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Uses Gemini to choose the single smartest, most realistic swap for the day — weighing
 * which activity is both high-impact *and* genuinely achievable for an everyday person
 * (e.g. a short car trip is easier to swap than a flight). It returns only the two
 * activity-type names; the [com.rivi.carbonwise.domain.CarbonEngine] computes the saving.
 * Any failure or off-list answer simply yields null, and the caller falls back to the
 * deterministic rule-based swap.
 */
class GeminiSwapAdvisor(
    apiKey: String,
    modelName: String = "gemini-2.5-flash",
) : SwapAdvisor {

    private val model = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.2f
            responseMimeType = "application/json"
        },
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Lower-carbon options the engine can price, grouped by category for sensible swaps. */
    private val menu: String = EmissionFactors.table
        .groupBy { it.category }
        .entries.joinToString("\n") { (category, factors) ->
            val items = factors.joinToString(", ") { it.type }
            "${category.label}: $items"
        }

    override suspend fun suggest(footprint: Footprint): SwapSuggestion? {
        if (footprint.activities.isEmpty()) return null

        val response = model.generateContent(buildPrompt(footprint))
        val text = response.text?.trim().orEmpty().ifEmpty { return null }
        val suggestion = runCatching { json.decodeFromString<Suggestion>(text) }.getOrNull()
            ?: return null

        // Validate strictly: both must be known types, and from must be in today's day.
        val fromKnown = footprint.activities.any { it.factor.type == suggestion.fromType }
        val toKnown = EmissionFactors.byType(suggestion.toType) != null
        if (!fromKnown || !toKnown) return null

        return SwapSuggestion(fromType = suggestion.fromType, toType = suggestion.toType)
    }

    private fun buildPrompt(footprint: Footprint): String {
        val today = footprint.activities.joinToString("\n") {
            "- ${it.factor.type} (${it.factor.displayName}), the largest contributors first"
        }
        return """
            You advise on the single best carbon swap for a person's day. Choose the ONE
            change that is both high-impact and realistic/achievable for an everyday person
            in India. Prefer a swap that is easy to actually do (a short car trip is easier
            to change than a long flight; a meal is easy to change).

            You must NOT mention or estimate any carbon/CO2 numbers — a separate engine does
            all math. You only pick which activity to swap and what to swap it to.

            Today's activities (by type):
            $today

            You may only swap an activity TO one of these lower-carbon types, and it must be
            a sensible substitute for the same need (commute → other transport, meal → other
            meal, appliance → other appliance):
            $menu

            "fromType" must be one of today's activity types. "toType" must be from the menu
            and genuinely lower-carbon than fromType.

            Respond with ONLY: {"fromType":"car_petrol","toType":"metro"}
        """.trimIndent()
    }

    @Serializable
    private data class Suggestion(val fromType: String, val toType: String)
}
