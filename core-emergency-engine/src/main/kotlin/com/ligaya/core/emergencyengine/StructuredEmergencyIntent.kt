package com.ligaya.core.emergencyengine

/**
 * The AI/deterministic boundary made concrete (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 11):
 * this is the entire set of fields Gemini is allowed to produce ("Emergency intent, Incident
 * type, Context, Confidence score, Extracted information" — section 11's table, left column).
 * Everything in that table's right column (phone number, emergency-service decision,
 * family-recipient routing, permission/subscription/location-sharing authorization,
 * notification-success claims, emergency-state transitions, resolution decisions) has no field
 * here — there is nowhere for Gemini to put an executable instruction, only interpreted data.
 *
 * `confidence` is validated at construction, not just trusted: this is the one boundary in the
 * whole system where genuinely untrusted (AI-generated, not user-typed) input enters the
 * deterministic engine's world, so it gets the same "validate at the boundary" treatment as any
 * external input, per section 11's "Gemini's output is advisory input, never an executable
 * instruction."
 */
data class StructuredEmergencyIntent(
    val emergency: Boolean,
    val incidentType: IncidentType?,
    val confidence: Float,
    val userContext: String,
    val requestedLocation: Boolean,
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in [0.0, 1.0], was $confidence" }
    }
}
