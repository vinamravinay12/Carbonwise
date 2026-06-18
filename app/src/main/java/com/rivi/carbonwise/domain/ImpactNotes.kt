package com.rivi.carbonwise.domain

/**
 * Curated, hand-written "what this means" notes — factual and measured, never alarmist and
 * never claiming a single day directly harms the individual (climate impact is cumulative
 * and collective). Selected deterministically from the day's band and biggest category, so
 * they're consistent and need no AI. Each pairs a planet truth with a real personal lever.
 */
object ImpactNotes {

    fun forFootprint(footprint: Footprint): List<String> {
        if (footprint.activities.isEmpty()) return emptyList()
        val notes = mutableListOf<String>()
        notes += bandNote(Benchmarks.band(footprint.totalKg))
        footprint.byCategory.maxByOrNull { it.value }?.key?.let { notes += categoryNote(it) }
        return notes
    }

    private fun bandNote(band: Benchmarks.Band): String = when (band) {
        Benchmarks.Band.LOW ->
            "A light day, below the climate-aligned mark. CO₂ stays in the air for centuries, " +
                "so it's the running total over years that matters — days like this, repeated, " +
                "are what a 1.5°C-compatible lifestyle looks like."
        Benchmarks.Band.AVERAGE ->
            "A fairly typical day. Most of a footprint lives in everyday routine, not one-off " +
                "events — which is why small, repeatable swaps add up faster than they seem."
        Benchmarks.Band.HIGH ->
            "A heavier day. No single day changes the climate, but the cumulative total does, " +
                "so steady trimming beats occasional big effort. One focused swap is the place to start."
    }

    private fun categoryNote(category: Category): String = when (category) {
        Category.TRANSPORT ->
            "Getting around was your biggest source today. Beyond CO₂, vehicle exhaust adds " +
                "local air pollution — a direct, everyday health factor in Indian cities — and " +
                "fuel is a recurring cost fully in your control."
        Category.ELECTRICITY ->
            "Electricity led today. India's grid is still largely coal-fired, so each unit carries " +
                "real CO₂ — and it lands on your monthly bill, so cutting use saves money too."
        Category.FOOD ->
            "Food was your biggest share. Meat and dairy are land-, water- and emissions-intensive; " +
                "shifting even a few meals lighter eases all three at once."
        Category.HOME ->
            "Home energy and cooking led today. Efficient appliances, shorter water-heating and " +
                "lids-on cooking trim both the fuel used and what you pay for it."
    }
}
