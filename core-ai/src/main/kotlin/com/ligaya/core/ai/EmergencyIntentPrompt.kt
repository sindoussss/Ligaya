package com.ligaya.core.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Section 11's "enforce JSON schema, don't free-text-parse Gemini output" made concrete: this
 * prompt tells Gemini its only job is data extraction (section 10/11 — "Gemini produces data,
 * never actions"), and [emergencyIntentResponseSchema] constrains the API call itself to return
 * exactly StructuredEmergencyIntent's shape via Gemini's structured-output mode, so there is
 * nothing for GeminiIntentProvider to free-text-parse in the first place.
 */
internal fun buildEmergencyIntentPrompt(transcript: String): String = """
    You are Ligaya's emergency-intent interpreter, for a Philippines-focused personal safety app.
    The person speaking may use English, Tagalog, or Taglish (a mix of both in the same sentence),
    for example: "Ligaya, tulong. May sunog." (there is a fire).

    Your ONLY job is to extract structured data from what was said. You never decide what action
    to take, you never claim any action has been taken, and you never produce anything beyond the
    fields below — the app's own deterministic logic, not you, makes every actual decision.

    From the transcript, determine:
    - emergency: true only if the speaker is describing a genuine, current emergency needing help
      right now. Ordinary conversation, a past/hypothetical emergency, or an unclear statement is
      false.
    - incidentType: exactly one of FIRE, POLICE, MEDICAL, RESCUE, OTHER if emergency is true and
      the type is reasonably clear; null otherwise. Never guess a specific type you are not
      reasonably confident about — OTHER exists for genuine emergencies that don't clearly fit the
      other four.
    - confidence: your own confidence, from 0.0 to 1.0, that the "emergency" classification above
      is correct. Reserve values above 0.7 for cases with little ambiguity.
    - userContext: a brief, strictly factual summary of what the person said, in their own words
      where possible. Never invent, embellish, or infer details they did not actually say.
    - requestedLocation: true only if the person explicitly asked for help finding them or
      mentioned sharing their location.

    Transcript: "$transcript"
""".trimIndent()

/**
 * Gemini's structured-output schema (an OpenAPI-subset format) for the fields above — passed as
 * `generationConfig.responseSchema` alongside `responseMimeType: "application/json"` so the API
 * itself, not prompt wording alone, constrains the response shape.
 */
internal fun emergencyIntentResponseSchema(): JsonObject = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        putJsonObject("emergency") {
            put("type", "BOOLEAN")
        }
        putJsonObject("incidentType") {
            put("type", "STRING")
            put("nullable", true)
            putJsonArray("enum") {
                listOf("FIRE", "POLICE", "MEDICAL", "RESCUE", "OTHER").forEach { add(it) }
            }
        }
        putJsonObject("confidence") {
            put("type", "NUMBER")
        }
        putJsonObject("userContext") {
            put("type", "STRING")
        }
        putJsonObject("requestedLocation") {
            put("type", "BOOLEAN")
        }
    }
    putJsonArray("required") {
        listOf("emergency", "confidence", "userContext", "requestedLocation").forEach { add(it) }
    }
}
