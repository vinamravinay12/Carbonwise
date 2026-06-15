package com.rivi.carbonwise.data

import android.util.Log
import com.rivi.carbonwise.advisor.SwapAdvisor
import com.rivi.carbonwise.domain.CarbonEngine
import com.rivi.carbonwise.domain.Footprint
import com.rivi.carbonwise.domain.InsightPhraser
import com.rivi.carbonwise.domain.Swap
import com.rivi.carbonwise.parser.ActivityParser
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
 * persist (Room) — and exposes domain-level [LoggedDay]s to the rest of the app.
 */
class CarbonRepository(
    private val dao: EntryDao,
    private val parser: ActivityParser,
    private val engine: CarbonEngine = CarbonEngine(),
    private val swapAdvisor: SwapAdvisor? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeHistory(): Flow<List<LoggedDay>> =
        dao.observeAll().map { list -> list.map { it.toLoggedDay() } }

    suspend fun getById(id: Long): LoggedDay? = dao.getById(id)?.toLoggedDay()

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** The single user action: a sentence in, a fully computed & saved day out. */
    suspend fun logDay(sentence: String): LoggedDay {
        val parsed = parser.parse(sentence)
        val footprint = engine.compute(parsed.activities)
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
                ListSerializer(String.serializer()), parsed.unrecognized,
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
}
