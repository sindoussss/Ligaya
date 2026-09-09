package com.ligaya.core.voice

import com.ligaya.core.ai.IntentProvider
import com.ligaya.core.ai.VoiceInterpretationOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Wires the wake phrase into the same engine entry point as SOS (section 9's requirement that
 * both paths converge on the deterministic engine): capture (Step 21) -> wake-phrase check ->
 * Gemini interpretation (Step 22) -> engine intake, reported through [VoiceEmergencyIntentReporter]
 * exactly as any other subsystem coordinator in this codebase reports into the engine.
 *
 * Only a final transcript (not partial results) containing the wake phrase is ever interpreted —
 * partial, in-progress speech is never sent to Gemini, both to avoid wasted API calls and because
 * section 11's "Gemini produces data, never actions" implies that data should be complete, not a
 * fragment.
 *
 * When the AI/speech stack can't process an utterance at all (Step 27), this reports
 * [VoiceActivationResult.AiUnavailable] rather than silently routing it to the reporter as an
 * ordinary "not confident" decision — the engine intake (SOS, location, 911, family alerts) is
 * never reached or affected either way, matching section 21's failure table.
 */
class VoiceActivationCoordinator(
    private val captureCoordinator: VoiceCaptureCoordinator,
    private val intentProvider: IntentProvider,
    private val reporter: VoiceEmergencyIntentReporter,
) {
    fun listenForWakePhrase(): Flow<VoiceActivationResult> =
        captureCoordinator.startListening()
            .mapNotNull { event ->
                (event as? TranscriptionEvent.Success)
                    ?.takeIf { it.isFinal }
                    ?.text
                    ?.takeIf(::containsWakePhrase)
            }
            .mapNotNull { transcript ->
                when (val outcome = intentProvider.interpret(transcript)) {
                    is VoiceInterpretationOutcome.Interpreted ->
                        VoiceActivationResult.Decision(reporter.reportVoiceIntent(outcome.intent))
                    is VoiceInterpretationOutcome.Unavailable ->
                        VoiceActivationResult.AiUnavailable
                }
            }
}
