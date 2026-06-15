package com.rivi.carbonwise.parser

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.rivi.carbonwise.domain.EmissionFactors
import com.rivi.carbonwise.domain.ParseResult
import com.rivi.carbonwise.domain.ParsedActivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gemini-backed parser. Its single responsibility is language → structured activities.
 * It is explicitly forbidden, by prompt, from producing any carbon number — the engine
 * owns all math. A strict JSON schema plus a closed list of activity types keeps the
 * output safe; anything off-list is dropped, and any failure propagates so the caller
 * can fall back to the rule-based parser.
 */
class GeminiParser(
    apiKey: String,
    modelName: String = "gemini-2.5-flash",
) : ActivityParser {

    private val model = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0f
            responseMimeType = "application/json"
        },
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val allowedTypes = EmissionFactors.table.joinToString("\n") {
        "- ${it.type} (${it.displayName}, measured in ${it.unit.symbol})"
    }

    override suspend fun parse(sentence: String): ParseResult {
        val prompt = buildPrompt(sentence)
        val response = model.generateContent(prompt)
        val text = response.text?.trim().orEmpty().ifEmpty { error("Empty Gemini response") }

        val parsed = json.decodeFromString<GeminiResponse>(text)
        val activities = parsed.activities
            .filter { EmissionFactors.byType(it.type) != null && it.quantity > 0 }
            .map { ParsedActivity(type = it.type, quantity = it.quantity, rawText = it.rawText) }

        return ParseResult(activities = activities, unrecognized = parsed.unrecognized)
    }

    private fun buildPrompt(sentence: String): String = """
        You convert a person's plain-language description of their day into structured
        activities for a carbon-footprint app. You ONLY identify what was done and how much.
        You must NEVER estimate, invent, or output any carbon/CO2 number — a separate engine
        does all math.

        Use ONLY these activity types:
        $allowedTypes

        Rules:
        - "quantity" is the amount in the type's unit: km for transport, servings for food
          (one meal = 1), hours for appliances, kWh for grid electricity.
        - If a distance or duration is not stated, pick a reasonable default (10 km for a
          commute, 1 hour for an appliance, 1 serving for a meal).
        - Map informal phrasing to the closest allowed type (e.g. "thali"/"dal rice" ->
          meal_vegetarian, "cab"/"uber" -> car_petrol, "AC" -> ac).
        - Put any fragment you genuinely cannot map into "unrecognized". Do not force a guess.

        Respond with ONLY this JSON shape:
        {"activities":[{"type":"car_petrol","quantity":15,"rawText":"drove 15 km"}],"unrecognized":[]}

        The person's day: "$sentence"
    """.trimIndent()

    @Serializable
    private data class GeminiResponse(
        val activities: List<GeminiActivity> = emptyList(),
        val unrecognized: List<String> = emptyList(),
    )

    @Serializable
    private data class GeminiActivity(
        val type: String,
        val quantity: Double,
        val rawText: String = "",
    )
}
