package com.rivi.carbonwise

import com.rivi.carbonwise.recognition.HeuristicVehicleClassifier
import com.rivi.carbonwise.recognition.TripFeatures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleModeClassifierTest {

    private val classifier = HeuristicVehicleClassifier

    private fun features(
        avg: Double,
        max: Double,
        stops: Int,
        gaps: Int,
    ) = TripFeatures(
        distanceKm = 10.0,
        durationMinutes = 30,
        avgSpeedKmh = avg,
        maxSpeedKmh = max,
        stopCount = stops,
        gpsGaps = gaps,
    )

    @Test
    fun `defaults to car when ambiguous (car prior)`() = runTest {
        assertEquals("car_petrol", classifier.classify(features(avg = 30.0, max = 50.0, stops = 2, gaps = 0)))
    }

    @Test
    fun `frequent stops at low speed lean bus`() = runTest {
        assertEquals("bus", classifier.classify(features(avg = 18.0, max = 40.0, stops = 6, gaps = 0)))
    }

    @Test
    fun `gps dropouts lean metro (tunnels)`() = runTest {
        assertEquals("metro", classifier.classify(features(avg = 35.0, max = 70.0, stops = 3, gaps = 3)))
    }

    @Test
    fun `smooth fast few-stops lean train`() = runTest {
        assertEquals("train", classifier.classify(features(avg = 45.0, max = 85.0, stops = 1, gaps = 0)))
    }
}
