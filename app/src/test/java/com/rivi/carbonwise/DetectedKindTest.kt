package com.rivi.carbonwise

import com.rivi.carbonwise.recognition.DetectedKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedKindTest {

    @Test
    fun `estimates active-travel distance from duration and typical speed`() {
        assertEquals(5.0, DetectedKind.WALK.estimatedDistanceKm(60)!!, 0.0)    // 5 km/h for 1 h
        assertEquals(2.5, DetectedKind.WALK.estimatedDistanceKm(30)!!, 0.0)
        assertEquals(15.0, DetectedKind.BICYCLE.estimatedDistanceKm(60)!!, 0.0)
        assertEquals(9.0, DetectedKind.RUN.estimatedDistanceKm(60)!!, 0.0)
    }

    @Test
    fun `vehicle distance is not estimated (too variable)`() {
        assertNull(DetectedKind.VEHICLE.estimatedDistanceKm(60))
    }

    @Test
    fun `vehicle default mode is car and offers the full mode set`() {
        assertEquals("car_petrol", DetectedKind.VEHICLE.defaultType)
        assertTrue(
            DetectedKind.VEHICLE.candidateTypes.containsAll(
                listOf("car_petrol", "bus", "metro", "train", "tram", "ferry"),
            ),
        )
    }

    @Test
    fun `valueOfOrNull is null-safe`() {
        assertEquals(DetectedKind.WALK, DetectedKind.valueOfOrNull("WALK"))
        assertNull(DetectedKind.valueOfOrNull("TELEPORT"))
    }
}
