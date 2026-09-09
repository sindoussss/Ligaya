package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.StructuredEmergencyIntent

/**
 * Test double for IntentProvider — returns whatever canned outcome(s) a test configures, in
 * order, so a test can simulate a sequence of AI responses (e.g. an unclear reading followed by
 * a confident one on a follow-up utterance, or an AI-unavailable turn) without any real Gemini
 * call existing. Once the configured responses are exhausted, it keeps repeating the last one,
 * so a test doesn't need to predict exactly how many times interpret() will be called.
 */
class FakeIntentProvider(private val responses: List<VoiceInterpretationOutcome>) : IntentProvider {
    constructor(vararg responses: VoiceInterpretationOutcome) : this(responses.toList())

    /** Convenience for the common case: a test only cares about Interpreted outcomes. */
    constructor(vararg intents: StructuredEmergencyIntent) :
        this(intents.map { VoiceInterpretationOutcome.Interpreted(it) })

    init {
        require(responses.isNotEmpty()) { "FakeIntentProvider needs at least one response" }
    }

    private var index = 0

    override suspend fun interpret(transcript: String): VoiceInterpretationOutcome {
        val response = responses.getOrElse(index) { responses.last() }
        index++
        return response
    }
}
