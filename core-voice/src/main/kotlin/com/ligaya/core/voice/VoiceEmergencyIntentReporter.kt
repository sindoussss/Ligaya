package com.ligaya.core.voice

import com.ligaya.core.emergencyengine.EmergencyIntentDecision
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent

/**
 * Abstracts "hand this structured intent to the engine's existing intake" so
 * VoiceActivationCoordinator never needs to know whether it's talking to a bare
 * EmergencyStateMachine (unit tests, via core-emergency-engine's own submitStructuredIntent,
 * Step 9/12) or the persistence-backed real controller (core-data's DefaultEmergencyController,
 * Step 13/23) — same reporter pattern as core-location's LocationFlowReporter (Step 15) and
 * every subsystem coordinator since. Returning EmergencyIntentDecision — an existing
 * core-emergency-engine type, not a new one — is deliberate: it is the same type
 * EmergencyIntentEvaluator already produces, so this interface cannot itself invent a different
 * notion of "confirmed" than the one the engine already has.
 */
fun interface VoiceEmergencyIntentReporter {
    suspend fun reportVoiceIntent(intent: StructuredEmergencyIntent): EmergencyIntentDecision
}
