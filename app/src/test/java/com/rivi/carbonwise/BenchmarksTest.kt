package com.rivi.carbonwise

import com.rivi.carbonwise.domain.Benchmarks
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarksTest {

    @Test
    fun `bands fall on the right side of each threshold`() {
        assertEquals(Benchmarks.Band.LOW, Benchmarks.band(0.0))
        assertEquals(Benchmarks.Band.LOW, Benchmarks.band(Benchmarks.TARGET_DAILY_KG)) // boundary inclusive
        assertEquals(Benchmarks.Band.AVERAGE, Benchmarks.band(Benchmarks.TARGET_DAILY_KG + 0.01))
        assertEquals(Benchmarks.Band.AVERAGE, Benchmarks.band(Benchmarks.AVERAGE_DAILY_KG)) // boundary inclusive
        assertEquals(Benchmarks.Band.HIGH, Benchmarks.band(Benchmarks.AVERAGE_DAILY_KG + 0.01))
    }

    @Test
    fun `a carbon-positive (negative net) day is low`() {
        assertEquals(Benchmarks.Band.LOW, Benchmarks.band(-1.5))
    }
}
