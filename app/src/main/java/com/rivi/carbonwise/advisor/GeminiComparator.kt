package com.rivi.carbonwise.advisor

import com.rivi.carbonwise.ai.GeminiClient
import com.rivi.carbonwise.ai.GeminiTurn
import com.rivi.carbonwise.data.CompareOption
import com.rivi.carbonwise.data.CompareReply
import com.rivi.carbonwise.data.CompareResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Conversational carbon comparison via Gemini, with memory. It keeps a chat session so
 * follow-ups like "why?" are understood in context — a comparison turn returns ranked
 * items, while a follow-up returns just an explanation (no new card). Figures are AI
 * estimates (the deterministic table can't price specific models); the app badges them.
 */
class GeminiComparator(
    apiKey: String,
    modelName: String = "gemini-2.5-flash",
) {
    private val client = GeminiClient(apiKey = apiKey, model = modelName)
    private val json = Json { ignoreUnknownKeys = true }

    /** Conversation history (alternating user/model turns) kept for follow-up context. */
    private val history = mutableListOf<GeminiTurn>()

    /** Start a fresh conversation (clears all prior context). */
    fun reset() {
        history.clear()
    }

    suspend fun send(
        message: String,
        imageBase64: String? = null,
        imageMime: String? = null,
    ): CompareReply {
        // Send history + the current turn (with image, if any). Store history text-only so we
        // don't resend image bytes every turn; the model's reply carries the visual context.
        val turns = history + GeminiTurn("user", message, imageBase64, imageMime)
        val text = client.generate(
            turns = turns,
            systemInstruction = SYSTEM_INSTRUCTION,
            jsonOutput = true,
            temperature = 0.3,
        )
        if (text.isEmpty()) return CompareReply("Sorry, I didn't catch that — try rephrasing.", null)
        history.add(GeminiTurn("user", if (imageBase64 != null) "$message [shared an image]" else message))
        history.add(GeminiTurn("model", text))

        val parsed = runCatching { json.decodeFromString<Response>(text) }.getOrNull()
            ?: return CompareReply(text, null) // not JSON → show as plain text

        val options = parsed.items
            .filter { it.kgCo2 >= 0 }
            .map { CompareOption(label = it.name, kgCo2 = it.kgCo2, detail = it.note) }
            .sortedByDescending { it.kgCo2 }

        // Only a real comparison (2+ options) produces a ranked card.
        val result = if (options.size >= 2) {
            CompareResult(
                verdict = parsed.verdict.ifBlank { parsed.reply },
                options = options,
                aiEstimated = true,
            )
        } else {
            null
        }
        val reply = parsed.reply.ifBlank { parsed.verdict.ifBlank { "Here's what I found." } }
        return CompareReply(reply, result)
    }

    private companion object {
        const val SYSTEM_INSTRUCTION = """
            You are a friendly, knowledgeable carbon-footprint assistant. Answer ANY
            carbon/climate question conversationally — comparisons, "list the biggest sources
            of...", "how can I cut my commute emissions", general explanations, follow-ups.
            Keep context across the whole conversation. Keep replies short and plain.

            When the user asks to COMPARE specific options (which emits more CO₂):
            - Compare on a fair common basis (same distance, servings, or duration); state that
              basis in the verdict (e.g. "for the same 20 km").
            - Return an "items" array; each item has: "name", "kgCo2" (estimated kg CO₂e on that
              basis), and "note" (a short assumption, e.g. "petrol, ~20 km/l, 20 km").
            - Include a one-line "verdict" naming which is worse and roughly by how much.
            - Do NOT assume a newer model emits more — newer cars are often MORE fuel-efficient.
              Reason from fuel type and realistic mileage (India-relevant where applicable).

            For any OTHER question (lists, explanations, advice, follow-ups like "why?"):
            - Answer fully in "reply". Leave "items" and "verdict" empty. Use brief lines or a
              short list inside "reply" when listing things.

            Be honest and non-alarmist; never claim a person's own emissions directly cause
            their personal illness — climate impact is cumulative and collective.

            Always fill "reply" with a conversational answer. Respond with ONLY this JSON:
            {"reply":"...","verdict":"...","items":[{"name":"...","kgCo2":0.0,"note":"..."}]}
            "verdict" and "items" may be empty for non-comparison replies.
        """
    }

    @Serializable
    private data class Response(
        val reply: String = "",
        val verdict: String = "",
        val items: List<Item> = emptyList(),
    )

    @Serializable
    private data class Item(
        val name: String,
        val kgCo2: Double,
        val note: String = "",
    )
}
