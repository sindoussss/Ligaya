package com.ligaya.core.emergencyengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers this step's acceptance criteria: every transition in section 25's diagram is
 * implemented, and every illegal transition — including the roadmap's own named example,
 * IDLE -> EMERGENCY_ACTIVE directly — is rejected.
 */
class EmergencyStateMachineTest {

    // --- Legal transitions, one at a time ---

    @Test
    fun `IDLE to EMERGENCY_DETECTED succeeds`() {
        val engine = EmergencyStateMachine()
        val result = engine.detectEmergency()
        assertTrue(result.isSuccess)
        assertEquals(EmergencyState.EMERGENCY_DETECTED, engine.snapshot.state)
    }

    @Test
    fun `EMERGENCY_DETECTED to EMERGENCY_CONFIRMED succeeds`() {
        val engine = engineAt(EmergencyState.EMERGENCY_DETECTED)
        val result = engine.confirmEmergency()
        assertTrue(result.isSuccess)
        assertEquals(EmergencyState.EMERGENCY_CONFIRMED, engine.snapshot.state)
    }

    @Test
    fun `EMERGENCY_CONFIRMED to EMERGENCY_ACTIVE succeeds`() {
        val engine = engineAt(EmergencyState.EMERGENCY_CONFIRMED)
        val result = engine.activateEmergency()
        assertTrue(result.isSuccess)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, engine.snapshot.state)
    }

    @Test
    fun `EMERGENCY_ACTIVE to USER_MARKED_SAFE succeeds`() {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val result = engine.markUserSafe()
        assertTrue(result.isSuccess)
        assertEquals(EmergencyState.USER_MARKED_SAFE, engine.snapshot.state)
    }

    @Test
    fun `USER_MARKED_SAFE to EMERGENCY_RESOLVED succeeds`() {
        val engine = engineAt(EmergencyState.USER_MARKED_SAFE)
        val result = engine.resolveEmergency()
        assertTrue(result.isSuccess)
        assertEquals(EmergencyState.EMERGENCY_RESOLVED, engine.snapshot.state)
    }

    @Test
    fun `EMERGENCY_RESOLVED to CLOSED succeeds`() {
        val engine = engineAt(EmergencyState.EMERGENCY_RESOLVED)
        val result = engine.closeEmergency()
        assertTrue(result.isSuccess)
        assertEquals(EmergencyState.CLOSED, engine.snapshot.state)
    }

    // --- The roadmap's explicit named example ---

    @Test
    fun `IDLE to EMERGENCY_ACTIVE directly fails`() {
        val engine = EmergencyStateMachine()
        val result = engine.activateEmergency()
        assertTrue(result.isFailure)
        assertEquals(EmergencyState.IDLE, engine.snapshot.state) // unchanged
        val error = result.exceptionOrNull() as IllegalEmergencyTransitionException
        assertEquals(EmergencyState.IDLE, error.from)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, error.to)
    }

    // --- Exhaustive: from every state, only the one legal target succeeds ---

    @Test
    fun `from every state, only the documented next state is reachable`() {
        val legalNext = mapOf(
            EmergencyState.IDLE to EmergencyState.EMERGENCY_DETECTED,
            EmergencyState.EMERGENCY_DETECTED to EmergencyState.EMERGENCY_CONFIRMED,
            EmergencyState.EMERGENCY_CONFIRMED to EmergencyState.EMERGENCY_ACTIVE,
            EmergencyState.EMERGENCY_ACTIVE to EmergencyState.USER_MARKED_SAFE,
            EmergencyState.USER_MARKED_SAFE to EmergencyState.EMERGENCY_RESOLVED,
            EmergencyState.EMERGENCY_RESOLVED to EmergencyState.CLOSED,
        )

        for (from in EmergencyState.values()) {
            for (to in EmergencyState.values()) {
                val engine = engineAt(from)
                val result = attemptTransitionTo(engine, to)
                val shouldSucceed = legalNext[from] == to
                assertEquals(
                    "transition $from -> $to should ${if (shouldSucceed) "succeed" else "fail"}",
                    shouldSucceed,
                    result.isSuccess,
                )
            }
        }
    }

    @Test
    fun `CLOSED is terminal — no transition out of it succeeds`() {
        for (to in EmergencyState.values()) {
            val engine = engineAt(EmergencyState.CLOSED)
            val result = attemptTransitionTo(engine, to)
            assertTrue("CLOSED -> $to should fail", result.isFailure)
        }
    }

    // --- Resolution rule: USER_MARKED_SAFE never depends on subsystem completion ---

    @Test
    fun `markUserSafe succeeds even when every subsystem is still Pending`() {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        assertEquals(ConcurrentSubsystemStates(), engine.snapshot.subsystems) // all Pending
        assertTrue(engine.markUserSafe().isSuccess)
    }

    @Test
    fun `markUserSafe succeeds even when every subsystem has failed`() {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        engine.updateLocationFlow(LocationFlowState.Unavailable)
        engine.updateUnified911Flow(Unified911FlowState.CallFailed)
        engine.updateEmergencyServiceFlow(EmergencyServiceFlowState.LookupFailed)
        engine.updateFamilyAlertFlow(FamilyAlertFlowState.DeliveryFailed)

        assertTrue(engine.markUserSafe().isSuccess)
    }

    @Test
    fun `markUserSafe succeeds even when every subsystem has succeeded`() {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        engine.updateLocationFlow(LocationFlowState.Succeeded)
        engine.updateUnified911Flow(Unified911FlowState.Succeeded)
        engine.updateEmergencyServiceFlow(EmergencyServiceFlowState.Succeeded)
        engine.updateFamilyAlertFlow(FamilyAlertFlowState.Succeeded)
        engine.updateEmergencyCompanion(EmergencyCompanionState.Active)

        assertTrue(engine.markUserSafe().isSuccess)
    }

    // --- Resolution rule: EMERGENCY_RESOLVED never depends on subsystem completion either ---

    @Test
    fun `resolveEmergency succeeds even when every subsystem is still Pending`() {
        val engine = engineAt(EmergencyState.USER_MARKED_SAFE)
        assertEquals(ConcurrentSubsystemStates(), engine.snapshot.subsystems) // all Pending
        val result = engine.resolveEmergency()
        assertTrue(result.isSuccess)
        assertEquals(EmergencyState.EMERGENCY_RESOLVED, engine.snapshot.state)
    }

    @Test
    fun `resolveEmergency succeeds even when every subsystem has failed, and the episode can still be closed`() {
        val engine = engineAt(EmergencyState.USER_MARKED_SAFE)
        engine.updateLocationFlow(LocationFlowState.Unavailable)
        engine.updateUnified911Flow(Unified911FlowState.CallFailed)
        engine.updateEmergencyServiceFlow(EmergencyServiceFlowState.LookupFailed)
        engine.updateFamilyAlertFlow(FamilyAlertFlowState.DeliveryFailed)

        val resolveResult = engine.resolveEmergency()
        assertTrue(resolveResult.isSuccess)
        assertEquals(EmergencyState.EMERGENCY_RESOLVED, engine.snapshot.state)

        val closeResult = engine.closeEmergency()
        assertTrue(closeResult.isSuccess)
        assertEquals(EmergencyState.CLOSED, engine.snapshot.state)
    }

    // --- Subsystem update window ---

    @Test
    fun `subsystem updates are rejected before EMERGENCY_ACTIVE`() {
        for (state in listOf(EmergencyState.IDLE, EmergencyState.EMERGENCY_DETECTED, EmergencyState.EMERGENCY_CONFIRMED)) {
            val engine = engineAt(state)
            val result = engine.updateLocationFlow(LocationFlowState.InProgress)
            assertTrue("subsystem update should be rejected in $state", result.isFailure)
            assertTrue(result.exceptionOrNull() is SubsystemUpdateNotAllowedException)
        }
    }

    @Test
    fun `subsystem updates are rejected once CLOSED`() {
        val engine = engineAt(EmergencyState.CLOSED)
        val result = engine.updateLocationFlow(LocationFlowState.InProgress)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SubsystemUpdateNotAllowedException)
    }

    @Test
    fun `subsystem updates are allowed for EMERGENCY_ACTIVE, USER_MARKED_SAFE, and EMERGENCY_RESOLVED`() {
        for (state in listOf(EmergencyState.EMERGENCY_ACTIVE, EmergencyState.USER_MARKED_SAFE, EmergencyState.EMERGENCY_RESOLVED)) {
            val engine = engineAt(state)
            val result = engine.updateFamilyAlertFlow(FamilyAlertFlowState.Succeeded)
            assertTrue("subsystem update should be allowed in $state", result.isSuccess)
            assertEquals(FamilyAlertFlowState.Succeeded, engine.snapshot.subsystems.familyAlert)
        }
    }

    @Test
    fun `a rejected subsystem update does not change the snapshot`() {
        val engine = engineAt(EmergencyState.IDLE)
        val before = engine.snapshot
        engine.updateLocationFlow(LocationFlowState.Succeeded)
        assertEquals(before, engine.snapshot)
    }

    // --- Full happy-path episode, end to end ---

    @Test
    fun `a full emergency episode walks every state in order`() {
        val engine = EmergencyStateMachine()

        assertTrue(engine.detectEmergency().isSuccess)
        assertTrue(engine.confirmEmergency().isSuccess)
        assertTrue(engine.activateEmergency().isSuccess)

        assertTrue(engine.updateLocationFlow(LocationFlowState.Succeeded).isSuccess)
        assertTrue(engine.updateUnified911Flow(Unified911FlowState.Succeeded).isSuccess)
        assertTrue(engine.updateEmergencyServiceFlow(EmergencyServiceFlowState.Succeeded).isSuccess)
        assertTrue(engine.updateFamilyAlertFlow(FamilyAlertFlowState.Succeeded).isSuccess)
        assertTrue(engine.updateEmergencyCompanion(EmergencyCompanionState.Active).isSuccess)

        assertTrue(engine.markUserSafe().isSuccess)
        assertTrue(engine.resolveEmergency().isSuccess)
        assertTrue(engine.closeEmergency().isSuccess)

        assertEquals(EmergencyState.CLOSED, engine.snapshot.state)
    }

    @Test
    fun `a snapshot can be restored into a fresh engine instance (crash recovery shape)`() {
        val originalEngine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        originalEngine.updateLocationFlow(LocationFlowState.InProgress)
        val persisted = originalEngine.snapshot

        val restoredEngine = EmergencyStateMachine(initial = persisted)

        assertEquals(persisted, restoredEngine.snapshot)
        assertTrue(restoredEngine.markUserSafe().isSuccess)
    }

    // --- helpers ---

    private fun engineAt(state: EmergencyState): EmergencyStateMachine =
        EmergencyStateMachine(initial = EmergencySnapshot(state = state))

    private fun attemptTransitionTo(engine: EmergencyStateMachine, target: EmergencyState): Result<EmergencySnapshot> =
        when (target) {
            EmergencyState.IDLE -> Result.failure(UnsupportedOperationException("no transition targets IDLE"))
            EmergencyState.EMERGENCY_DETECTED -> engine.detectEmergency()
            EmergencyState.EMERGENCY_CONFIRMED -> engine.confirmEmergency()
            EmergencyState.EMERGENCY_ACTIVE -> engine.activateEmergency()
            EmergencyState.USER_MARKED_SAFE -> engine.markUserSafe()
            EmergencyState.EMERGENCY_RESOLVED -> engine.resolveEmergency()
            EmergencyState.CLOSED -> engine.closeEmergency()
        }
}
