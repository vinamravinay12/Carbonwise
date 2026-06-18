package com.rivi.carbonwise.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * One conversation turn for the Gemini REST API; [role] is "user" or "model". A user turn may
 * carry an inline image ([imageBase64] + [imageMime]) for vision questions.
 */
data class GeminiTurn(
    val role: String,
    val text: String,
    val imageBase64: String? = null,
    val imageMime: String? = null,
)

/**
 * Minimal direct client for the Gemini REST API (generativelanguage v1beta). Replaces the
 * deprecated `com.google.ai.client.generativeai` SDK, which fails to call 2.5 models. We own
 * the request/response shape here, so it works with current models and supports system
 * instructions, JSON mode, and multi-turn chat (by passing the full [turns] history).
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(
        turns: List<GeminiTurn>,
        systemInstruction: String? = null,
        jsonOutput: Boolean = false,
        temperature: Double = 0.2,
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            if (!systemInstruction.isNullOrBlank()) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        putJsonArray("parts") { add(buildJsonObject { put("text", systemInstruction) }) }
                    },
                )
            }
            putJsonArray("contents") {
                turns.forEach { turn ->
                    add(
                        buildJsonObject {
                            put("role", turn.role)
                            putJsonArray("parts") {
                                if (turn.text.isNotBlank()) {
                                    add(buildJsonObject { put("text", turn.text) })
                                }
                                if (turn.imageBase64 != null && turn.imageMime != null) {
                                    add(
                                        buildJsonObject {
                                            put(
                                                "inlineData",
                                                buildJsonObject {
                                                    put("mimeType", turn.imageMime)
                                                    put("data", turn.imageBase64)
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", temperature)
                    if (jsonOutput) put("responseMimeType", "application/json")
                },
            )
        }.toString()

        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey",
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Gemini HTTP $code: ${response.take(180)}")
            extractText(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractText(response: String): String {
        val parts = json.parseToJsonElement(response).jsonObject["candidates"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")
            ?.jsonArray
            ?: error("No candidates in Gemini response")
        return parts.joinToString("") {
            it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
        }.trim()
    }
}
