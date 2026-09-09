package com.ligaya.core.ai

/**
 * Abstracts a free-form (not structured-output) Gemini call, so GeminiCompanionResponseProvider
 * is unit-testable without a real network call. Distinct from GeminiContentGenerator (Step 22),
 * which is specifically for schema-constrained structured output — the companion loop's replies
 * are conversational text, not data to parse into a fixed shape. HttpGeminiTextGenerator is the
 * real implementation.
 */
fun interface GeminiTextGenerator {
    suspend fun generateText(prompt: String): String
}
