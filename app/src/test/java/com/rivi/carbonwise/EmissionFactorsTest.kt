package com.rivi.carbonwise

import com.rivi.carbonwise.domain.EmissionFactors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmissionFactorsTest {

    @Test
    fun `every factor type is unique`() {
        val types = EmissionFactors.table.map { it.type }
        assertEquals("duplicate factor type(s) in the table", types.size, types.toSet().size)
    }

    @Test
    fun `byType looks up known and unknown types`() {
        assertNotNull(EmissionFactors.byType("car_petrol"))
        assertEquals(0.192, EmissionFactors.byType("car_petrol")!!.kgCo2PerUnit, 0.0)
        assertNull(EmissionFactors.byType("flux_capacitor"))
    }

    @Test
    fun `factors are never negative`() {
        assertTrue(EmissionFactors.table.all { it.kgCo2PerUnit >= 0.0 })
    }

    @Test
    fun `driving baseline matches the petrol-car factor`() {
        assertEquals(
            EmissionFactors.byType("car_petrol")!!.kgCo2PerUnit,
            EmissionFactors.drivingBaselineKgPerKm,
            0.0,
        )
    }

    @Test
    fun `every swap alternative is a known, lower-carbon, same-unit substitute`() {
        EmissionFactors.swapAlternatives.forEach { (from, to) ->
            val fromFactor = EmissionFactors.byType(from)
            val toFactor = EmissionFactors.byType(to)
            assertNotNull("swap 'from' $from missing from table", fromFactor)
            assertNotNull("swap 'to' $to missing from table", toFactor)
            assertEquals(
                "swap $from -> $to crosses units",
                fromFactor!!.unit,
                toFactor!!.unit,
            )
            assertTrue(
                "swap $from -> $to is not lower-carbon",
                toFactor.kgCo2PerUnit < fromFactor.kgCo2PerUnit,
            )
        }
    }
}
