package com.ligaya.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The real Gemini adapter — calls the Generative Language API's `generateContent` endpoint with
 * structured-output mode (`responseMimeType` + `responseSchema`), so Gemini is constrained to
 * return exactly the shape GeminiIntentProvider expects, per section 11's "enforce JSON schema —
 * do NOT free-text-parse Gemini output."
 *
 * Requires a real Gemini API key (from Google AI Studio) — an account action only the app's
 * operator can take; `apiKey` is a plain constructor parameter, never hardcoded.
 */
class HttpGeminiContentGenerator(
    private val apiKey: String,
    private val model: String = "gemini-3.6-flash",
) : GeminiContentGenerator {

    override suspend fun generateStructuredContent(prompt: String): String = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", emergencyIntentResponseSchema())
            }
        }

        val connection = (URL("$GEMINI_GENERATE_CONTENT_URL/$model:generateContent?key=$apiKey")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = GEMINI_CONNECT_TIMEOUT_MILLIS
            readTimeout = GEMINI_READ_TIMEOUT_MILLIS
        }

        try {
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }
            check(connection.responseCode in 200..299) {
                "Gemini request failed with HTTP ${connection.responseCode}"
            }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            extractGeminiResponseText(responseBody)
        } finally {
            connection.disconnect()
        }
    }
}
