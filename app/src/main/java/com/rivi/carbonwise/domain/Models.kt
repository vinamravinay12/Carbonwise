package com.rivi.carbonwise.domain

import kotlinx.serialization.Serializable

/**
 * The four buckets a footprint is broken down into. Kept deliberately small and
 * region-agnostic; UI metadata (colour, icon) lives in the UI layer, not here.
 */
enum class Category {
    TRANSPORT,
    FOOD,
    ELECTRICITY,
    HOME;

    val label: String
        get() = when (this) {
            TRANSPORT -> "Transport"
            FOOD -> "Food"
            ELECTRICITY -> "Electricity"
            HOME -> "Home"
        }
}

/** The unit a quantity is measured in for a given activity. */
enum class Unit(val symbol: String) {
    KM("km"),
    SERVING("serving"),
    HOUR("hr"),
    KWH("kWh");
}

/**
 * A single row of the transparent emission-factor table. Every kilogram of CO2 the
 * user ever sees traces back to one of these. Factors are illustrative and oriented
 * to India (see app spec, section 8).
 */
@Serializable
data class EmissionFactor(
    val type: String,          // stable key, e.g. "car_petrol"
    val category: Category,
    val displayName: String,   // human label, e.g. "Petrol car"
    val unit: Unit,
    val kgCo2PerUnit: Double,
)

/** What the parser produces: what was done and how much, nothing computed yet. */
@Serializable
data class ParsedActivity(
    val type: String,
    val quantity: Double,
    val rawText: String = "",
)

/** Result of parsing a sentence. Unrecognised fragments are surfaced, never guessed. */
data class ParseResult(
    val activities: List<ParsedActivity>,
    val unrecognized: List<String> = emptyList(),
)

/** A parsed activity after the engine has applied its factor. */
@Serializable
data class ComputedActivity(
    val factor: EmissionFactor,
    val quantity: Double,
    val kgCo2: Double,
    val rawText: String = "",
)

/**
 * The full deterministic result for one logged day. [avoidedKg] is the CO₂ the user
 * *avoided* by choosing zero-carbon active travel (walking/cycling) over driving the same
 * distance — a positive, motivating number, computed by the engine just like the rest.
 */
@Serializable
data class Footprint(
    val activities: List<ComputedActivity>,
    val totalKg: Double,
    val byCategory: Map<Category, Double>,
    val avoidedKg: Double = 0.0,
)

/**
 * The single most impactful, achievable change for this entry. The alternative may be
 * chosen by the AI (smarter, context-aware), but every figure here is real math from the
 * engine: today's saving, the same saving projected over a year, and the trees it equates
 * to. [aiChosen] notes whether the AI picked this swap. [message] is the assistant phrasing.
 */
@Serializable
data class Swap(
    val from: ComputedActivity,
    val toFactor: EmissionFactor,
    val savingKg: Double,
    val savingKgPerYear: Double = 0.0,
    val treesPerYear: Double = 0.0,
    val aiChosen: Boolean = false,
    val message: String = "",
)
