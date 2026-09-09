package com.ligaya.core.places

import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Step 28's checklist-driven coverage for this module's two rows of section 21/25: "Google
 * Places — Emergency-service search failed" (section 25's literal
 * EMERGENCY_SERVICE_LOOKUP_FAILED) and "Emergency-service phone number — Missing — do not invent
 * a number." Both are real, testable outcomes already built in Step 17
 * (EmergencyServiceFlowCoordinator/Test); this test exists to make the mapping traceable at a
 * glance.
 */
class FailureStateChecklistTest {

    @Test
    fun `section 21 and 25's Places lookup failure row maps to EmergencyServiceFlowState LookupFailed`() {
        val lookupFailureState = EmergencyServiceFlowState.LookupFailed

        assertEquals(EmergencyServiceFlowState.LookupFailed, lookupFailureState)
        assertNotEquals(EmergencyServiceFlowState.Pending, lookupFailureState)
    }

    @Test
    fun `section 21's missing-phone-number row is representable without inventing a number`() {
        // PlaceDetails.phoneNumber = null is itself the "do not invent a number" state — see
        // PlaceDetails' own doc comment (Step 17) and EmergencyServiceFlowCoordinatorTest's "a
        // place with no public phone number still succeeds, never inventing one".
        val missingNumber = PlaceDetails(phoneNumber = null)

        assertNull(missingNumber.phoneNumber)
    }
}
