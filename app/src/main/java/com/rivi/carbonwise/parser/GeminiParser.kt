package com.rivi.carbonwise.parser

import com.rivi.carbonwise.ai.GeminiClient
import com.rivi.carbonwise.ai.GeminiTurn
import com.rivi.carbonwise.domain.Category
import com.rivi.carbonwise.domain.EmissionFactor
import com.rivi.carbonwise.domain.EmissionFactors
import com.rivi.carbonwise.domain.ParseResult
import com.rivi.carbonwise.domain.ParsedActivity
import com.rivi.carbonwise.domain.Unit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gemini-backed, open-ended parser. It maps phrasing to the deterministic factor table
 * wherever possible (so everyday activities stay consistent), and for anything the table
 * doesn't cover it returns an estimated factor (category, unit, kg per unit) so the engine
 * can still price it — those are flagged [EmissionFactor.estimated] and badged in the UI.
 * This is the relaxation that lets the user log literally anything without it scoring 0.
 */
class GeminiParser(
    apiKey: String,
    modelName: String = "gemini-2.5-flash",
) : ActivityParser {

    private val client = GeminiClient(apiKey = apiKey, model = modelName)

    private val json = Json { ignoreUnknownKeys = true }

    private val allowedTypes = EmissionFactors.table.joinToString("\n") {
        "- ${it.type} (${it.displayName}, measured in ${it.unit.symbol})"
    }

    override suspend fun parse(sentence: String): ParseResult {
        val text = client.generate(
            turns = listOf(GeminiTurn("user", buildPrompt(sentence))),
            jsonOutput = true,
            temperature = 0.1,
        ).ifEmpty { error("Empty Gemini response") }

        val parsed = json.decodeFromString<GeminiResponse>(text)
        val activities = parsed.activities.mapNotNull { it.toParsedActivity() }
        return ParseResult(activities = activities, unrecognized = parsed.unrecognized)
    }

    private fun GeminiActivity.toParsedActivity(): ParsedActivity? {
        if (quantity <= 0) return null
        // Prefer a known table type for consistency.
        if (EmissionFactors.byType(type) != null) {
            return ParsedActivity(type = type, quantity = quantity, rawText = rawText)
        }
        // Otherwise use the AI's estimated factor, if it supplied a valid one.
        val c = custom ?: return null
        val category = runCatching { Category.valueOf(c.category.uppercase()) }.getOrNull() ?: Category.HOME
        val unit = parseUnit(c.unit) ?: return null
        if (c.kgCo2PerUnit < 0) return null
        val factor = EmissionFactor(
            type = "ai:" + c.label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'),
            category = category,
            displayName = c.label,
            unit = unit,
            kgCo2PerUnit = c.kgCo2PerUnit,
            estimated = true,
        )
        return ParsedActivity(type = factor.type, quantity = quantity, rawText = rawText, customFactor = factor)
    }

    private fun parseUnit(raw: String): Unit? = when (raw.lowercase().trim()) {
        "km", "kms", "kilometre", "kilometer" -> Unit.KM
        "serving", "servings", "meal", "plate" -> Unit.SERVING
        "hr", "hour", "hours", "h" -> Unit.HOUR
        "kwh", "kw", "unit", "units" -> Unit.KWH
        else -> null
    }

    private fun buildPrompt(sentence: String): String = """
        You convert a person's plain-language description of their day into structured
        activities for a carbon-footprint app.

        Prefer these known activity types whenever one fits (use its exact key):
        $allowedTypes

        Rules:
        - "quantity" is the amount in the unit: km for transport, servings for food (one meal
          = 1), hours for appliances/devices, kWh for grid electricity.
        - Infer reasonable amounts from natural phrasing (e.g. "since waking up", "all
          morning" -> a realistic number of hours; a typical commute -> ~10 km).
        - If an activity matches a known type, set "type" to that key and omit "custom".
        - If NOTHING in the list fits (e.g. "Mac Mini", "gaming PC", a specific dish), leave
          "type" empty and provide "custom" with: a short "label", a "category" (one of
          TRANSPORT, FOOD, ELECTRICITY, HOME), a "unit" (km, serving, hr, or kWh), and your
          best realistic "kgCo2PerUnit" for India.
        - Only put a fragment in "unrecognized" if it truly isn't an activity with a footprint.

        Respond with ONLY this JSON shape:
        {"activities":[
          {"type":"ac","quantity":6,"rawText":"AC for 6 hours"},
          {"type":"","quantity":5,"rawText":"mac mini since waking up","custom":{"label":"Mac Mini + 2 monitors","category":"ELECTRICITY","unit":"hr","kgCo2PerUnit":0.09}}
        ],"unrecognized":[]}

        The person's day: "$sentence"
    """.trimIndent()

    @Serializable
    private data class GeminiResponse(
        val activities: List<GeminiActivity> = emptyList(),
        val unrecognized: List<String> = emptyList(),
    )

    @Serializable
    private data class GeminiActivity(
        val type: String = "",
        val quantity: Double,
        val rawText: String = "",
        val custom: Custom? = null,
    )

    @Serializable
    private data class Custom(
        val label: String,
        val category: String,
        val unit: String,
        val kgCo2PerUnit: Double,
    )
}
