package com.ligaya.core.voice

import com.ligaya.core.ai.IntentProvider
import com.ligaya.core.ai.VoiceInterpretationOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

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
 *
 * [phase] is Step 53's audit follow-up: the always-on wake-word loop had no live signal at all
 * for driving Home's own "persistent, always-visible" voice indicator (section 27), unlike
 * [com.ligaya.feature.companion.EmergencyCompanionCoordinator]'s own per-turn phase. Mirrors that
 * class's exact `_phase`/`phase` pattern, just wired at each real transition in this class's own
 * Flow chain instead of an imperative loop: [VoicePipelinePhase.LISTENING] once collection of a
 * capture session actually starts, [VoicePipelinePhase.PROCESSING] once a wake-phrase-matching
 * transcript is about to be sent to [intentProvider], back to [VoicePipelinePhase.IDLE] once that
 * capture session's flow completes for any reason (a result, an error, or cancellation). This
 * class never reaches [VoicePipelinePhase.SPEAKING] itself — it doesn't speak anything; the
 * composition root that reads [VoiceActivationResult.AiUnavailable] off [listenForWakePhrase] and
 * actually calls [SpeechOutput.speak] owns that moment.
 */
class VoiceActivationCoordinator(
    private val captureCoordinator: VoiceCaptureCoordinator,
    private val intentProvider: IntentProvider,
    private val reporter: VoiceEmergencyIntentReporter,
) {
    private val _phase = MutableStateFlow(VoicePipelinePhase.IDLE)
    val phase: StateFlow<VoicePipelinePhase> = _phase.asStateFlow()

    fun listenForWakePhrase(): Flow<VoiceActivationResult> =
        captureCoordinator.startListening()
            .onStart { _phase.value = VoicePipelinePhase.LISTENING }
            .mapNotNull { event ->
                (event as? TranscriptionEvent.Success)
                    ?.takeIf { it.isFinal }
                    ?.text
                    ?.takeIf(::containsWakePhrase)
            }
            .onEach { _phase.value = VoicePipelinePhase.PROCESSING }
            .mapNotNull { transcript ->
                when (val outcome = intentProvider.interpret(transcript)) {
                    is VoiceInterpretationOutcome.Interpreted ->
                        VoiceActivationResult.Decision(reporter.reportVoiceIntent(outcome.intent))
                    is VoiceInterpretationOutcome.Unavailable ->
                        VoiceActivationResult.AiUnavailable
                }
            }
            .onCompletion { _phase.value = VoicePipelinePhase.IDLE }
}
