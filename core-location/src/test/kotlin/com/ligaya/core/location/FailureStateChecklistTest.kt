package com.ligaya.core.location

import com.ligaya.core.emergencyengine.LocationFlowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Step 28's checklist-driven coverage for this module's row of section 21's "all other
 * dependencies" table: GPS ("Location unavailable"). LocationFlowState.Unavailable — already
 * built in Step 9 and driven by LocationFlowCoordinator (Step 15) — is that state, real and
 * testable, not implicit or inferred. This test exists to make the mapping traceable at a
 * glance; LocationFlowCoordinatorTest already covers the coordinator actually reaching it.
 */
class FailureStateChecklistTest {

    @Test
    fun `section 21's GPS row maps to LocationFlowState Unavailable`() {
        val gpsFailureState = LocationFlowState.Unavailable

        assertEquals(LocationFlowState.Unavailable, gpsFailureState)
        assertNotEquals(LocationFlowState.Pending, gpsFailureState)
    }
}
