package com.rivi.carbonwise

import com.rivi.carbonwise.parser.RuleBasedParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedParserTest {

    private val parser = RuleBasedParser()

    @Test
    fun `parses the canonical example sentence`() = runTest {
        val result = parser.parse(
            "Drove 15 km to work, had a chicken thali for lunch, ran the AC for 6 hours.",
        )
        val byType = result.activities.associateBy { it.type }

        assertTrue(byType.containsKey("car_petrol"))
        assertEquals(15.0, byType["car_petrol"]!!.quantity, 0.0)

        assertTrue(byType.containsKey("meal_chicken"))
        assertEquals(1.0, byType["meal_chicken"]!!.quantity, 0.0)

        assertTrue(byType.containsKey("ac"))
        assertEquals(6.0, byType["ac"]!!.quantity, 0.0)
    }

    @Test
    fun `applies safe defaults when a distance is not stated`() = runTest {
        val result = parser.parse("Took the metro to office")
        val metro = result.activities.first { it.type == "metro" }
        assertEquals(10.0, metro.quantity, 0.0)
    }

    @Test
    fun `flags a fragment with a number it cannot place`() = runTest {
        val result = parser.parse("Bought 3 widgets")
        assertTrue(result.activities.isEmpty())
        assertTrue(result.unrecognized.isNotEmpty())
    }

    @Test
    fun `prefers the more specific keyword`() = runTest {
        val result = parser.parse("Drove my electric car 30 km")
        assertEquals("car_electric", result.activities.single().type)
        assertEquals(30.0, result.activities.single().quantity, 0.0)
    }
}
