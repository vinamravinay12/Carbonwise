package com.rivi.carbonwise.advisor

import com.rivi.carbonwise.ai.GeminiClient
import com.rivi.carbonwise.ai.GeminiTurn
import com.rivi.carbonwise.domain.Benchmarks
import com.rivi.carbonwise.domain.Footprint

/**
 * Generates the day's impact summary with Gemini, from the day's numbers and how it compares
 * to the previous day. A lighter/reduced day gets an encouraging summary; a heavier day gets
 * an honest read of what it means personally (cost, local air) and environmentally
 * (cumulative warming). Strictly grounded in the engine's numbers, with guardrails against
 * inventing figures or claiming a single day directly harms the individual.
 */
class GeminiImpactNarrator(
    apiKey: String,
    modelName: String = "gemini-2.5-flash",
) {
    private val client = GeminiClient(apiKey = apiKey, model = modelName)

    /** Returns the summary text, or null on failure (caller falls back to curated notes). */
    suspend fun narrate(footprint: Footprint, previousKg: Double?): String? {
        if (footprint.activities.isEmpty()) return null
        return client.generate(
            turns = listOf(GeminiTurn("user", buildPrompt(footprint, previousKg))),
            systemInstruction = SYSTEM_INSTRUCTION,
            temperature = 0.4,
        ).takeIf { it.isNotEmpty() }
    }

    private fun buildPrompt(footprint: Footprint, previousKg: Double?): String {
        val breakdown = footprint.byCategory.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key.label} ${fmt(it.value)} kg" }
        val top = footprint.byCategory.maxByOrNull { it.value }?.key?.label ?: "—"
        val band = when (Benchmarks.band(footprint.totalKg)) {
            Benchmarks.Band.LOW -> "below the climate-safe mark"
            Benchmarks.Band.AVERAGE -> "around the daily average"
            Benchmarks.Band.HIGH -> "above the daily average"
        }
        val trend = when {
            previousKg == null -> "No previous day to compare against."
            footprint.totalKg < previousKg ->
                "Down from the previous day's ${fmt(previousKg)} kg — a reduction of " +
                    "${fmt(previousKg - footprint.totalKg)} kg."
            footprint.totalKg > previousKg ->
                "Up from the previous day's ${fmt(previousKg)} kg — an increase of " +
                    "${fmt(footprint.totalKg - previousKg)} kg."
            else -> "About the same as the previous day (${fmt(previousKg)} kg)."
        }
        return """
            Today's footprint: ${fmt(footprint.totalKg)} kg CO₂.
            Breakdown: $breakdown. Biggest source: $top.
            Level: $band (climate-safe ≈ ${fmt(Benchmarks.TARGET_DAILY_KG)} kg/day, average ≈ ${fmt(Benchmarks.AVERAGE_DAILY_KG)} kg/day).
            $trend

            Write the impact summary now.
        """.trimIndent()
    }

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)

    private companion object {
        const val SYSTEM_INSTRUCTION = """
            You write a short, honest impact summary of a person's day of carbon emissions for a
            footprint app. 2 to 4 sentences, plain and warm, never preachy or alarmist.

            Rules:
            - Use ONLY the numbers provided; never invent or recompute figures.
            - NEVER claim this person's own emissions directly cause specific personal illness or
              disasters — climate impact is cumulative and collective. You MAY mention honest
              personal levers: cost (fuel/electricity bills) and local air pollution from vehicles,
              plus their share of a climate-safe budget.
            - If today is LOWER than the previous day, or below the climate-safe mark: write an
              encouraging, congratulatory summary that reinforces the habit and names what went well.
            - If today is HIGHER, or a heavy day: briefly explain what it means at a PERSONAL level
              (cost, local air) and an ENVIRONMENTAL level (adds to cumulative warming), then point
              gently at the biggest source as where to improve. Stay factual and non-judgmental.

            Reply with only the summary paragraph — no preamble, no headings, no bullet points.
        """
    }
}
