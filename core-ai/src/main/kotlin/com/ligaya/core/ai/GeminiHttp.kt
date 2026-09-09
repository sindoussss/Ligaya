package com.ligaya.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The Generative Language API's base URL for both HttpGeminiContentGenerator (structured
 *  output, Step 22) and HttpGeminiTextGenerator (free-form text, Step 26) — one real network
 *  adapter shape, reused rather than duplicated for the second Gemini use this codebase has. */
internal const val GEMINI_GENERATE_CONTENT_URL = "https://generativelanguage.googleapis.com/v1beta/models"

/** Both callers send the same request envelope and get the same response envelope back — only
 *  `generationConfig` (present only for structured output) differs — so parsing the response
 *  text out of `candidates[0].content.parts[0].text` is genuinely one shared piece of logic, not
 *  two copies that happen to currently agree. */
internal fun extractGeminiResponseText(responseBody: String): String =
    Json.parseToJsonElement(responseBody).jsonObject
        .getValue("candidates").jsonArray[0].jsonObject
        .getValue("content").jsonObject
        .getValue("parts").jsonArray[0].jsonObject
        .getValue("text").jsonPrimitive.content
