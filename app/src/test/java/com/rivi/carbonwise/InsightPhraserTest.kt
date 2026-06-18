package com.rivi.carbonwise

import com.rivi.carbonwise.domain.CarbonEngine
import com.rivi.carbonwise.domain.InsightPhraser
import com.rivi.carbonwise.domain.ParsedActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightPhraserTest {

    private val engine = CarbonEngine()

    @Test
    fun `daily headline flags a heavier day`() {
        val footprint = engine.compute(listOf(ParsedActivity("car_petrol", 40.0))) // 7.68 kg
        val headline = InsightPhraser.dailyHeadline(footprint)
        assertTrue(headline.contains("Heavier day"))
    }

    @Test
    fun `daily headline celebrates a carbon-positive day`() {
        val footprint = engine.compute(listOf(ParsedActivity("bicycle", 10.0))) // 0 emitted, 1.92 avoided
        val headline = InsightPhraser.dailyHeadline(footprint)
        assertTrue(headline.contains("Carbon-positive"))
    }

    @Test
    fun `empty footprint prompts the user to log`() {
        val headline = InsightPhraser.dailyHeadline(engine.compute(emptyList()))
        assertTrue(headline.contains("Nothing logged"))
    }

    @Test
    fun `swap message names both modes and the saving`() {
        val footprint = engine.compute(listOf(ParsedActivity("car_petrol", 20.0)))
        val swap = engine.computeSwapFor(footprint, "car_petrol", "metro")!!
        val message = InsightPhraser.swapMessage(swap)
        assertTrue(message.contains("petrol car", ignoreCase = true))
        assertTrue(message.contains("metro", ignoreCase = true))
        assertTrue(message.contains("save", ignoreCase = true))
    }

    @Test
    fun `comparison headline says one emits more`() {
        val comparison = engine.compare(
            listOf(ParsedActivity("meal_beef", 1.0), ParsedActivity("meal_chicken", 1.0)),
        )
        assertTrue(InsightPhraser.comparisonHeadline(comparison).contains("more"))
    }

    @Test
    fun `comparison headline highlights a zero-carbon option`() {
        val comparison = engine.compare(
            listOf(ParsedActivity("car_petrol", 10.0), ParsedActivity("walk", 10.0)),
        )
        assertTrue(InsightPhraser.comparisonHeadline(comparison).contains("emits none"))
    }

    @Test
    fun `avoided note names the saving`() {
        assertTrue(InsightPhraser.avoidedNote(1.9).contains("1.9"))
    }
}
