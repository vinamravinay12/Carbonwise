package com.rivi.carbonwise.data

import android.util.Log
import com.rivi.carbonwise.advisor.GeminiComparator
import com.rivi.carbonwise.advisor.GeminiImpactNarrator
import com.rivi.carbonwise.advisor.SwapAdvisor
import com.rivi.carbonwise.domain.CarbonEngine
import com.rivi.carbonwise.domain.EmissionFactors
import com.rivi.carbonwise.domain.Footprint
import com.rivi.carbonwise.domain.InsightPhraser
import com.rivi.carbonwise.domain.ParsedActivity
import com.rivi.carbonwise.domain.Swap
import com.rivi.carbonwise.parser.ActivityParser
import com.rivi.carbonwise.recognition.DetectedKind
import com.rivi.carbonwise.recognition.HeuristicVehicleClassifier
import com.rivi.carbonwise.recognition.TripFeatures
import com.rivi.carbonwise.recognition.VehicleModeClassifier
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/**
 * Orchestrates the full pipeline — parse (AI), compute (engine), phrase (assistant),
 * persist (Room) — and exposes domain-level [LoggedDay]s to the rest of the app. Also
 * owns the auto-tracking detection lifecycle: open/close segments and confirm them into
 * real entries (the user supplies the amount, so no quantity is ever guessed).
 */
