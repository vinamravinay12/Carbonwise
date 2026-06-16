package com.rivi.carbonwise.domain

import kotlin.math.roundToInt

/**
 * Pure, deterministic calculation engine. Given parsed activities it produces a
 * [Footprint] and the single best [Swap]. It never estimates or invents a number —
 * everything is `quantity * factor` from [EmissionFactors]. Fully unit-testable; no
 * Android, network, or AI dependencies.
 */
class CarbonEngine(
    private val factors: (String) -> EmissionFactor? = EmissionFactors::byType,
    private val alternatives: Map<String, String> = EmissionFactors.swapAlternatives,
    private val drivingBaselineKgPerKm: Double = EmissionFactors.drivingBaselineKgPerKm,
) {

    /** Apply factors to parsed activities. Unknown types are silently skipped. */
    fun compute(parsed: List<ParsedActivity>): Footprint {
        val computed = parsed.mapNotNull { a ->
            val factor = factors(a.type) ?: return@mapNotNull null
            val kg = round(a.quantity * factor.kgCo2PerUnit)
            ComputedActivity(
                factor = factor,
                quantity = a.quantity,
                kgCo2 = kg,
                rawText = a.rawText,
            )
        }

        val byCategory = computed
            .groupBy { it.factor.category }
            .mapValues { (_, items) -> round(items.sumOf { it.kgCo2 }) }

        val total = round(computed.sumOf { it.kgCo2 })
        return Footprint(
            activities = computed,
            totalKg = total,
            byCategory = byCategory,
            avoidedKg = avoidedByActiveTravel(computed),
        )
    }

    /**
     * CO₂ avoided by zero-carbon travel (walking/cycling): the distance, had it been driven,
     * would have cost `distance × driving baseline`. Only credits transport activities that
     * are measured in km and emit nothing themselves.
     */
    private fun avoidedByActiveTravel(activities: List<ComputedActivity>): Double {
        val avoided = activities
            .filter {
                it.factor.category == Category.TRANSPORT &&
                    it.factor.unit == Unit.KM &&
                    it.factor.kgCo2PerUnit == 0.0
            }
            .sumOf { it.quantity * drivingBaselineKgPerKm }
        return round(avoided)
    }

    /**
     * Deterministic fallback: take the highest-emitting activity that has a defined
     * lower-impact alternative and build the swap. Used when no AI advisor is available.
     * Returns null when there is nothing worth swapping (e.g. an all-green day).
     */
    fun bestSwap(footprint: Footprint): Swap? {
        val candidates = footprint.activities
            .filter { alternatives.containsKey(it.factor.type) }
            .sortedByDescending { it.kgCo2 }

        for (activity in candidates) {
            val altType = alternatives[activity.factor.type] ?: continue
            buildSwap(activity, altType, aiChosen = false)?.let { return it }
        }
        return null
    }

    /**
     * Build a swap for a specific (from → to) chosen elsewhere — e.g. by the AI advisor.
     * The choice of *what* to swap may be the AI's; the numbers are always the engine's.
     * Returns null if the activity isn't in the day, the type is unknown, or it wouldn't help.
     */
    fun computeSwapFor(footprint: Footprint, fromType: String, toType: String): Swap? {
        val from = footprint.activities.firstOrNull { it.factor.type == fromType } ?: return null
        return buildSwap(from, toType, aiChosen = true)
    }

    private fun buildSwap(from: ComputedActivity, toType: String, aiChosen: Boolean): Swap? {
        val toFactor = factors(toType) ?: return null
        if (toFactor.unit != from.factor.unit) return null // must substitute like-for-like
        val altKg = round(from.quantity * toFactor.kgCo2PerUnit)
        val saving = round(from.kgCo2 - altKg)
        if (saving <= 0.05) return null

        val savingPerYear = round(saving * 365)
        val trees = round(savingPerYear / Benchmarks.TREE_KG_PER_YEAR)
        return Swap(
            from = from,
            toFactor = toFactor,
            savingKg = saving,
            savingKgPerYear = savingPerYear,
            treesPerYear = trees,
            aiChosen = aiChosen,
        )
    }

    private fun round(value: Double): Double = (value * 100).roundToInt() / 100.0
}
