package com.ligaya.core.ai

/**
 * Step 51's latency instrumentation for the wake-word path's own Gemini call — wraps a real
 * [IntentProvider] without changing [com.ligaya.core.voice.VoiceActivationCoordinator]'s own
 * logic at all. See [PipelineLatencyLog]'s own doc comment for why instrumentation lives here,
 * at the composition root's wiring, rather than inside the pipeline itself.
 */
class TimingIntentProvider(
    private val delegate: IntentProvider,
) : IntentProvider {
    override suspend fun interpret(transcript: String): VoiceInterpretationOutcome =
        PipelineLatencyLog.measure("gemini_intent") { delegate.interpret(transcript) }
}
