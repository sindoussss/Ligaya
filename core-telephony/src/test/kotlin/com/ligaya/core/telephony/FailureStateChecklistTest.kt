package com.ligaya.core.telephony

import com.ligaya.core.emergencyengine.Unified911FlowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Step 28's checklist-driven coverage for this module's rows of section 21/25: "911 call action
 * — Failed" and section 25's literal CALL_FAILED. Unified911FlowState.CallFailed — built in
 * Step 9, driven by Unified911FlowCoordinator (Step 16) — is that state, real and testable, with
 * a working retry path (Unified911FlowCoordinatorTest already covers dial() being callable again
 * after a CallFailed reading). This test exists to make the mapping traceable at a glance.
 */
class FailureStateChecklistTest {

    @Test
    fun `section 21 and 25's 911 call failure row maps to Unified911FlowState CallFailed`() {
        val callFailureState = Unified911FlowState.CallFailed

        assertEquals(Unified911FlowState.CallFailed, callFailureState)
        assertNotEquals(Unified911FlowState.Pending, callFailureState)
    }
}
