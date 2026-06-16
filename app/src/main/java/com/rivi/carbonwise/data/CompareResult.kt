package com.rivi.carbonwise.data

/** One option in a comparison, with its (estimated) footprint and a short assumption note. */
data class CompareOption(
    val label: String,
    val kgCo2: Double,
    val detail: String,
)

/**
 * Result of a "which emits more?" question. [options] are sorted heaviest-first.
 * [aiEstimated] is true when Gemini produced the figures conversationally (e.g. for specific
 * car models the deterministic table can't price); false when they come from the engine's
 * audited factors. The UI badges AI-estimated results so the user knows the difference.
 */
data class CompareResult(
    val verdict: String,
    val options: List<CompareOption>,
    val aiEstimated: Boolean,
)
