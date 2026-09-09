package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.EmergencyIntentDecision
import com.ligaya.core.emergencyengine.EmergencyIntentEvaluator
import com.ligaya.core.emergencyengine.IncidentType
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The golden-set test this step's own acceptance criteria call for: a fixed set of transcripts,
 * each with a mocked (not live) Gemini response, asserting the resulting StructuredEmergencyIntent
 * is schema-valid and — critically — that low-confidence input reaches EmergencyIntentEvaluator
 * as NeedsClarification, never a silently confirmed emergency. No real network call or API key is
 * used anywhere in this file; see GeminiLiveApiSmokeTest for the separate, optional live check.
 */
class GeminiIntentProviderTest {

    private fun fakeGenerator(json: String) = GeminiContentGenerator { json }

    private fun throwingGenerator(message: String) = GeminiContentGenerator { error(message) }

    private suspend fun interpretedIntent(provider: GeminiIntentProvider, transcript: String): StructuredEmergencyIntent {
        val outcome = provider.interpret(transcript)
        assertTrue("expected Interpreted, got $outcome", outcome is VoiceInterpretationOutcome.Interpreted)
        return (outcome as VoiceInterpretationOutcome.Interpreted).intent
    }

    // --- Golden set: the doc's own example, plus a representative spread of Taglish/English ---

    @Test
    fun `the doc's own example phrase produces a confirmed FIRE intent`() = runTest {
        val provider = GeminiIntentProvider(
            fakeGenerator(
                """{"emergency": true, "incidentType": "FIRE", "confidence": 0.95, "userContext": "May sunog sa bahay.", "requestedLocation": true}""",
            ),
        )

        val intent = interpretedIntent(provider, "Ligaya, tulong. May sunog.")

        assertTrue(intent.emergency)
        assertEquals(IncidentType.FIRE, intent.incidentType)
        assertEquals(0.95f, intent.confidence, 0.001f)
        assertTrue(intent.requestedLocation)
        assertEquals(EmergencyIntentDecision.Confirmed(intent), EmergencyIntentEvaluator.evaluate(intent))
    }

    @Test
    fun `a clear medical emergency in English produces a confirmed MEDICAL intent`() = runTest {
        val provider = GeminiIntentProvider(
            fakeGenerator(
                """{"emergency": true, "incidentType": "MEDICAL", "confidence": 0.9, "userContext": "My father collapsed and is not breathing.", "requestedLocation": false}""",
            ),
        )

        val intent = interpretedIntent(provider, "Help, my father collapsed and he's not breathing!")

        assertEquals(IncidentType.MEDICAL, intent.incidentType)
        assertTrue(EmergencyIntentEvaluator.evaluate(intent) is EmergencyIntentDecision.Confirmed)
    }

    @Test
    fun `ordinary non-emergency speech produces a non-emergency intent`() = runTest {
        val provider = GeminiIntentProvider(
            fakeGenerator(
                """{"emergency": false, "incidentType": null, "confidence": 0.05, "userContext": "Kumusta ka, Ligaya?", "requestedLocation": false}""",
            ),
        )

        val intent = interpretedIntent(provider, "Kumusta ka, Ligaya?")

        assertEquals(false, intent.emergency)
        assertNull(intent.incidentType)
        assertTrue(EmergencyIntentEvaluator.evaluate(intent) is EmergencyIntentDecision.NeedsClarification)
    }

    // --- The acceptance criterion stated explicitly: low confidence never silently proceeds ---

    @Test
    fun `emergency reported true but at low confidence still needs clarification, not silent action`() = runTest {
        val provider = GeminiIntentProvider(
            fakeGenerator(
                """{"emergency": true, "incidentType": "OTHER", "confidence": 0.3, "userContext": "Parang may mali.", "requestedLocation": false}""",
            ),
        )

        val intent = interpretedIntent(provider, "Parang may mali... hindi ko sure.")

        assertTrue(intent.emergency)
        assertTrue(
            "a 0.3-confidence reading must never be Confirmed",
            EmergencyIntentEvaluator.evaluate(intent) is EmergencyIntentDecision.NeedsClarification,
        )
    }

