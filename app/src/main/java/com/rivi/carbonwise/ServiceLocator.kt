package com.rivi.carbonwise

import android.content.Context
import com.rivi.carbonwise.advisor.GeminiSwapAdvisor
import com.rivi.carbonwise.advisor.SwapAdvisor
import com.rivi.carbonwise.data.CarbonDatabase
import com.rivi.carbonwise.data.CarbonRepository
import com.rivi.carbonwise.parser.ActivityParser
import com.rivi.carbonwise.parser.FallbackParser
import com.rivi.carbonwise.parser.GeminiParser
import com.rivi.carbonwise.parser.RuleBasedParser
import com.rivi.carbonwise.recognition.ActivityRecognitionManager
import com.rivi.carbonwise.recognition.GeminiVehicleClassifier
import com.rivi.carbonwise.recognition.HeuristicVehicleClassifier
import com.rivi.carbonwise.recognition.VehicleModeClassifier

/**
 * Tiny manual DI container. Builds the parser stack — Gemini in front of the rule-based
 * fallback when an API key is configured, otherwise rule-based only — and the repository.
 * Keeps the single-module app honest without dragging in a DI framework.
 */
object ServiceLocator {

    @Volatile
    private var repository: CarbonRepository? = null

    @Volatile
    private var recognitionManager: ActivityRecognitionManager? = null

    /** True when a live Gemini key is wired in; surfaced in the UI as a small badge. */
    var usingAi: Boolean = false
        private set

    fun repository(context: Context): CarbonRepository =
        repository ?: synchronized(this) {
            repository ?: build(context).also { repository = it }
        }

    fun recognitionManager(context: Context): ActivityRecognitionManager =
        recognitionManager ?: synchronized(this) {
            recognitionManager
                ?: ActivityRecognitionManager(context.applicationContext).also {
                    recognitionManager = it
                }
        }

    private fun build(context: Context): CarbonRepository {
        val db = CarbonDatabase.get(context)
        val key = BuildConfig.GEMINI_API_KEY
        usingAi = key.isNotBlank()
        return CarbonRepository(
            dao = db.entryDao(),
            parser = buildParser(key),
            detectedDao = db.detectedSegmentDao(),
            swapAdvisor = buildSwapAdvisor(key),
            vehicleClassifier = buildVehicleClassifier(key),
        )
    }

    /** Hybrid (Gemini refining the heuristic) when a key is present; heuristic otherwise. */
    private fun buildVehicleClassifier(key: String): VehicleModeClassifier =
        if (key.isNotBlank()) {
            GeminiVehicleClassifier(apiKey = key, fallback = HeuristicVehicleClassifier)
        } else {
            HeuristicVehicleClassifier
        }

    private fun buildParser(key: String): ActivityParser {
        val rules = RuleBasedParser()
        return if (key.isNotBlank()) {
            FallbackParser(primary = GeminiParser(apiKey = key), fallback = rules)
        } else {
            rules
        }
    }

    /** AI swap advisor only when a key is present; otherwise null → engine's rule-based swap. */
    private fun buildSwapAdvisor(key: String): SwapAdvisor? =
        if (key.isNotBlank()) GeminiSwapAdvisor(apiKey = key) else null
}
