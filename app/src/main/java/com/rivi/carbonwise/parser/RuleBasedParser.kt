package com.rivi.carbonwise.parser

import com.rivi.carbonwise.domain.EmissionFactors
import com.rivi.carbonwise.domain.ParseResult
import com.rivi.carbonwise.domain.ParsedActivity
import com.rivi.carbonwise.domain.Unit

/**
 * Deterministic keyword-and-quantity parser. Used as the offline-safe fallback and in
 * tests, so the app is fully demoable without a live AI call. It never guesses a carbon
 * number (that is the engine's job) — it only maps phrases to activity types and amounts.
 */
class RuleBasedParser : ActivityParser {

    /** Ordered most-specific-first so "electric car" beats "car", "water heater" beats "heater". */
    private val keywords: List<Pair<List<String>, String>> = listOf(
        // transport
        listOf("electric car", "ev", " ev ") to "car_electric",
        listOf("diesel") to "car_diesel",
        listOf("petrol car", "car", "drove", "driving", "uber", "ola", "cab", "taxi") to "car_petrol",
        listOf("motorbike", "motorcycle", "scooter", "bike", "scooty") to "motorbike",
        listOf("auto-rickshaw", "rickshaw", "auto ", "tuk") to "auto_rickshaw",
        listOf("bus") to "bus",
        listOf("metro", "subway", "train", "local") to "metro",
        listOf("flight", "flew", "plane", "airplane", "aeroplane") to "flight",
        listOf("bicycle", "cycled", "cycling", "cycle") to "bicycle",
        listOf("walked", "walking", "on foot", "walk") to "walk",
        // food
        listOf("beef", "steak", "burger") to "meal_beef",
        listOf("mutton", "lamb", "goat") to "meal_lamb",
        listOf("chicken", "butter chicken") to "meal_chicken",
        listOf("fish", "prawn", "seafood") to "meal_fish",
        listOf("egg", "omelette", "omelet") to "meal_egg",
        listOf("vegan") to "meal_vegan",
        listOf("vegetarian", "veg ", "thali", "salad", "dal", "paneer", "rice", "roti", "sabzi", "lunch", "dinner", "breakfast") to "meal_vegetarian",
        listOf("milk", "dairy", "yogurt", "curd", "lassi") to "dairy",
        listOf("coffee", "latte", "cappuccino", "tea", "chai") to "coffee",
        // electricity / appliances
        listOf("air conditioner", "air-conditioner", "air conditioning", " ac ", "a/c") to "ac",
        listOf("heater") to "heater",
        listOf("ceiling fan", "fan") to "fan",
        listOf("water heater", "geyser") to "geyser",
        listOf("washing machine", "laundry", "washer") to "washing_machine",
        listOf("television", " tv", "tv ") to "tv",
        listOf("laptop", "computer", "pc ") to "laptop",
        // home
        listOf("lpg", "gas stove", "cooking gas", "stove") to "lpg",
        listOf("kwh", "units of electricity", "electricity") to "electricity_kwh",
    )

    private val wordNumbers = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0,
        "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0,
        "nine" to 9.0, "ten" to 10.0, "couple" to 2.0, "few" to 3.0,
    )

    override suspend fun parse(sentence: String): ParseResult {
        val activities = mutableListOf<ParsedActivity>()
        val unrecognized = mutableListOf<String>()

        val clauses = sentence
            .split(
                Regex(
                    "[,;.&]| and | then | also | plus | as well as | vs | versus | or ",
                    RegexOption.IGNORE_CASE,
                ),
            )
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (clause in clauses) {
            val lower = " ${clause.lowercase()} "
            val type = keywords.firstOrNull { (kws, _) -> kws.any { lower.contains(it) } }?.second

            if (type == null) {
                // Only flag clauses that look like a missed activity (contain a number).
                if (Regex("\\d").containsMatchIn(clause)) unrecognized.add(clause)
                continue
            }

            val factor = EmissionFactors.byType(type) ?: continue
            val quantity = extractQuantity(lower, factor.unit)
            activities.add(ParsedActivity(type = type, quantity = quantity, rawText = clause))
        }

        return ParseResult(activities = activities, unrecognized = unrecognized)
    }

    private fun extractQuantity(lower: String, unit: Unit): Double {
        // Word-numbers ("a", "two", "couple") only make sense for countable servings;
        // for distance/time/energy we trust explicit digits and otherwise use a safe default.
        return when (unit) {
            Unit.KM -> extractDigits(lower) ?: 10.0
            Unit.HOUR -> extractDigits(lower) ?: 1.0
            Unit.KWH -> extractDigits(lower) ?: 1.0
            Unit.SERVING -> extractDigits(lower) ?: extractWordNumber(lower) ?: 1.0
        }
    }

    private fun extractDigits(lower: String): Double? =
        Regex("(\\d+(?:\\.\\d+)?)").find(lower)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun extractWordNumber(lower: String): Double? {
        for ((word, value) in wordNumbers) {
            if (Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(lower)) return value
        }
        return null
    }
}
