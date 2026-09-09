package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.IncidentType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * This step's own "smaller live-API smoke test" — a real call to the real Gemini API, which
 * needs a real API key only the app's operator can obtain (Google AI Studio). Never runs in CI
 * or on a machine without one: every test method starts by checking the GEMINI_API_KEY
 * environment variable and skips (not fails) via Assume.assumeTrue when it's absent, so this
 * file compiles and exists as a ready-to-run check without ever blocking a normal build.
 *
 * Deliberately asserts Interpreted, not just any outcome: an Unavailable result here means a
 * real integration bug (wrong model name, malformed request, ...), and this test's whole job is
 * catching exactly that — silently accepting Unavailable would defeat the point.
 *
 * Run locally with a real key: `GEMINI_API_KEY=... ./gradlew :core-ai:test --tests
 * "*GeminiLiveApiSmokeTest*"` (or set it as an environment variable however your shell prefers).
 */
class GeminiLiveApiSmokeTest {

    private val apiKey: String? = System.getenv("GEMINI_API_KEY")

    @Test
    fun `the doc's own example phrase produces a real, schema-valid FIRE intent`() = runTest {
        assumeTrue("GEMINI_API_KEY not set — skipping live Gemini smoke test", apiKey != null)

        val provider = GeminiIntentProvider(HttpGeminiContentGenerator(apiKey!!))

        val outcome = provider.interpret("Ligaya, tulong. May sunog.")

        assertTrue("expected Interpreted, got $outcome", outcome is VoiceInterpretationOutcome.Interpreted)
        val intent = (outcome as VoiceInterpretationOutcome.Interpreted).intent
        assertEquals(true, intent.emergency)
        assertEquals(IncidentType.FIRE, intent.incidentType)
        assertTrue(intent.userContext.isNotBlank())
        assertTrue(intent.confidence in 0f..1f)
    }

    @Test
    fun `ordinary non-emergency speech does not falsely trigger an emergency`() = runTest {
        assumeTrue("GEMINI_API_KEY not set — skipping live Gemini smoke test", apiKey != null)

        val provider = GeminiIntentProvider(HttpGeminiContentGenerator(apiKey!!))

        val outcome = provider.interpret("Kumusta ka, Ligaya? Ano balita?")

        assertTrue("expected Interpreted, got $outcome", outcome is VoiceInterpretationOutcome.Interpreted)
        assertEquals(false, (outcome as VoiceInterpretationOutcome.Interpreted).intent.emergency)
    }
}
