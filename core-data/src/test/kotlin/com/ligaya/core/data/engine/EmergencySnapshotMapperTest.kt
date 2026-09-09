package com.ligaya.core.data.engine

import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM — no Room/Android needed, since the mapper only touches plain data classes. */
class EmergencySnapshotMapperTest {

    @Test
    fun `every main EmergencyState round-trips through the entity`() {
        for (state in EmergencyState.values()) {
            val snapshot = EmergencySnapshot(state = state)
            val entity = snapshot.toEntity(emergencyEventId = "event-1", updatedAtEpochMillis = 1_000L)
            assertEquals(snapshot, entity.toSnapshot())
        }
    }

    @Test
    fun `every LocationFlowState round-trips`() {
        val states = listOf(
            LocationFlowState.Pending, LocationFlowState.InProgress,
            LocationFlowState.Succeeded, LocationFlowState.Unavailable,
        )
        for (locationState in states) {
            val snapshot = baseSnapshot().copy(subsystems = ConcurrentSubsystemStates(location = locationState))
            assertEquals(locationState, roundTrip(snapshot).subsystems.location)
        }
    }

    @Test
    fun `every Unified911FlowState round-trips`() {
        val states = listOf(
            Unified911FlowState.Pending, Unified911FlowState.InProgress,
            Unified911FlowState.Succeeded, Unified911FlowState.CallFailed,
        )
        for (callState in states) {
            val snapshot = baseSnapshot().copy(subsystems = ConcurrentSubsystemStates(unified911 = callState))
            assertEquals(callState, roundTrip(snapshot).subsystems.unified911)
        }
    }

    @Test
    fun `every EmergencyServiceFlowState round-trips`() {
        val states = listOf(
            EmergencyServiceFlowState.Pending, EmergencyServiceFlowState.InProgress,
            EmergencyServiceFlowState.Succeeded, EmergencyServiceFlowState.LookupFailed,
        )
        for (lookupState in states) {
            val snapshot = baseSnapshot().copy(subsystems = ConcurrentSubsystemStates(emergencyService = lookupState))
            assertEquals(lookupState, roundTrip(snapshot).subsystems.emergencyService)
        }
    }

    @Test
    fun `every FamilyAlertFlowState round-trips`() {
        val states = listOf(
            FamilyAlertFlowState.Pending, FamilyAlertFlowState.InProgress,
            FamilyAlertFlowState.Succeeded, FamilyAlertFlowState.DeliveryFailed,
        )
        for (alertState in states) {
            val snapshot = baseSnapshot().copy(subsystems = ConcurrentSubsystemStates(familyAlert = alertState))
            assertEquals(alertState, roundTrip(snapshot).subsystems.familyAlert)
        }
    }

    @Test
    fun `every EmergencyCompanionState round-trips`() {
        val states = listOf(EmergencyCompanionState.Pending, EmergencyCompanionState.Active)
        for (companionState in states) {
            val snapshot = baseSnapshot().copy(subsystems = ConcurrentSubsystemStates(companion = companionState))
            assertEquals(companionState, roundTrip(snapshot).subsystems.companion)
        }
    }

    @Test
    fun `emergencyEventId and timestamp are carried onto the entity`() {
        val entity = baseSnapshot().toEntity(emergencyEventId = "event-42", updatedAtEpochMillis = 12_345L)
        assertEquals("event-42", entity.emergencyEventId)
        assertEquals(12_345L, entity.lastUpdatedAtEpochMillis)
    }

    @Test
    fun `a null emergencyEventId is preserved`() {
        val entity = baseSnapshot().toEntity(emergencyEventId = null, updatedAtEpochMillis = 1_000L)
        assertEquals(null, entity.emergencyEventId)
    }

    private fun baseSnapshot() = EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE)

    private fun roundTrip(snapshot: EmergencySnapshot): EmergencySnapshot =
        snapshot.toEntity(emergencyEventId = "event-1", updatedAtEpochMillis = 1_000L).toSnapshot()
}
