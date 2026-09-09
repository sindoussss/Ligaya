package com.ligaya.core.uistate

import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 35's own acceptance criterion, verified directly: every value of every engine state type
 * defined through Step 28 (EmergencyState plus all five of ConcurrentSubsystemStates' fields) has
 * a presentation mapping, checked here one value at a time — not just "the `when` compiles,"
 * which the compiler already guarantees for these sealed/enum types, but "the label and tone are
 * the ones this step actually intends."
 */
class StatePresentationMappingsTest {

    @Test
    fun `every EmergencyState value maps to the intended label and tone`() {
        val expected = mapOf(
            EmergencyState.IDLE to ("Idle" to PresentationTone.NEUTRAL),
            EmergencyState.EMERGENCY_DETECTED to ("Emergency detected" to PresentationTone.EMERGENCY),
            EmergencyState.EMERGENCY_CONFIRMED to ("Emergency confirmed" to PresentationTone.EMERGENCY),
            EmergencyState.EMERGENCY_ACTIVE to ("Emergency active" to PresentationTone.EMERGENCY),
            EmergencyState.USER_MARKED_SAFE to ("Marked safe" to PresentationTone.SUCCESS),
            EmergencyState.EMERGENCY_RESOLVED to ("Resolved" to PresentationTone.SUCCESS),
            EmergencyState.CLOSED to ("Closed" to PresentationTone.NEUTRAL),
        )
        // Proves every enum constant was included above, not just that the ones listed pass.
        assertEquals(EmergencyState.entries.toSet(), expected.keys)

        for ((state, labelAndTone) in expected) {
            val presentation = state.toPresentation()
            assertEquals("label for $state", labelAndTone.first, presentation.label)
            assertEquals("tone for $state", labelAndTone.second, presentation.tone)
        }
    }

    @Test
    fun `every LocationFlowState value maps to the intended label and tone`() {
        val expected = mapOf(
            LocationFlowState.Pending to ("Waiting to acquire location" to PresentationTone.PENDING),
            LocationFlowState.InProgress to ("Acquiring location…" to PresentationTone.IN_PROGRESS),
            LocationFlowState.Succeeded to ("Location acquired" to PresentationTone.SUCCESS),
            LocationFlowState.Unavailable to ("Location unavailable" to PresentationTone.FAILURE),
        )
        for ((state, labelAndTone) in expected) {
            val presentation = state.toPresentation()
            assertEquals(labelAndTone.first, presentation.label)
            assertEquals(labelAndTone.second, presentation.tone)
        }
    }

    @Test
    fun `every Unified911FlowState value maps to the intended label and tone`() {
        val expected = mapOf(
            Unified911FlowState.Pending to ("Waiting to call 911" to PresentationTone.PENDING),
            Unified911FlowState.InProgress to ("Dialing 911…" to PresentationTone.IN_PROGRESS),
            Unified911FlowState.Succeeded to ("Connected to 911" to PresentationTone.SUCCESS),
            Unified911FlowState.CallFailed to ("911 call failed" to PresentationTone.FAILURE),
        )
        for ((state, labelAndTone) in expected) {
            val presentation = state.toPresentation()
            assertEquals(labelAndTone.first, presentation.label)
            assertEquals(labelAndTone.second, presentation.tone)
        }
    }

    @Test
    fun `every EmergencyServiceFlowState value maps to the intended label and tone`() {
        val expected = mapOf(
            EmergencyServiceFlowState.Pending to ("Waiting to look up nearby services" to PresentationTone.PENDING),
            EmergencyServiceFlowState.InProgress to ("Looking up nearby services…" to PresentationTone.IN_PROGRESS),
            EmergencyServiceFlowState.Succeeded to ("Nearby service found" to PresentationTone.SUCCESS),
            EmergencyServiceFlowState.LookupFailed to ("Lookup failed" to PresentationTone.FAILURE),
        )
        for ((state, labelAndTone) in expected) {
            val presentation = state.toPresentation()
            assertEquals(labelAndTone.first, presentation.label)
            assertEquals(labelAndTone.second, presentation.tone)
        }
    }

    @Test
    fun `every FamilyAlertFlowState value maps to the intended label and tone`() {
        val expected = mapOf(
            FamilyAlertFlowState.Pending to ("Waiting to alert Safety Circle" to PresentationTone.PENDING),
            FamilyAlertFlowState.InProgress to ("Alerting Safety Circle…" to PresentationTone.IN_PROGRESS),
            FamilyAlertFlowState.Succeeded to ("Safety Circle alerted" to PresentationTone.SUCCESS),
            FamilyAlertFlowState.DeliveryFailed to ("Alert delivery failed" to PresentationTone.FAILURE),
        )
        for ((state, labelAndTone) in expected) {
            val presentation = state.toPresentation()
            assertEquals(labelAndTone.first, presentation.label)
            assertEquals(labelAndTone.second, presentation.tone)
        }
    }

    @Test
    fun `every EmergencyCompanionState value maps to the intended label and tone`() {
        val expected = mapOf(
            EmergencyCompanionState.Pending to ("Companion not yet engaged" to PresentationTone.PENDING),
            EmergencyCompanionState.Active to ("Companion active" to PresentationTone.IN_PROGRESS),
        )
        for ((state, labelAndTone) in expected) {
            val presentation = state.toPresentation()
            assertEquals(labelAndTone.first, presentation.label)
            assertEquals(labelAndTone.second, presentation.tone)
        }
    }

    @Test
    fun `icon follows tone deterministically for every tone value`() {
        val expected = mapOf(
            PresentationTone.NEUTRAL to PresentationIcon.NONE,
            PresentationTone.PENDING to PresentationIcon.SCHEDULE,
            PresentationTone.IN_PROGRESS to PresentationIcon.SYNC,
            PresentationTone.SUCCESS to PresentationIcon.CHECK,
            PresentationTone.FAILURE to PresentationIcon.ERROR,
            PresentationTone.EMERGENCY to PresentationIcon.EMERGENCY,
        )
        assertEquals(PresentationTone.entries.toSet(), expected.keys)
        for ((tone, icon) in expected) {
            assertEquals(icon, StatePresentation(label = "irrelevant", tone = tone).icon)
        }
    }

    @Test
    fun `no mapped label is blank`() {
        val allPresentations = EmergencyState.entries.map { it.toPresentation() } +
            listOf(LocationFlowState.Pending, LocationFlowState.InProgress, LocationFlowState.Succeeded, LocationFlowState.Unavailable).map { it.toPresentation() } +
            listOf(Unified911FlowState.Pending, Unified911FlowState.InProgress, Unified911FlowState.Succeeded, Unified911FlowState.CallFailed).map { it.toPresentation() } +
            listOf(EmergencyServiceFlowState.Pending, EmergencyServiceFlowState.InProgress, EmergencyServiceFlowState.Succeeded, EmergencyServiceFlowState.LookupFailed).map { it.toPresentation() } +
            listOf(FamilyAlertFlowState.Pending, FamilyAlertFlowState.InProgress, FamilyAlertFlowState.Succeeded, FamilyAlertFlowState.DeliveryFailed).map { it.toPresentation() } +
            listOf(EmergencyCompanionState.Pending, EmergencyCompanionState.Active).map { it.toPresentation() }

        assertEquals(25, allPresentations.size) // 7 + 4 + 4 + 4 + 4 + 2
        for (presentation in allPresentations) {
            assertTrue("label must not be blank: $presentation", presentation.label.isNotBlank())
        }
    }
}
