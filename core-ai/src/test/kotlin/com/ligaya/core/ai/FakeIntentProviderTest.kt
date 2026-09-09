package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.IncidentType
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeIntentProviderTest {

    private fun intent(emergency: Boolean, confidence: Float) =
        StructuredEmergencyIntent(emergency, IncidentType.FIRE, confidence, "context", requestedLocation = true)

    @Test
    fun `returns each configured response in order`() = runTest {
        val unclear = intent(emergency = true, confidence = 0.2f)
        val confident = intent(emergency = true, confidence = 0.95f)
        val provider = FakeIntentProvider(unclear, confident)

        assertEquals(VoiceInterpretationOutcome.Interpreted(unclear), provider.interpret("first utterance"))
        assertEquals(VoiceInterpretationOutcome.Interpreted(confident), provider.interpret("second utterance"))
    }

    @Test
    fun `repeats the last response once exhausted`() = runTest {
        val onlyResponse = intent(emergency = true, confidence = 0.9f)
        val provider = FakeIntentProvider(onlyResponse)

        provider.interpret("first")
        assertEquals(VoiceInterpretationOutcome.Interpreted(onlyResponse), provider.interpret("second"))
        assertEquals(VoiceInterpretationOutcome.Interpreted(onlyResponse), provider.interpret("third"))
    }

    @Test
    fun `can also simulate an AI-unavailable turn in the middle of a sequence`() = runTest {
        val confident = intent(emergency = true, confidence = 0.9f)
        val provider = FakeIntentProvider(
            VoiceInterpretationOutcome.Unavailable,
            VoiceInterpretationOutcome.Interpreted(confident),
        )

        assertEquals(VoiceInterpretationOutcome.Unavailable, provider.interpret("first"))
        assertEquals(VoiceInterpretationOutcome.Interpreted(confident), provider.interpret("second"))
    }

    @Test
    fun `NullIntentProvider always reports Unavailable`() = runTest {
        val result = NullIntentProvider().interpret("anything")
        assertEquals(VoiceInterpretationOutcome.Unavailable, result)
    }
}
