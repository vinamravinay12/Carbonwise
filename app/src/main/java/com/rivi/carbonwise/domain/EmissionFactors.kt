package com.rivi.carbonwise.domain

/**
 * The one and only emission-factor table. Transparent, configurable, and the sole
 * source of every carbon number in the app. Values are illustrative kg CO2e per unit,
 * oriented to India; they are reasonable but not formally audited.
 */
object EmissionFactors {

    val table: List<EmissionFactor> = listOf(
        // ---- Transport (per km) ----
        EmissionFactor("car_petrol", Category.TRANSPORT, "Petrol car", Unit.KM, 0.192),
        EmissionFactor("car_diesel", Category.TRANSPORT, "Diesel car", Unit.KM, 0.171),
        EmissionFactor("car_electric", Category.TRANSPORT, "Electric car", Unit.KM, 0.053),
        EmissionFactor("motorbike", Category.TRANSPORT, "Motorbike", Unit.KM, 0.103),
        EmissionFactor("auto_rickshaw", Category.TRANSPORT, "Auto-rickshaw", Unit.KM, 0.107),
        EmissionFactor("bus", Category.TRANSPORT, "Bus", Unit.KM, 0.041),
        EmissionFactor("metro", Category.TRANSPORT, "Metro", Unit.KM, 0.028),
        EmissionFactor("train", Category.TRANSPORT, "Train", Unit.KM, 0.035),
        EmissionFactor("tram", Category.TRANSPORT, "Tram", Unit.KM, 0.029),
        EmissionFactor("ferry", Category.TRANSPORT, "Ferry", Unit.KM, 0.19),
        EmissionFactor("flight", Category.TRANSPORT, "Flight", Unit.KM, 0.246),
        EmissionFactor("bicycle", Category.TRANSPORT, "Bicycle", Unit.KM, 0.0),
        EmissionFactor("walk", Category.TRANSPORT, "Walking", Unit.KM, 0.0),

        // ---- Food (per serving / meal) ----
        EmissionFactor("meal_beef", Category.FOOD, "Beef meal", Unit.SERVING, 6.0),
        EmissionFactor("meal_lamb", Category.FOOD, "Lamb / mutton meal", Unit.SERVING, 5.8),
        EmissionFactor("meal_chicken", Category.FOOD, "Chicken meal", Unit.SERVING, 1.82),
        EmissionFactor("meal_fish", Category.FOOD, "Fish meal", Unit.SERVING, 1.34),
        EmissionFactor("meal_egg", Category.FOOD, "Egg meal", Unit.SERVING, 0.9),
        EmissionFactor("meal_vegetarian", Category.FOOD, "Vegetarian meal", Unit.SERVING, 0.68),
        EmissionFactor("meal_vegan", Category.FOOD, "Vegan meal", Unit.SERVING, 0.42),
        EmissionFactor("dairy", Category.FOOD, "Dairy (glass of milk etc.)", Unit.SERVING, 0.6),
        EmissionFactor("coffee", Category.FOOD, "Coffee", Unit.SERVING, 0.21),

        // ---- Electricity / appliances (per hour, grid-backed) ----
        EmissionFactor("ac", Category.ELECTRICITY, "Air conditioner", Unit.HOUR, 1.05),
        EmissionFactor("heater", Category.ELECTRICITY, "Electric heater", Unit.HOUR, 1.4),
        EmissionFactor("fan", Category.ELECTRICITY, "Fan", Unit.HOUR, 0.057),
        EmissionFactor("geyser", Category.ELECTRICITY, "Water heater / geyser", Unit.HOUR, 1.6),
        EmissionFactor("washing_machine", Category.ELECTRICITY, "Washing machine", Unit.HOUR, 0.41),
        EmissionFactor("tv", Category.ELECTRICITY, "Television", Unit.HOUR, 0.07),
        EmissionFactor("laptop", Category.ELECTRICITY, "Laptop", Unit.HOUR, 0.04),
        EmissionFactor("desktop_computer", Category.ELECTRICITY, "Desktop computer", Unit.HOUR, 0.08),
        EmissionFactor("monitor", Category.ELECTRICITY, "Monitor", Unit.HOUR, 0.03),

        // ---- Home / raw energy (per kWh) ----
        EmissionFactor("electricity_kwh", Category.HOME, "Grid electricity", Unit.KWH, 0.71),
        EmissionFactor("lpg", Category.HOME, "LPG cooking", Unit.HOUR, 0.79),
    )

    private val byType: Map<String, EmissionFactor> = table.associateBy { it.type }

    fun byType(type: String): EmissionFactor? = byType[type]

    /**
     * The baseline a zero-carbon trip is credited against — i.e. "what you'd have emitted
     * had you driven this distance instead." Uses the petrol-car factor (kg CO₂ per km).
     */
    val drivingBaselineKgPerKm: Double = byType["car_petrol"]?.kgCo2PerUnit ?: 0.192

    /**
     * Lower-impact alternatives for the "single best swap". Each maps an activity type
     * to a realistic, lower-emission substitute in the same need (commute, meal, cooling).
     */
    val swapAlternatives: Map<String, String> = mapOf(
        "car_petrol" to "metro",
        "car_diesel" to "metro",
        "motorbike" to "bus",
        "auto_rickshaw" to "bus",
        "flight" to "metro",
        "bus" to "bicycle",
        "meal_beef" to "meal_chicken",
        "meal_lamb" to "meal_chicken",
        "meal_chicken" to "meal_vegetarian",
        "meal_fish" to "meal_vegetarian",
        "meal_egg" to "meal_vegan",
        "meal_vegetarian" to "meal_vegan",
        "ac" to "fan",
        "heater" to "fan",
        "geyser" to "fan",
    )
}
