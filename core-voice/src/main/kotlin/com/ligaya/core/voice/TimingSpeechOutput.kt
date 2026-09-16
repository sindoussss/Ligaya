package com.ligaya.core.voice

import com.ligaya.core.ai.PipelineLatencyLog
import com.ligaya.core.ai.ValidatedSpeech

/**
 * Step 51's latency instrumentation for the TTS stage — wraps a real [SpeechOutput] without
 * changing any caller's own logic at all. Two log stages, not one, matching [SpeechOutput]'s own
 * deliberate two-overload split: a fixed status announcement and a validated companion reply are
 * different enough in typical length that conflating their durations under one label would blur
 * the very figure Step 51 wants documented. See [PipelineLatencyLog]'s own doc comment for why
 * instrumentation lives here, at the composition root's wiring, rather than inside the pipeline
 * itself.
 */
class TimingSpeechOutput(
    private val delegate: SpeechOutput,
) : SpeechOutput {
    override suspend fun speak(message: EmergencyStatusMessage): SpeechResult =
        PipelineLatencyLog.measure("tts_status") { delegate.speak(message) }

    override suspend fun speak(speech: ValidatedSpeech): SpeechResult =
        PipelineLatencyLog.measure("tts_companion") { delegate.speak(speech) }
}
