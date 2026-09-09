package com.ligaya.core.ai

/**
 * The "empty implementation stub" Step 12 called for: a placeholder IntentProvider for wherever
 * no real Gemini-backed provider has been wired in. Always reports Unavailable — that is
 * genuinely, honestly what this class is: a stub with no real interpretation behind it at all,
 * not a provider that happened to determine "not an emergency" (Step 27's own distinction exists
 * specifically so this class no longer has to pretend to be the latter).
 */
class NullIntentProvider : IntentProvider {
    override suspend fun interpret(transcript: String): VoiceInterpretationOutcome =
        VoiceInterpretationOutcome.Unavailable
}
