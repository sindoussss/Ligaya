package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.StructuredEmergencyIntent

/**
 * Section 21's granular Gemini-failure table, made concrete for the one capability it actually
 * marks unavailable when the AI/speech stack can't process something: "Voice emergency
 * interpretation — Unavailable if the AI/speech stack cannot process it." Every other capability
 * in that table (SOS, the deterministic engine, location, 911, family alerts) has no dependency
 * on this type at all — see EmergencyStateMachine and DefaultEmergencyController's own doc
 * comments for why that's structural, not just usually true.
 *
 * Before this type existed, IntentProvider.interpret() had no way to represent "could not
 * process this at all" separately from "processed it, and it wasn't an emergency" — both
 * surfaced as the identical StructuredEmergencyIntent(emergency = false, confidence = 0f, ...).
 * That conflation is exactly what section 21's own UI requirement forbids: "must never imply
 * full voice intelligence still works" when it doesn't. A caller can now tell the two apart and
 * show the user the truth.
 */
sealed interface VoiceInterpretationOutcome {
    data class Interpreted(val intent: StructuredEmergencyIntent) : VoiceInterpretationOutcome
    data object Unavailable : VoiceInterpretationOutcome
}
