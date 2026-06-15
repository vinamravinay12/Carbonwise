package com.rivi.carbonwise.parser

import android.util.Log
import com.rivi.carbonwise.domain.ParseResult

/**
 * Wraps a [primary] parser (typically Gemini) and falls back to a deterministic
 * [fallback] (rule-based) when the primary throws or returns nothing usable. This is
 * what makes the app robust and demoable even with no network or no API key.
 */
class FallbackParser(
    private val primary: ActivityParser,
    private val fallback: ActivityParser,
) : ActivityParser {

    override suspend fun parse(sentence: String): ParseResult {
        return try {
            val result = primary.parse(sentence)
            if (result.activities.isEmpty()) fallback.parse(sentence) else result
        } catch (e: Exception) {
            Log.w("CarbonWise", "Primary parser failed, using fallback: ${e.message}")
            fallback.parse(sentence)
        }
    }
}
