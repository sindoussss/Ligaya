package com.ligaya.core.voice

import com.ligaya.core.emergencyengine.EmergencyIntentDecision

/**
 * What listening for the wake phrase produced, once a final transcript containing it was heard.
 * Distinguishing [AiUnavailable] from a [Decision] is Step 27's own point: before
 * VoiceInterpretationOutcome existed, an AI/speech-stack failure surfaced as an ordinary
 * NeedsClarification decision — indistinguishable from Gemini genuinely, successfully
 * determining the utterance wasn't a confident emergency. Section 21's own rule is that a caller
 * (eventually, a UI) must be able to tell those apart and never imply full voice intelligence
 * still works when it doesn't.
 */
sealed interface VoiceActivationResult {
    data class Decision(val decision: EmergencyIntentDecision) : VoiceActivationResult
    data object AiUnavailable : VoiceActivationResult
}
