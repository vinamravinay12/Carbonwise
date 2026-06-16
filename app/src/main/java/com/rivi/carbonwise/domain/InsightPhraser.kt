package com.rivi.carbonwise.domain

/**
 * The friendly "assistant" layer. It turns numbers the engine already produced into a
 * warm, encouraging sentence. It does NO math — every figure passed in was computed by
 * [CarbonEngine]. Deterministic so the tone is reliable and offline-safe.
 */
object InsightPhraser {

    /** A one-line, supportive headline for the whole day. */
    fun dailyHeadline(footprint: Footprint): String {
        if (footprint.activities.isEmpty()) {
            return "Nothing logged yet — describe your day to see its footprint."
        }
        val top = footprint.byCategory.maxByOrNull { it.value }
        return when (Benchmarks.band(footprint.totalKg)) {
            Benchmarks.Band.LOW ->
                "Lovely — a light day at ${fmt(footprint.totalKg)} kg, below the climate-friendly mark."
            Benchmarks.Band.AVERAGE ->
                "A steady day at ${fmt(footprint.totalKg)} kg." +
                    (top?.let { " ${it.key.label} was your biggest share." } ?: "")
            Benchmarks.Band.HIGH ->
                "Heavier day at ${fmt(footprint.totalKg)} kg" +
                    (top?.let { ", mostly from ${it.key.label.lowercase()}." } ?: ".") +
                    " One small swap can pull it down."
        }
    }

    /** Phrases the single best swap into an actionable, achievable nudge. */
    fun swapMessage(swap: Swap): String {
        val from = swap.from.factor.displayName.lowercase()
        val to = swap.toFactor.displayName.lowercase()
        return "Swapping your ${from} for ${to} would save about " +
            "${fmt(swap.savingKg)} kg CO₂ — your single biggest win today."
    }

    /** Celebrates zero-carbon active travel by naming what driving would have cost. */
    fun avoidedNote(avoidedKg: Double): String =
        "By going active instead of driving, you kept about ${fmt(avoidedKg)} kg CO₂ " +
            "out of the air today. Every active trip counts."

    fun benchmarkNote(totalKg: Double): String = when (Benchmarks.band(totalKg)) {
        Benchmarks.Band.LOW -> "Below the ${fmt(Benchmarks.TARGET_DAILY_KG)} kg climate target. Keep it up."
        Benchmarks.Band.AVERAGE -> "Around the typical ${fmt(Benchmarks.AVERAGE_DAILY_KG)} kg daily average."
        Benchmarks.Band.HIGH -> "Above the ${fmt(Benchmarks.AVERAGE_DAILY_KG)} kg daily average for now."
    }

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format("%.1f", value)
}
