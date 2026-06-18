package com.rivi.carbonwise

import com.rivi.carbonwise.domain.ParseResult
import com.rivi.carbonwise.domain.ParsedActivity
import com.rivi.carbonwise.parser.ActivityParser
import com.rivi.carbonwise.parser.FallbackParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackParserTest {

    private val fallbackResult = ParseResult(listOf(ParsedActivity("walk", 2.0)))
    private val fallback = FakeParser(fallbackResult)

    @Test
    fun `uses the primary result when it has activities`() = runTest {
        val primary = FakeParser(ParsedActivity("car_petrol", 10.0))
        val result = FallbackParser(primary, fallback).parse("anything")
        assertEquals("car_petrol", result.activities.single().type)
    }

    @Test
    fun `falls back when the primary returns nothing`() = runTest {
        val emptyPrimary = FakeParser(ParseResult(emptyList()))
        val result = FallbackParser(emptyPrimary, fallback).parse("anything")
        assertEquals("walk", result.activities.single().type)
    }

    @Test
    fun `falls back when the primary throws`() = runTest {
        val throwingPrimary = object : ActivityParser {
            override suspend fun parse(sentence: String): ParseResult = error("network down")
        }
        val result = FallbackParser(throwingPrimary, fallback).parse("anything")
        assertEquals("walk", result.activities.single().type)
    }
}
