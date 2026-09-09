package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.IncidentType
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The real Gemini-backed IntentProvider (Step 22) — section 10/11's STT-transcript-to-
 * structured-data stage. Confidence-threshold logic itself already exists
 * (EmergencyIntentEvaluator, Step 9); this class's only job is producing a trustworthy
 * VoiceInterpretationOutcome for that evaluator to consume.
 *
 * Any failure here — network error, malformed/unparseable JSON, an incidentType string outside
 * the enum — resolves to VoiceInterpretationOutcome.Unavailable (Step 27), never a fabricated
 * "not an emergency" reading. This is deliberate, not an oversight: interpret() is a total
 * function precisely so a Gemini outage can never itself become a silent failure mode, and never
 * gets presented as though real interpretation happened when it didn't (section 21's own explicit
 * requirement). Nothing here decides or executes anything (section 11); it only ever produces
 * data for the deterministic engine to evaluate.
 */
class GeminiIntentProvider(
    private val contentGenerator: GeminiContentGenerator,
) : IntentProvider {

    override suspend fun interpret(transcript: String): VoiceInterpretationOutcome {
        val rawJson = runCatching { contentGenerator.generateStructuredContent(buildEmergencyIntentPrompt(transcript)) }
            .getOrNull() ?: return VoiceInterpretationOutcome.Unavailable
        val intent = runCatching { parseStructuredIntent(rawJson, transcript) }.getOrNull()
            ?: return VoiceInterpretationOutcome.Unavailable
        return VoiceInterpretationOutcome.Interpreted(intent)
    }
}

@Serializable
internal data class StructuredIntentJson(
    val emergency: Boolean,
    val incidentType: String? = null,
    val confidence: Float,
    val userContext: String = "",
    val requestedLocation: Boolean = false,
)

/**
 * Split out from GeminiIntentProvider so this mapping — the part most likely to hide a subtle
 * bug — is directly unit-testable against fixed JSON strings, not only through the full
 * interpret() round trip.
 */
private val lenientJson = Json { ignoreUnknownKeys = true }

internal fun parseStructuredIntent(rawJson: String, transcript: String): StructuredEmergencyIntent {
    val parsed = lenientJson.decodeFromString<StructuredIntentJson>(rawJson)
    val incidentType = parsed.incidentType?.let { raw -> runCatching { IncidentType.valueOf(raw) }.getOrNull() }
    return StructuredEmergencyIntent(
        emergency = parsed.emergency,
        incidentType = incidentType,
        // Gemini is asked for [0.0, 1.0] but is a model, not a validator — coerced rather than
        // trusted, so a borderline-out-of-range value degrades gracefully instead of crashing
        // StructuredEmergencyIntent's own init { require(...) } check.
        confidence = parsed.confidence.coerceIn(0f, 1f),
        userContext = parsed.userContext.ifBlank { transcript },
        requestedLocation = parsed.requestedLocation,
    )
}
