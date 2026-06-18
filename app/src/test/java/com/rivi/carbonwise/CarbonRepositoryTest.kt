package com.rivi.carbonwise

import com.rivi.carbonwise.data.CarbonRepository
import com.rivi.carbonwise.domain.ParsedActivity
import com.rivi.carbonwise.recognition.DetectedKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the repository's orchestration (parse → compute → persist → swap fallback) and
 * the auto-tracking lifecycle, using in-memory fakes — no Room, no network, no Android.
 */
class CarbonRepositoryTest {

    private fun repository(parser: FakeParser) = CarbonRepository(
        dao = FakeEntryDao(),
        parser = parser,
        detectedDao = FakeDetectedSegmentDao(),
        // engine default; no swapAdvisor / comparator / impactNarrator (deterministic path)
    )

    @Test
    fun `logDay computes, persists, and surfaces the entry in history`() = runTest {
        val repo = repository(FakeParser(ParsedActivity("car_petrol", 10.0)))

        val day = repo.logDay("drove 10 km")

        assertEquals(1.92, day.footprint.totalKg, 0.0001)
        val history = repo.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals(day.id, history.first().id)
    }

    @Test
    fun `logDay falls back to the deterministic best swap when no AI advisor`() = runTest {
        val repo = repository(FakeParser(ParsedActivity("car_petrol", 20.0)))

        val day = repo.logDay("drove 20 km")

        assertNotNull("expected a rule-based swap", day.swap)
        assertEquals("metro", day.swap!!.toFactor.type)
        assertTrue(day.swap!!.savingKg > 0)
    }

    @Test
    fun `net footprint reflects emissions avoided by active travel`() = runTest {
        val repo = repository(FakeParser(ParsedActivity("bicycle", 10.0)))

        val day = repo.logDay("cycled 10 km")

        assertEquals(0.0, day.footprint.totalKg, 0.0001)
        assertEquals(1.92, day.footprint.avoidedKg, 0.0001)
        assertEquals(-1.92, day.footprint.netKg, 0.0001)
    }

    @Test
    fun `delete removes an entry from history`() = runTest {
        val repo = repository(FakeParser(ParsedActivity("meal_chicken", 1.0)))
        val day = repo.logDay("chicken")

        repo.delete(day.id)

        assertTrue(repo.observeHistory().first().isEmpty())
    }

    @Test
    fun `a detected trip opens, closes to pending, and confirms into an entry`() = runTest {
        val repo = repository(FakeParser())

        val segmentId = repo.recordActivityEnter(DetectedKind.VEHICLE, startMillis = 1_000L)
        val trip = repo.recordActivityExit(DetectedKind.VEHICLE, endMillis = 2_000L)

        assertNotNull(trip)
        assertEquals(segmentId, trip!!.id)
        assertEquals(1, repo.observePendingDetections().first().size)

        val day = repo.confirmDetection(segmentId, factorType = "bus", quantity = 5.0)

        assertEquals(0.21, day.footprint.totalKg, 0.0001) // 5 km × 0.041 = 0.205, rounded to 0.21
        assertTrue("confirmed trip should leave the pending list", repo.observePendingDetections().first().isEmpty())
    }

    @Test
    fun `dismissing a detection clears it from pending`() = runTest {
        val repo = repository(FakeParser())
        val id = repo.recordActivityEnter(DetectedKind.WALK, startMillis = 1_000L)
        repo.recordActivityExit(DetectedKind.WALK, endMillis = 2_000L)

        repo.dismissDetection(id)

        assertTrue(repo.observePendingDetections().first().isEmpty())
    }

    @Test
    fun `exit before a matching enter yields no trip`() = runTest {
        val repo = repository(FakeParser())
        assertNull(repo.recordActivityExit(DetectedKind.VEHICLE, endMillis = 5_000L))
    }

    @Test
    fun `comparison falls back to the engine over known categories without a key`() = runTest {
        val repo = repository(
            FakeParser(ParsedActivity("car_petrol", 10.0), ParsedActivity("metro", 10.0)),
        )

        val reply = repo.askComparison("petrol car vs metro")

        assertNotNull(reply.result)
        assertEquals(2, reply.result!!.options.size)
        assertTrue("engine fallback is not an AI estimate", !reply.result!!.aiEstimated)
    }
}
