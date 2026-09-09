package com.ligaya.core.ai

/**
 * Abstracts the actual Gemini API call so GeminiIntentProvider's parsing/mapping logic — the part
 * most likely to have a bug — is unit-testable without a real network call or a real API key.
 * Returns the raw JSON text Gemini's structured-output mode produced (matching
 * emergencyIntentResponseSchema). HttpGeminiContentGenerator is the real implementation.
 */
fun interface GeminiContentGenerator {
    suspend fun generateStructuredContent(prompt: String): String
}
