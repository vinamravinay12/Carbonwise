package com.rivi.carbonwise.domain

/**
 * Reference points that give the raw kilograms meaning. Illustrative daily figures
 * for an individual in India; kept here so they are transparent and configurable.
 */
object Benchmarks {
    /** Roughly the current average personal footprint per day (India, illustrative). */
    const val AVERAGE_DAILY_KG = 5.0

    /** A climate-target-aligned daily footprint (≈1.5°C pathway, illustrative). */
    const val TARGET_DAILY_KG = 2.5

    /** CO₂ a mature tree absorbs in a year, for relatable swap projections (illustrative). */
    const val TREE_KG_PER_YEAR = 21.0

    enum class Band { LOW, AVERAGE, HIGH }

    fun band(totalKg: Double): Band = when {
        totalKg <= TARGET_DAILY_KG -> Band.LOW
        totalKg <= AVERAGE_DAILY_KG -> Band.AVERAGE
        else -> Band.HIGH
    }
}
