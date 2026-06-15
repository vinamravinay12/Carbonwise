package com.rivi.carbonwise.data

import android.util.Log
import com.rivi.carbonwise.advisor.SwapAdvisor
import com.rivi.carbonwise.domain.CarbonEngine
import com.rivi.carbonwise.domain.EmissionFactors
import com.rivi.carbonwise.domain.Footprint
import com.rivi.carbonwise.domain.InsightPhraser
import com.rivi.carbonwise.domain.ParsedActivity
import com.rivi.carbonwise.domain.Swap
import com.rivi.carbonwise.parser.ActivityParser
import com.rivi.carbonwise.recognition.DetectedKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
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

    // ---- Auto-tracking: Activity Recognition ----

    fun observePendingDetections(): Flow<List<DetectedTrip>> =
        detectedDao.observePending().map { list -> list.mapNotNull { it.toTrip() } }

    suspend fun getDetection(id: Long): DetectedTrip? = detectedDao.getById(id)?.toTrip()

    /** A movement of [kind] began at [startMillis]; open a segment, tidying stale ones. */
    suspend fun recordActivityEnter(kind: DetectedKind, startMillis: Long) {
        detectedDao.pruneStaleOpen(System.currentTimeMillis() - STALE_OPEN_MS)
        detectedDao.insert(
            DetectedSegmentEntity(
                kind = kind.name,
                startMillis = startMillis,
                endMillis = null,
                status = SegmentStatus.OPEN,
            ),
        )
    }

    /** A movement of [kind] ended; close the matching open segment into a PENDING trip. */
    suspend fun recordActivityExit(kind: DetectedKind, endMillis: Long): DetectedTrip? {
        val open = detectedDao.latestOpen(kind.name) ?: return null
        if (endMillis <= open.startMillis) return null
        detectedDao.close(open.id, endMillis, SegmentStatus.PENDING)
        return DetectedTrip(open.id, kind, open.startMillis, endMillis)
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
        val swap = bestSwap(footprint)
            ?.let { it.copy(message = InsightPhraser.swapMessage(it)) }

        val now = Instant.now()
        val entity = EntryEntity(
            epochDay = LocalDate.ofInstant(now, zoneId).toEpochDay(),
            createdAt = now.toEpochMilli(),
            sentence = sentence,
            totalKg = footprint.totalKg,
            footprintJson = json.encodeToString(Footprint.serializer(), footprint),
            swapJson = swap?.let { json.encodeToString(Swap.serializer(), it) },
            unrecognizedJson = json.encodeToString(
                ListSerializer(String.serializer()), unrecognized,
            ),
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
        val kind = DetectedKind.valueOfOrNull(kind) ?: return null
        val end = endMillis ?: return null
        return DetectedTrip(id = id, kind = kind, startMillis = startMillis, endMillis = end)
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format("%.1f", value)

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
    )

    private companion object {
        const val STALE_OPEN_MS = 6 * 60 * 60 * 1000L // 6 hours
    }
}
