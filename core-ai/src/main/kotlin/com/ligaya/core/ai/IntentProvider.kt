package com.ligaya.core.ai

/**
 * Produces a voice interpretation outcome from user speech (LIGAYA_ARCHITECTURE_FINAL_VOICE.md
 * section 10: STT -> Gemini -> intent/context extraction -> structured data). GeminiIntentProvider
 * (Step 22) is the real implementation.
 *
 * Returns VoiceInterpretationOutcome (Step 27), not a bare StructuredEmergencyIntent: a caller
 * needs to be able to tell "processed it, not an emergency" apart from "could not process it at
 * all" — section 21's own explicit requirement that the AI/speech stack being unavailable must
 * never be presented as though full voice intelligence still works.
 */
fun interface IntentProvider {
    suspend fun interpret(transcript: String): VoiceInterpretationOutcome
}
