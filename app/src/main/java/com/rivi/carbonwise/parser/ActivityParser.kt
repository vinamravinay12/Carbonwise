package com.rivi.carbonwise.parser

import com.rivi.carbonwise.domain.ParseResult

/**
 * Converts a messy human sentence into structured activities. This is the *only*
 * job the AI does. Sits behind an interface so a deterministic implementation can
 * stand in for tests, offline demos, and as a safety net.
 */
interface ActivityParser {
    suspend fun parse(sentence: String): ParseResult
}
