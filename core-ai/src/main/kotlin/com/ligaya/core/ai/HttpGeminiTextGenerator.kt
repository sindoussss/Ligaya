package com.ligaya.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * The real free-form Gemini adapter for the companion loop (Step 26) — same Generative Language
 * API endpoint as HttpGeminiContentGenerator (Step 22), minus the structured-output
 * `generationConfig`: the companion's replies are conversational text, not data with a schema to
 * enforce. Requires the same real Gemini API key (Google AI Studio) as every other Gemini caller
 * in this codebase; `apiKey` is a plain constructor parameter, never hardcoded.
 */
class HttpGeminiTextGenerator(
    private val apiKey: String,
    private val model: String = "gemini-3.6-flash",
) : GeminiTextGenerator {

    override suspend fun generateText(prompt: String): String = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
        }

        val connection = (URL("$GEMINI_GENERATE_CONTENT_URL/$model:generateContent?key=$apiKey")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
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
