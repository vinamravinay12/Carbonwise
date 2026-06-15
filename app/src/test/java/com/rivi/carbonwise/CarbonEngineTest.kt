package com.rivi.carbonwise

import com.rivi.carbonwise.domain.CarbonEngine
import com.rivi.carbonwise.domain.Category
import com.rivi.carbonwise.domain.ParsedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonEngineTest {

    private val engine = CarbonEngine()

    @Test
    fun `computes a single activity from its factor`() {
        val footprint = engine.compute(listOf(ParsedActivity("car_petrol", 15.0)))
        // 15 km * 0.192 = 2.88
        assertEquals(2.88, footprint.totalKg, 0.0001)
        assertEquals(2.88, footprint.byCategory[Category.TRANSPORT]!!, 0.0001)
    }

    @Test
    fun `same activity always yields the same number`() {
        val a = engine.compute(listOf(ParsedActivity("meal_chicken", 1.0))).totalKg
        val b = engine.compute(listOf(ParsedActivity("meal_chicken", 1.0))).totalKg
        assertEquals(a, b, 0.0)
        assertEquals(1.82, a, 0.0001)
    }

    @Test
    fun `aggregates across categories`() {
        val footprint = engine.compute(
            listOf(
                ParsedActivity("car_petrol", 15.0),   // 2.88 transport
                ParsedActivity("meal_chicken", 1.0),  // 1.82 food
                ParsedActivity("ac", 6.0),            // 6.30 electricity
            ),
        )
        assertEquals(2.88, footprint.byCategory[Category.TRANSPORT]!!, 0.0001)
        assertEquals(1.82, footprint.byCategory[Category.FOOD]!!, 0.0001)
        assertEquals(6.30, footprint.byCategory[Category.ELECTRICITY]!!, 0.0001)
        assertEquals(11.0, footprint.totalKg, 0.0001)
    }

    @Test
    fun `unknown activity types are skipped, not guessed`() {
        val footprint = engine.compute(
            listOf(
                ParsedActivity("teleportation", 99.0),
                ParsedActivity("meal_vegan", 1.0),
            ),
        )
        assertEquals(1, footprint.activities.size)
        assertEquals(0.42, footprint.totalKg, 0.0001)
    }

    @Test
    fun `best swap targets the largest emitter with an alternative`() {
        val footprint = engine.compute(
            listOf(
                ParsedActivity("car_petrol", 20.0),  // 3.84, biggest
                ParsedActivity("meal_chicken", 1.0), // 1.82
            ),
        )
        val swap = engine.bestSwap(footprint)
        assertNotNull(swap)
        assertEquals("car_petrol", swap!!.from.factor.type)
        assertEquals("metro", swap.toFactor.type)
        // 20*0.192 - 20*0.028 = 3.84 - 0.56 = 3.28
        assertEquals(3.28, swap.savingKg, 0.0001)
        assertTrue(swap.savingKg > 0)
        // Projections: 3.28 * 365 = 1197.2 kg/year; / 21 ≈ 57.01 trees
        assertEquals(1197.2, swap.savingKgPerYear, 0.01)
        assertEquals(57.01, swap.treesPerYear, 0.01)
        assertFalse("rule-based swap is not AI-chosen", swap.aiChosen)
    }

    @Test
    fun `computeSwapFor prices an AI-chosen alternative deterministically`() {
        val footprint = engine.compute(listOf(ParsedActivity("ac", 6.0))) // 6.30 kg
        val swap = engine.computeSwapFor(footprint, fromType = "ac", toType = "fan")
        assertNotNull(swap)
        // 6*1.05 = 6.30 ; fan 6*0.057 = 0.34 ; saving = 5.96
        assertEquals(5.96, swap!!.savingKg, 0.0001)
        assertTrue(swap.aiChosen)
    }

    @Test
    fun `computeSwapFor rejects nonsensical, non-helpful or absent swaps`() {
        val footprint = engine.compute(listOf(ParsedActivity("meal_vegan", 1.0)))
        // vegan -> beef would increase emissions, not save
        assertNull(engine.computeSwapFor(footprint, "meal_vegan", "meal_beef"))
        // unit mismatch (serving vs km) is rejected outright
        assertNull(engine.computeSwapFor(footprint, "meal_vegan", "car_petrol"))
        // the "from" activity isn't even in today's day
        assertNull(engine.computeSwapFor(footprint, "ac", "fan"))
    }

    @Test
    fun `no swap offered for an already-green day`() {
        val footprint = engine.compute(
            listOf(
                ParsedActivity("bicycle", 10.0),
                ParsedActivity("meal_vegan", 1.0),
            ),
        )
        // vegan -> nothing lower defined, bicycle has no alternative; saving must be > 0
        assertNull(engine.bestSwap(footprint))
    }
}