    @Test
    fun `confidence exactly at the threshold is confirmed, just below it is not`() = runTest {
        val atThreshold = interpretedIntent(
            GeminiIntentProvider(
                fakeGenerator(
                    """{"emergency": true, "incidentType": "POLICE", "confidence": ${EmergencyIntentEvaluator.CONFIRM_THRESHOLD}, "userContext": "may magnanakaw", "requestedLocation": true}""",
                ),
            ),
            "May magnanakaw sa bahay namin!",
        )
        val justBelow = interpretedIntent(
            GeminiIntentProvider(
                fakeGenerator(
                    """{"emergency": true, "incidentType": "POLICE", "confidence": ${EmergencyIntentEvaluator.CONFIRM_THRESHOLD - 0.01f}, "userContext": "may magnanakaw", "requestedLocation": true}""",
                ),
            ),
            "May magnanakaw sa bahay namin!",
        )

        assertTrue(EmergencyIntentEvaluator.evaluate(atThreshold) is EmergencyIntentDecision.Confirmed)
        assertTrue(EmergencyIntentEvaluator.evaluate(justBelow) is EmergencyIntentDecision.NeedsClarification)
    }

    // --- Failure modes: never throw, never fabricate a reading — always Unavailable (Step 27) ---

    @Test
    fun `a content generator failure resolves to Unavailable, not a fabricated reading`() = runTest {
        val provider = GeminiIntentProvider(throwingGenerator("network unreachable"))

        val outcome = provider.interpret("Ligaya, tulong!")

        assertEquals(VoiceInterpretationOutcome.Unavailable, outcome)
    }

    @Test
    fun `malformed JSON resolves to Unavailable instead of throwing`() = runTest {
        val provider = GeminiIntentProvider(fakeGenerator("this is not json"))

        val outcome = provider.interpret("Ligaya, tulong!")

        assertEquals(VoiceInterpretationOutcome.Unavailable, outcome)
    }

    @Test
    fun `a response missing a required field resolves to Unavailable instead of throwing`() = runTest {
        val provider = GeminiIntentProvider(fakeGenerator("""{"emergency": true}"""))

        val outcome = provider.interpret("Ligaya, tulong!")

        assertEquals(VoiceInterpretationOutcome.Unavailable, outcome)
    }

    @Test
    fun `an incidentType outside the enum parses as null rather than throwing`() = runTest {
        val provider = GeminiIntentProvider(
            fakeGenerator(
                """{"emergency": true, "incidentType": "ALIEN_INVASION", "confidence": 0.9, "userContext": "test", "requestedLocation": false}""",
            ),
        )

        val intent = interpretedIntent(provider, "test transcript")

        assertNull(intent.incidentType)
        assertTrue(intent.emergency) // the rest of the reading is still honored
    }

    @Test
    fun `an out-of-range confidence is coerced rather than crashing`() = runTest {
        val provider = GeminiIntentProvider(
            fakeGenerator(
                """{"emergency": true, "incidentType": "FIRE", "confidence": 1.4, "userContext": "test", "requestedLocation": false}""",
            ),
        )

        val intent = interpretedIntent(provider, "test transcript")

        assertEquals(1.0f, intent.confidence, 0.001f)
    }

    @Test
    fun `a blank userContext in the response falls back to the raw transcript`() = runTest {
        val provider = GeminiIntentProvider(
            fakeGenerator(
                """{"emergency": false, "incidentType": null, "confidence": 0.1, "userContext": "", "requestedLocation": false}""",
            ),
        )

        val intent = interpretedIntent(provider, "the original transcript")

        assertEquals("the original transcript", intent.userContext)
    }
}
