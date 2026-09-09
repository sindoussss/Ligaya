package com.ligaya.core.emergencyengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers this step's acceptance criteria: the engine accepts a fake structured intent (no real
 * Gemini call exists anywhere in this test) and transitions correctly — high confidence,
 * low confidence, and a non-emergency reading, per section 10's decision box.
 */
class EmergencyIntentEvaluatorTest {

    private fun fakeIntent(
        emergency: Boolean,
        confidence: Float,
        incidentType: IncidentType? = IncidentType.FIRE,
        userContext: String = "may sunog sa kusina",
        requestedLocation: Boolean = true,
    ) = StructuredEmergencyIntent(emergency, incidentType, confidence, userContext, requestedLocation)

    @Test
    fun `high-confidence emergency intent is Confirmed`() {
        val intent = fakeIntent(emergency = true, confidence = 0.95f)
        assertEquals(EmergencyIntentDecision.Confirmed(intent), EmergencyIntentEvaluator.evaluate(intent))
    }

    @Test
    fun `low-confidence emergency intent needs clarification`() {
        val intent = fakeIntent(emergency = true, confidence = 0.2f)
        assertEquals(EmergencyIntentDecision.NeedsClarification(intent), EmergencyIntentEvaluator.evaluate(intent))
    }

    @Test
    fun `non-emergency intent needs clarification even at full confidence`() {
        val intent = fakeIntent(emergency = false, confidence = 1.0f, incidentType = null)
        assertEquals(EmergencyIntentDecision.NeedsClarification(intent), EmergencyIntentEvaluator.evaluate(intent))
    }

    @Test
    fun `confidence exactly at the threshold is Confirmed (inclusive boundary)`() {
        val intent = fakeIntent(emergency = true, confidence = EmergencyIntentEvaluator.CONFIRM_THRESHOLD)
        assertTrue(EmergencyIntentEvaluator.evaluate(intent) is EmergencyIntentDecision.Confirmed)
    }

    @Test
    fun `confidence just below the threshold needs clarification`() {
        val justBelow = EmergencyIntentEvaluator.CONFIRM_THRESHOLD - 0.01f
        val intent = fakeIntent(emergency = true, confidence = justBelow)
        assertTrue(EmergencyIntentEvaluator.evaluate(intent) is EmergencyIntentDecision.NeedsClarification)
    }

    @Test
    fun `confidence outside 0 to 1 is rejected at construction, not silently accepted`() {
        assertThrows(IllegalArgumentException::class.java) {
            fakeIntent(emergency = true, confidence = 1.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            fakeIntent(emergency = true, confidence = -0.1f)
        }
    }

    // --- Engine wiring: submitStructuredIntent ---

    @Test
    fun `a Confirmed intent drives a fresh engine all the way to EMERGENCY_ACTIVE`() {
        val engine = EmergencyStateMachine()
        val decision = engine.submitStructuredIntent(fakeIntent(emergency = true, confidence = 0.9f))

        assertTrue(decision is EmergencyIntentDecision.Confirmed)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, engine.snapshot.state)
    }

    @Test
    fun `a clarification-needed intent never touches the engine's state`() {
        val engine = EmergencyStateMachine()
        val decision = engine.submitStructuredIntent(fakeIntent(emergency = true, confidence = 0.1f))

        assertTrue(decision is EmergencyIntentDecision.NeedsClarification)
        assertEquals(EmergencyState.IDLE, engine.snapshot.state)
    }

    @Test
    fun `a non-emergency intent never touches the engine's state`() {
        val engine = EmergencyStateMachine()
        val decision = engine.submitStructuredIntent(fakeIntent(emergency = false, confidence = 0.99f, incidentType = null))

        assertTrue(decision is EmergencyIntentDecision.NeedsClarification)
        assertEquals(EmergencyState.IDLE, engine.snapshot.state)
    }

    @Test
    fun `submitting a Confirmed intent to an already-active engine is a harmless no-op on state`() {
        val engine = EmergencyStateMachine(initial = EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE))
        val decision = engine.submitStructuredIntent(fakeIntent(emergency = true, confidence = 0.9f))

        // The decision is still computed honestly...
        assertTrue(decision is EmergencyIntentDecision.Confirmed)
        // ...but the underlying transitions were illegal from EMERGENCY_ACTIVE and simply failed,
        // exactly as calling detectEmergency() directly out of order always does (Step 9) —
        // this never corrupts or force-overwrites an already-active session.
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, engine.snapshot.state)
    }

    // --- Step 23: proving the voice path is the SAME entry point as SOS, not a parallel one ---

    @Test
    fun `a Confirmed voice intent reaches the identical snapshot a direct SOS-style call sequence would`() {
        // "SOS-style" here means calling the three transitions directly and unconditionally —
        // exactly what DefaultEmergencyController.triggerSos() does (Step 13, core-data) once it
        // has decided to proceed. There is no separate "voice transition sequence" anywhere in
        // this engine for submitStructuredIntent to invoke instead; both paths bottom out in
        // these same three calls on the same class.
        val sosEngine = EmergencyStateMachine()
        sosEngine.detectEmergency()
        sosEngine.confirmEmergency()
        sosEngine.activateEmergency()

        val voiceEngine = EmergencyStateMachine()
        val decision = voiceEngine.submitStructuredIntent(fakeIntent(emergency = true, confidence = 0.95f))

        assertTrue(decision is EmergencyIntentDecision.Confirmed)
        assertEquals(sosEngine.snapshot.state, voiceEngine.snapshot.state)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, voiceEngine.snapshot.state)
        // Subsystems are untouched by either path — proving neither one does anything the other
        // doesn't; the only difference between them is the confidence gate before the identical
        // three calls, not the calls themselves.
        assertEquals(sosEngine.snapshot.subsystems, voiceEngine.snapshot.subsystems)
    }

    @Test
    fun `every IncidentType value round-trips through a Confirmed decision unchanged`() {
        for (type in IncidentType.values()) {
            val engine = EmergencyStateMachine()
            val intent = fakeIntent(emergency = true, confidence = 0.9f, incidentType = type)
            val decision = engine.submitStructuredIntent(intent) as EmergencyIntentDecision.Confirmed
            assertEquals(type, decision.intent.incidentType)
        }
    }
}