class CarbonRepository(
    private val dao: EntryDao,
    private val parser: ActivityParser,
    private val detectedDao: DetectedSegmentDao,
    private val engine: CarbonEngine = CarbonEngine(),
    private val swapAdvisor: SwapAdvisor? = null,
    private val vehicleClassifier: VehicleModeClassifier = HeuristicVehicleClassifier,
    private val comparator: GeminiComparator? = null,
    private val impactNarrator: GeminiImpactNarrator? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeHistory(): Flow<List<LoggedDay>> =
        dao.observeAll().map { list -> list.map { it.toLoggedDay() } }

    suspend fun getById(id: Long): LoggedDay? = dao.getById(id)?.toLoggedDay()

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** The manual action: a sentence in, a fully computed & saved day out. */
    suspend fun logDay(sentence: String): LoggedDay {
        val parsed = parser.parse(sentence)
        return logActivities(sentence, parsed.activities, parsed.unrecognized)
    }

    /**
     * One turn of the Compare conversation. With Gemini it's a stateful chat (follow-ups like
     * "why?" keep context) returning AI estimates; without a key it falls back to a one-shot
     * deterministic comparison over known categories. Nothing is saved — it's exploratory.
     */
    suspend fun askComparison(
        message: String,
        imageBase64: String? = null,
        imageMime: String? = null,
    ): CompareReply {
        comparator?.let { ai ->
            try {
                return ai.send(message, imageBase64, imageMime)
            } catch (e: Exception) {
                Log.w("CarbonWise", "AI comparator failed, using engine: ${e.message}")
            }
        }
        if (imageBase64 != null) {
            return CompareReply("I can only analyse images with a Gemini API key configured.", null)
        }
        // Deterministic fallback over known categories (no conversation memory).
        val parsed = parser.parse(message)
        val comparison = engine.compare(parsed.activities)
        if (comparison.items.size < 2) {
            return CompareReply(
                reply = "Add a Gemini API key to ask freely. Offline, I can compare known " +
                    "categories — e.g. \"petrol car vs metro for 20 km\".",
                result = null,
            )
        }
        val result = CompareResult(
            verdict = InsightPhraser.comparisonHeadline(comparison),
            options = comparison.items.map {
                CompareOption(
                    label = it.factor.displayName,
                    kgCo2 = it.kgCo2,
                    detail = "${formatNumber(it.quantity)} ${it.factor.unit.symbol} × " +
                        "${formatNumber(it.factor.kgCo2PerUnit)} kg/${it.factor.unit.symbol}",
                )
            },
            aiEstimated = false,
        )
        return CompareReply(reply = result.verdict, result = result)
    }

    /** Start a fresh Compare conversation. */
    fun resetComparison() = comparator?.reset()

    // ---- Auto-tracking: Activity Recognition ----

    fun observePendingDetections(): Flow<List<DetectedTrip>> =
        detectedDao.observePending().map { list -> list.mapNotNull { it.toTrip() } }

    suspend fun getDetection(id: Long): DetectedTrip? = detectedDao.getById(id)?.toTrip()

    /** A movement of [kind] began; open a segment (tidying stale ones) and return its id. */
    suspend fun recordActivityEnter(kind: DetectedKind, startMillis: Long): Long {
        detectedDao.pruneStaleOpen(System.currentTimeMillis() - STALE_OPEN_MS)
        return detectedDao.insert(
            DetectedSegmentEntity(
                kind = kind.name,
                startMillis = startMillis,
                endMillis = null,
                status = SegmentStatus.OPEN,
            ),
        )
    }

    /** Called by the location service as a trip progresses. */
    suspend fun updateTripMetrics(
        segmentId: Long,
        distanceMeters: Double,
        avgSpeedKmh: Double,
        maxSpeedKmh: Double,
        stopCount: Int,
        gpsGaps: Int,
    ) = detectedDao.updateMetrics(segmentId, distanceMeters, avgSpeedKmh, maxSpeedKmh, stopCount, gpsGaps)

    /**
     * A movement of [kind] ended; close the matching open segment into a PENDING trip and,
     * for a vehicle with GPS metrics, classify the most likely mode to pre-select.
     */
    suspend fun recordActivityExit(kind: DetectedKind, endMillis: Long): DetectedTrip? {
        val open = detectedDao.latestOpen(kind.name) ?: return null
        if (endMillis <= open.startMillis) return null
        detectedDao.close(open.id, endMillis, SegmentStatus.PENDING)

        val closed = detectedDao.getById(open.id) ?: return null
        val suggested = suggestMode(kind, closed, endMillis)
        if (suggested != null) detectedDao.setSuggestedType(open.id, suggested)

        return DetectedTrip(
            id = open.id,
            kind = kind,
            startMillis = open.startMillis,
            endMillis = endMillis,
            distanceKm = closed.distanceMeters?.let { it / 1000.0 },
            suggestedType = suggested,
        )
    }

    private suspend fun suggestMode(
        kind: DetectedKind,
        segment: DetectedSegmentEntity,
        endMillis: Long,
    ): String? {
        if (kind != DetectedKind.VEHICLE) return kind.defaultType
        val distanceMeters = segment.distanceMeters ?: return kind.defaultType
        val features = TripFeatures(
            distanceKm = distanceMeters / 1000.0,
            durationMinutes = ((endMillis - segment.startMillis) / 60_000L).coerceAtLeast(1),
            avgSpeedKmh = segment.avgSpeedKmh ?: 0.0,
            maxSpeedKmh = segment.maxSpeedKmh ?: 0.0,
            stopCount = segment.stopCount ?: 0,
            gpsGaps = segment.gpsGaps ?: 0,
        )
        return vehicleClassifier.classify(features)
    }

    /** Turn a detected trip into a real entry once the user confirms mode + amount. */
    suspend fun confirmDetection(id: Long, factorType: String, quantity: Double): LoggedDay {
        val factor = EmissionFactors.byType(factorType)
            ?: error("Unknown factor type: $factorType")
        val sentence = "Auto-tracked: ${formatNumber(quantity)} ${factor.unit.symbol} · ${factor.displayName}"
        val day = logActivities(sentence, listOf(ParsedActivity(factorType, quantity)), emptyList())
        detectedDao.setStatus(id, SegmentStatus.CONFIRMED)
        return day
    }

    suspend fun dismissDetection(id: Long) = detectedDao.setStatus(id, SegmentStatus.DISMISSED)

    // ---- Shared compute + persist ----

    private suspend fun logActivities(
        sentence: String,
        activities: List<ParsedActivity>,
        unrecognized: List<String>,
    ): LoggedDay {
        val footprint = engine.compute(activities)
        val previousKg = dao.latest()?.totalKg

        // The swap suggestion and the impact summary each hit the network; run them together.
        val (swap, narrative) = coroutineScope {
            val swapJob = async {
                bestSwap(footprint)?.let { it.copy(message = InsightPhraser.swapMessage(it)) }
            }
            val narrativeJob = async {
                impactNarrator?.let {
                    runCatching { it.narrate(footprint, previousKg) }
                        .onFailure { e -> Log.w("CarbonWise", "Impact narrator failed: ${e.message}") }
                        .getOrNull()
                }
            }
            swapJob.await() to narrativeJob.await()
        }

        val now = Instant.now()
        val entity = EntryEntity(
            epochDay = now.atZone(zoneId).toLocalDate().toEpochDay(),
            createdAt = now.toEpochMilli(),
            sentence = sentence,
            totalKg = footprint.totalKg,
            footprintJson = json.encodeToString(Footprint.serializer(), footprint),
            swapJson = swap?.let { json.encodeToString(Swap.serializer(), it) },
            unrecognizedJson = json.encodeToString(
                ListSerializer(String.serializer()), unrecognized,
            ),
            impactNarrative = narrative,
        )
        val id = dao.insert(entity)
        return entity.copy(id = id).toLoggedDay()
    }

    /**
     * Let the AI advisor pick the smartest swap; the engine prices it. If there's no
     * advisor, the AI returns nothing usable, or the call fails, fall back to the
     * deterministic rule-based swap so the insight is always present.
     */
    private suspend fun bestSwap(footprint: Footprint): Swap? {
        // Nothing emitted (e.g. a walk/cycle) → nothing to swap; skip the network call.
        if (footprint.totalKg <= 0.0) return null
        swapAdvisor?.let { advisor ->
            try {
                val suggestion = advisor.suggest(footprint)
                if (suggestion != null) {
                    engine.computeSwapFor(footprint, suggestion.fromType, suggestion.toType)
                        ?.let { return it }
                }
            } catch (e: Exception) {
                Log.w("CarbonWise", "Swap advisor failed, using rule-based swap: ${e.message}")
            }
        }
        return engine.bestSwap(footprint)
    }

    private fun DetectedSegmentEntity.toTrip(): DetectedTrip? {
        val detectedKind = DetectedKind.valueOfOrNull(kind) ?: return null
        val end = endMillis ?: return null
        return DetectedTrip(
            id = id,
            kind = detectedKind,
            startMillis = startMillis,
            endMillis = end,
            distanceKm = distanceMeters?.let { it / 1000.0 },
            suggestedType = suggestedType ?: detectedKind.defaultType,
        )
    }

    private fun formatNumber(value: Double): String =
        com.rivi.carbonwise.domain.formatAmount(value)

    private fun EntryEntity.toLoggedDay(): LoggedDay = LoggedDay(
        id = id,
        epochDay = epochDay,
        createdAt = createdAt,
        sentence = sentence,
        footprint = json.decodeFromString(Footprint.serializer(), footprintJson),
        swap = swapJson?.let { json.decodeFromString(Swap.serializer(), it) },
        unrecognized = json.decodeFromString(
            ListSerializer(String.serializer()), unrecognizedJson,
        ),
        impactNarrative = impactNarrative,
    )

    private companion object {
        const val STALE_OPEN_MS = 6 * 60 * 60 * 1000L // 6 hours
    }
}
