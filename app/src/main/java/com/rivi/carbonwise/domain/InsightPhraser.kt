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
        if (footprint.netKg <= 0.0 && footprint.avoidedKg > 0.0) {
            return "Carbon-positive day — you avoided more by going active than you emitted. Brilliant."
        }
        val top = footprint.byCategory.maxByOrNull { it.value }
        return when (Benchmarks.band(footprint.netKg)) {
            Benchmarks.Band.LOW ->
                "Lovely — a light day at ${fmt(footprint.netKg)} kg, below the climate-friendly mark."
            Benchmarks.Band.AVERAGE ->
                "A steady day at ${fmt(footprint.netKg)} kg." +
                    (top?.let { " ${it.key.label} was your biggest share." } ?: "")
            Benchmarks.Band.HIGH ->
                "Heavier day at ${fmt(footprint.netKg)} kg" +
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

    /** Plain-language verdict for a comparison: which option is worse and by how much. */
    fun comparisonHeadline(comparison: Comparison): String {
        val items = comparison.items
        val top = items.firstOrNull() ?: return "Tell me two things to compare."
        if (items.size < 2) {
            return "I only recognised ${top.factor.displayName.lowercase()} — name something to compare it with."
        }
        val bottom = items.last()
        val basis = "${fmt(top.quantity)} ${top.factor.unit.symbol}"
        if (bottom.kgCo2 <= 0.0) {
            return "${top.factor.displayName} emits ${fmt(top.kgCo2)} kg CO₂ over $basis, " +
                "while ${bottom.factor.displayName.lowercase()} emits none — an easy win."
        }
        val ratio = top.kgCo2 / bottom.kgCo2
        val moreText = if (ratio >= 1.95) {
            "${fmt(ratio)}× more"
        } else {
            "${fmt((ratio - 1) * 100)}% more"
        }
        return "${top.factor.displayName} emits $moreText CO₂ than " +
            "${bottom.factor.displayName.lowercase()} for the same $basis."
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
