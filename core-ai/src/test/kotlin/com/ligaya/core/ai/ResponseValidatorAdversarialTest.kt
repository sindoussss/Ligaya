package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 47's own acceptance criterion: "zero unconfirmed-claim leaks through to TTS across the
 * full adversarial set." Step 25's own baseline (ResponseValidatorTest) only ever used the exact
 * literal phrasing each [ResponseValidator] regex was written against ("help has arrived", "911
 * has been contacted", ...) — this suite is deliberately adversarial instead: every phrase below
 * asserts the same underlying claim using different, indirect, or implied wording a real Gemini
 * response could plausibly use, including this step's own named example ("you're safe now,
 * they're coming"). A phrase "leaking through" means [ValidatedResponse.Passed] with the claim
 * sentence still present — that is the one outcome every case below must never produce when the
 * corresponding subsystem is unverified.
 */
class ResponseValidatorAdversarialTest {

    private val allPending = EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE)

    private fun withUnified911(state: Unified911FlowState) =
        allPending.copy(subsystems = ConcurrentSubsystemStates(unified911 = state))

    private fun withFamilyAlert(state: FamilyAlertFlowState) =
        allPending.copy(subsystems = ConcurrentSubsystemStates(familyAlert = state))

    /** True only if the claim sentence is nowhere left in what would actually reach TTS. */
    private fun leaksThrough(result: ValidatedResponse, claimFragment: String): Boolean = when (result) {
        is ValidatedResponse.Passed -> result.text.contains(claimFragment, ignoreCase = true)
        is ValidatedResponse.Rewritten -> result.rewrittenText.contains(claimFragment, ignoreCase = true)
        is ValidatedResponse.Blocked -> false
    }

    // --- "Help is coming / on the way" — indirect and implied phrasings, unverified 911 ---

    @Test
    fun `indirect help-is-coming phrasings never leak through when 911 has not succeeded`() {
        val adversarialPhrases = listOf(
            "You're safe now, they're coming.", // this step's own named example
            "They're on their way.",
            "Someone will be there soon.",
            "You won't be alone for long.",
            "Rescue is en route.",
            "Officers are heading your way.",
            "Hang tight, they're almost there.",
            "The paramedics are close by.",
            "Emergency services are en route.",
            "The ambulance will be there any minute.",
        )

        for (phrase in adversarialPhrases) {
            val result = ResponseValidator.validate(phrase, allPending)
            assertFalse("leaked through unverified: \"$phrase\" -> $result", leaksThrough(result, "coming"))
        }
    }

    @Test
    fun `they're coming passes cleanly once 911 has actually succeeded`() {
        val snapshot = withUnified911(Unified911FlowState.Succeeded)
        val result = ResponseValidator.validate("You're safe now, they're coming.", snapshot)
        assertTrue("expected Passed once confirmed, got $result", result is ValidatedResponse.Passed)
    }

    // --- "911 contacted" — indirect phrasings, unverified ---

    @Test
    fun `indirect 911-contacted phrasings never leak through when 911 has not succeeded`() {
        val adversarialPhrases = listOf(
            "I've reached emergency services.",
            "The call went through.",
            "Dispatch has your location.",
            "911 picked up.",
            "Emergency services are aware of your situation.",
        )

        for (phrase in adversarialPhrases) {
            val result = ResponseValidator.validate(phrase, allPending)
            assertTrue(
                "expected this claim to be recognized and blocked, got Passed unchanged: \"$phrase\" -> $result",
                result !is ValidatedResponse.Passed,
            )
        }
    }

    // --- "Family notified" — indirect phrasings, unverified ---

    @Test
    fun `indirect family-notified phrasings never leak through when the family alert has not succeeded`() {
        val adversarialPhrases = listOf(
            "Your loved ones know what's happening.",
            "I've let your family know.",
            "Your mom knows you're in trouble.",
            "Your emergency contacts have been reached.",
        )

        for (phrase in adversarialPhrases) {
            val result = ResponseValidator.validate(phrase, allPending)
            assertTrue(
                "expected this claim to be recognized and blocked, got Passed unchanged: \"$phrase\" -> $result",
                result !is ValidatedResponse.Passed,
            )
        }
    }

    @Test
    fun `indirect family-notified phrasing passes cleanly once the family alert has actually succeeded`() {
        val snapshot = withFamilyAlert(FamilyAlertFlowState.Succeeded)
        val result = ResponseValidator.validate("I've let your family know.", snapshot)
        assertTrue("expected Passed once confirmed, got $result", result is ValidatedResponse.Passed)
    }

    // --- "Help arrived" (physical presence) — indirect phrasings, never verifiable at all ---

    @Test
    fun `indirect help-arrived phrasings are always blocked, even with every subsystem succeeded`() {
        val everythingSucceeded = allPending.copy(
            subsystems = ConcurrentSubsystemStates(
                unified911 = Unified911FlowState.Succeeded,
                familyAlert = FamilyAlertFlowState.Succeeded,
            ),
        )
        val adversarialPhrases = listOf(
            "They're right outside.",
            "The rescue team is with you now.",
            "Someone's at your door.",
            "They just got there.",
        )

        for (phrase in adversarialPhrases) {
            val result = ResponseValidator.validate(phrase, everythingSucceeded)
            assertTrue(
                "help-arrived claims must never pass, even fully confirmed: \"$phrase\" -> $result",
                result !is ValidatedResponse.Passed,
            )
        }
    }

    // --- A realistic multi-sentence adversarial reply mixing safe and unverified content ---

    @Test
    fun `a realistic reply mixing safe reassurance with an indirect unconfirmed claim never leaks the claim`() {
        val result = ResponseValidator.validate(
            "I'm right here with you, take a deep breath. You're safe now, they're coming, just hold on.",
            allPending,
        )

        assertFalse("the unconfirmed claim leaked through: $result", leaksThrough(result, "coming"))
    }
}
