package com.rivi.carbonwise.advisor

import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
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
    private val model = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.3f
            responseMimeType = "application/json"
        },
        systemInstruction = content { text(SYSTEM_INSTRUCTION) },
    )

    private val json = Json { ignoreUnknownKeys = true }
    private var chat: Chat = model.startChat()

    /** Start a fresh conversation (clears all prior context). */
    fun reset() {
        chat = model.startChat()
    }

    suspend fun send(message: String): CompareReply {
        val response = chat.sendMessage(message)
        val text = response.text?.trim().orEmpty()
        if (text.isEmpty()) return CompareReply("Sorry, I didn't catch that — try rephrasing.", null)

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
            You are a friendly, conversational carbon-footprint assistant. Keep context across
            the whole conversation and answer follow-up questions naturally. Keep replies short
            and plain.

            When the user asks to COMPARE options (which emits more CO₂):
            - Compare on a fair common basis (same distance, servings, or duration); state that
              basis in the verdict (e.g. "for the same 20 km").
            - Return an "items" array; each item has: "name", "kgCo2" (estimated kg CO₂e on that
              basis), and "note" (a short assumption, e.g. "petrol, ~20 km/l, 20 km").
            - Include a one-line "verdict" naming which is worse and roughly by how much.
            - Do NOT assume a newer model emits more — newer cars are often MORE fuel-efficient.
              Reason from fuel type and realistic mileage (India-relevant where applicable).

            When the user asks a FOLLOW-UP (e.g. "why?", "explain", "are you sure?"):
            - Answer it in "reply", referring to the comparison already made. Leave "items" empty
              unless they clearly ask for a new comparison. Do not flip your previous conclusion
              unless you were genuinely wrong — then say so.

            Always fill "reply" with a short conversational answer. Respond with ONLY this JSON:
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
