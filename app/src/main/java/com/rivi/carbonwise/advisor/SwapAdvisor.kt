package com.rivi.carbonwise.advisor

import com.rivi.carbonwise.domain.Footprint

/** A swap the advisor recommends: replace [fromType] with the lower-carbon [toType]. */
data class SwapSuggestion(val fromType: String, val toType: String)

/**
 * Decides *which* activity is worth swapping and *to what* — the smart, context-aware
 * judgement call. It returns only activity-type names; the engine turns the choice into
 * actual kilograms. This keeps the "AI never produces numbers" principle intact while
 * letting the AI be genuinely clever about the recommendation.
 */
interface SwapAdvisor {
    suspend fun suggest(footprint: Footprint): SwapSuggestion?
}
