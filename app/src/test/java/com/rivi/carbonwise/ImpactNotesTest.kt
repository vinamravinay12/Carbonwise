package com.rivi.carbonwise

import com.rivi.carbonwise.domain.CarbonEngine
import com.rivi.carbonwise.domain.ImpactNotes
import com.rivi.carbonwise.domain.ParsedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpactNotesTest {

    private val engine = CarbonEngine()

    @Test
    fun `empty footprint yields no notes`() {
        assertTrue(ImpactNotes.forFootprint(engine.compute(emptyList())).isEmpty())
    }

    @Test
    fun `returns a band note plus a note for the biggest category`() {
        val footprint = engine.compute(listOf(ParsedActivity("car_petrol", 30.0))) // 5.76 kg, transport
        val notes = ImpactNotes.forFootprint(footprint)
        assertEquals(2, notes.size)
        assertTrue("expected a transport note", notes.any { it.contains("Getting around") })
    }
}
