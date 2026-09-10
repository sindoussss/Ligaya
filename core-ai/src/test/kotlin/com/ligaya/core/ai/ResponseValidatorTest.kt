package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This step's own acceptance criteria: a fixed set of adversarial Gemini outputs claiming
 * unconfirmed outcomes, each checked against varying engine states, 100% blocked or rewritten
 * before it could ever reach TTS.
 */
class ResponseValidatorTest {

    private val allPending = EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE)

    private fun withUnified911(state: Unified911FlowState) =
        allPending.copy(subsystems = ConcurrentSubsystemStates(unified911 = state))

    private fun withFamilyAlert(state: FamilyAlertFlowState) =
        allPending.copy(subsystems = ConcurrentSubsystemStates(familyAlert = state))

    private fun withLocation(state: LocationFlowState) =
        allPending.copy(subsystems = ConcurrentSubsystemStates(location = state))

    private fun withEmergencyService(state: EmergencyServiceFlowState) =
        allPending.copy(subsystems = ConcurrentSubsystemStates(emergencyService = state))

    // --- The adversarial set: every banned phrase family, against an unverified (all-Pending) state ---

    @Test
    fun `'help arrived' is always blocked, even with every subsystem succeeded`() {
        val everythingSucceeded = allPending.copy(
            subsystems = ConcurrentSubsystemStates(
                unified911 = Unified911FlowState.Succeeded,
                familyAlert = FamilyAlertFlowState.Succeeded,
            ),
        )

        val result = ResponseValidator.validate("Don't worry, help has arrived.", everythingSucceeded)

        assertTrue("expected Blocked, got $result", result is ValidatedResponse.Blocked)
    }

    @Test
    fun `'911 has been contacted' is blocked when 911 has not actually succeeded`() {
        val result = ResponseValidator.validate("911 has been contacted, stay calm.", allPending)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    @Test
    fun `'911 has been contacted' passes when 911 actually succeeded`() {
        val snapshot = withUnified911(Unified911FlowState.Succeeded)

        val result = ResponseValidator.validate("911 has been contacted.", snapshot)

        assertEquals(ValidatedResponse.Passed("911 has been contacted."), result)
    }

    @Test
    fun `'help is on the way' is blocked when 911 has not actually succeeded`() {
        val result = ResponseValidator.validate("Help is on the way, hang in there.", allPending)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    @Test
    fun `'help is on the way' passes when 911 actually succeeded`() {
        val snapshot = withUnified911(Unified911FlowState.Succeeded)

        val result = ResponseValidator.validate("Help is on the way.", snapshot)

        assertTrue(result is ValidatedResponse.Passed)
    }

    @Test
    fun `'family notified' is blocked when the family alert has not actually succeeded`() {
        val result = ResponseValidator.validate("Your family has been notified.", allPending)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    @Test
    fun `'family notified' passes when the family alert actually succeeded`() {
        val snapshot = withFamilyAlert(FamilyAlertFlowState.Succeeded)

        val result = ResponseValidator.validate("Your family has been notified.", snapshot)

        assertTrue(result is ValidatedResponse.Passed)
    }

    @Test
    fun `'location shared' is blocked when location has not actually succeeded`() {
        val result = ResponseValidator.validate("Your location has been shared with them.", allPending)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    @Test
    fun `'location shared' passes when location actually succeeded`() {
        val snapshot = withLocation(LocationFlowState.Succeeded)

        val result = ResponseValidator.validate("Your location has been shared.", snapshot)

        assertTrue(result is ValidatedResponse.Passed)
    }

    @Test
    fun `'emergency-service contacted' is blocked when the lookup has not actually succeeded`() {
        val result = ResponseValidator.validate("The nearest hospital has been contacted.", allPending)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    @Test
    fun `'emergency-service contacted' passes when the lookup actually succeeded`() {
        val snapshot = withEmergencyService(EmergencyServiceFlowState.Succeeded)

        val result = ResponseValidator.validate("The nearest hospital has been contacted.", snapshot)

        assertTrue(result is ValidatedResponse.Passed)
    }

    @Test
    fun `'SMS delivered' is blocked when the family alert has not actually succeeded, independent of the 'family notified' wording`() {
        val result = ResponseValidator.validate("The SMS has been delivered.", allPending)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    @Test
    fun `'push notification delivered' is blocked when the family alert has not actually succeeded`() {
        val result = ResponseValidator.validate("The push notification was delivered.", allPending)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    @Test
    fun `emergency-service contacted and 911 contacted are independent claims, one succeeding does not verify the other`() {
        val snapshot = withUnified911(Unified911FlowState.Succeeded)

        val result = ResponseValidator.validate("The nearest hospital has been contacted.", snapshot)

        assertTrue("911 succeeding must not verify a distinct emergency-service claim, got $result", result is ValidatedResponse.Blocked)
    }

    @Test
    fun `a claim is still blocked when the corresponding subsystem has explicitly failed, not just pending`() {
        val snapshot = withUnified911(Unified911FlowState.CallFailed)

        val result = ResponseValidator.validate("911 has been contacted.", snapshot)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    // --- Rewriting: a safe sentence survives even when a sibling sentence is blocked ---

    @Test
    fun `a safe sentence is kept while a violating sentence in the same reply is dropped`() {
        val result = ResponseValidator.validate(
            "I'm here with you. 911 has been contacted, so just hang on.",
            allPending,
        )

        assertTrue("expected Rewritten, got $result", result is ValidatedResponse.Rewritten)
        val rewritten = result as ValidatedResponse.Rewritten
        assertEquals("I'm here with you.", rewritten.rewrittenText)
        assertTrue(rewritten.blockedClaims.contains("911 has been contacted"))
    }

    @Test
    fun `multiple distinct violations in one reply are all recorded`() {
        val result = ResponseValidator.validate(
            "911 has been contacted. Your family has been notified.",
            allPending,
        )

        assertTrue(result is ValidatedResponse.Blocked)
        val blocked = result as ValidatedResponse.Blocked
        assertTrue(blocked.blockedClaims.contains("911 has been contacted"))
        assertTrue(blocked.blockedClaims.contains("family notified"))
    }

    // --- Ordinary, safe companion replies pass through completely unchanged ---

    @Test
    fun `ordinary reassuring speech with no unverified claims passes unchanged`() {
        val safeReplies = listOf(
            "I'm right here with you. Take a deep breath.",
            "Can you tell me more about what's happening?",
            "You're doing great. Stay on the line with me.",
        )

        for (reply in safeReplies) {
            assertEquals(ValidatedResponse.Passed(reply), ResponseValidator.validate(reply, allPending))
        }
    }

    @Test
    fun `matching is case-insensitive`() {
        val result = ResponseValidator.validate("HELP HAS ARRIVED!", allPending)

        assertTrue(result is ValidatedResponse.Blocked)
    }

    @Test
    fun `blank input passes through as a trivial no-op`() {
        assertEquals(ValidatedResponse.Passed(""), ResponseValidator.validate("", allPending))
    }
}
