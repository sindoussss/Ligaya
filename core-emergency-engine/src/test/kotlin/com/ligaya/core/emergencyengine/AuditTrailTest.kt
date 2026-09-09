package com.ligaya.core.emergencyengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 31's acceptance criteria at the engine level: a full emergency lifecycle produces a
 * complete, ordered audit trail, and only ever from calls the engine actually accepted.
 */
class AuditTrailTest {

    @Test
    fun `a fresh engine starts with an empty audit trail`() {
        val engine = EmergencyStateMachine()
        assertTrue(engine.auditTrail.isEmpty())
    }

    @Test
    fun `a successful main transition appends exactly one ordered entry`() {
        val engine = EmergencyStateMachine()
        engine.detectEmergency()
        assertEquals(
            listOf(AuditEvent.MainStateTransition(EmergencyState.IDLE, EmergencyState.EMERGENCY_DETECTED)),
            engine.auditTrail,
        )
    }

    @Test
    fun `a rejected main transition appends nothing`() {
        val engine = EmergencyStateMachine()
        val result = engine.activateEmergency() // illegal from IDLE
        assertTrue(result.isFailure)
        assertTrue(engine.auditTrail.isEmpty())
    }

    @Test
    fun `a successful subsystem update appends exactly one ordered entry with the correct from and to`() {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        engine.updateLocationFlow(LocationFlowState.InProgress)
        assertEquals(
            listOf(
                AuditEvent.SubsystemStateChanged(
                    AuditedSubsystem.LOCATION,
                    from = LocationFlowState.Pending.toString(),
                    to = LocationFlowState.InProgress.toString(),
                ),
            ),
            engine.auditTrail,
        )
    }

    @Test
    fun `a rejected subsystem update appends nothing`() {
        val engine = engineAt(EmergencyState.IDLE) // subsystem updates not allowed yet
        val result = engine.updateLocationFlow(LocationFlowState.InProgress)
        assertTrue(result.isFailure)
        assertTrue(engine.auditTrail.isEmpty())
    }

    @Test
    fun `a subsystem moving to a failure state produces an ordinary entry, not a special one`() {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        engine.updateUnified911Flow(Unified911FlowState.CallFailed)
        assertEquals(
            listOf(
                AuditEvent.SubsystemStateChanged(
                    AuditedSubsystem.UNIFIED_911,
                    from = Unified911FlowState.Pending.toString(),
                    to = Unified911FlowState.CallFailed.toString(),
                ),
            ),
            engine.auditTrail,
        )
    }

    @Test
    fun `restoring from a persisted snapshot starts a fresh, empty audit trail for the new instance`() {
        val original = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        original.updateLocationFlow(LocationFlowState.Succeeded)
        assertEquals(1, original.auditTrail.size)

        val restored = EmergencyStateMachine(initial = original.snapshot)
        assertTrue(restored.auditTrail.isEmpty())
    }

    @Test
    fun `a full emergency lifecycle, including one subsystem failure, produces a complete ordered trail`() {
        val engine = EmergencyStateMachine()

        engine.detectEmergency()
        engine.confirmEmergency()
        engine.activateEmergency()
        engine.updateLocationFlow(LocationFlowState.Succeeded)
        engine.updateUnified911Flow(Unified911FlowState.CallFailed)
        engine.updateEmergencyServiceFlow(EmergencyServiceFlowState.Succeeded)
        engine.updateFamilyAlertFlow(FamilyAlertFlowState.Succeeded)
        engine.updateEmergencyCompanion(EmergencyCompanionState.Active)
        engine.markUserSafe()
        engine.resolveEmergency()
        engine.closeEmergency()

        val expected = listOf(
            AuditEvent.MainStateTransition(EmergencyState.IDLE, EmergencyState.EMERGENCY_DETECTED),
            AuditEvent.MainStateTransition(EmergencyState.EMERGENCY_DETECTED, EmergencyState.EMERGENCY_CONFIRMED),
            AuditEvent.MainStateTransition(EmergencyState.EMERGENCY_CONFIRMED, EmergencyState.EMERGENCY_ACTIVE),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.LOCATION, LocationFlowState.Pending.toString(), LocationFlowState.Succeeded.toString()),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.UNIFIED_911, Unified911FlowState.Pending.toString(), Unified911FlowState.CallFailed.toString()),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.EMERGENCY_SERVICE, EmergencyServiceFlowState.Pending.toString(), EmergencyServiceFlowState.Succeeded.toString()),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.FAMILY_ALERT, FamilyAlertFlowState.Pending.toString(), FamilyAlertFlowState.Succeeded.toString()),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.COMPANION, EmergencyCompanionState.Pending.toString(), EmergencyCompanionState.Active.toString()),
            AuditEvent.MainStateTransition(EmergencyState.EMERGENCY_ACTIVE, EmergencyState.USER_MARKED_SAFE),
            AuditEvent.MainStateTransition(EmergencyState.USER_MARKED_SAFE, EmergencyState.EMERGENCY_RESOLVED),
            AuditEvent.MainStateTransition(EmergencyState.EMERGENCY_RESOLVED, EmergencyState.CLOSED),
        )

        assertEquals(expected, engine.auditTrail)
    }

    private fun engineAt(state: EmergencyState): EmergencyStateMachine =
        EmergencyStateMachine(initial = EmergencySnapshot(state = state))
}
