package com.ligaya.core.telephony

import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.EmergencyStateMachine
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers this step's acceptance criteria against the real EmergencyStateMachine (Step 9) — not a
 * fake — so a passing test actually proves the engine reaches CallFailed/Succeeded, not just
 * that this class calls a mock correctly.
 */
class Unified911FlowCoordinatorTest {

    private class FakeDialAction(private val outcome: Result<Unit>) : Unified911DialAction {
        var callCount = 0
            private set

        override fun dial(): Result<Unit> {
            callCount++
            return outcome
        }
    }

    private fun engineAt(state: EmergencyState) = EmergencyStateMachine(initial = EmergencySnapshot(state = state))

    private fun reporterFor(engine: EmergencyStateMachine) = Unified911FlowReporter { engine.updateUnified911Flow(it) }

    private fun assertOtherSubsystemsUntouched(engine: EmergencyStateMachine) {
        assertEquals(LocationFlowState.Pending, engine.snapshot.subsystems.location)
        assertEquals(EmergencyServiceFlowState.Pending, engine.snapshot.subsystems.emergencyService)
        assertEquals(FamilyAlertFlowState.Pending, engine.snapshot.subsystems.familyAlert)
        assertEquals(EmergencyCompanionState.Pending, engine.snapshot.subsystems.companion)
    }

    @Test
    fun `dial firing successfully reaches Succeeded`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val coordinator = Unified911FlowCoordinator(
            dialAction = FakeDialAction(Result.success(Unit)),
            reporter = reporterFor(engine),
        )

        val result = coordinator.dial()

        assertTrue(result.isSuccess)
        assertEquals(Unified911FlowState.Succeeded, engine.snapshot.subsystems.unified911)
        assertOtherSubsystemsUntouched(engine)
    }

    @Test
    fun `dial failing reaches CallFailed`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val coordinator = Unified911FlowCoordinator(
            dialAction = FakeDialAction(Result.failure(IllegalStateException("no dialer available"))),
            reporter = reporterFor(engine),
        )

        val result = coordinator.dial()

        assertTrue(result.isSuccess) // reporting the failure state is itself a successful report
        assertEquals(Unified911FlowState.CallFailed, engine.snapshot.subsystems.unified911)
        assertOtherSubsystemsUntouched(engine)
    }

    @Test
    fun `reports InProgress before resolving to a terminal state`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val observedStates = mutableListOf<Unified911FlowState>()
        val coordinator = Unified911FlowCoordinator(
            dialAction = FakeDialAction(Result.success(Unit)),
            reporter = Unified911FlowReporter { state ->
                observedStates += state
                engine.updateUnified911Flow(state)
            },
        )

        coordinator.dial()

        assertEquals(listOf(Unified911FlowState.InProgress, Unified911FlowState.Succeeded), observedStates)
    }

    @Test
    fun `retrying after CallFailed by calling dial again can succeed`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        var shouldFail = true
        val dialAction = object : Unified911DialAction {
            override fun dial(): Result<Unit> =
                if (shouldFail) Result.failure(IllegalStateException("first attempt fails")) else Result.success(Unit)
        }
        val coordinator = Unified911FlowCoordinator(dialAction = dialAction, reporter = reporterFor(engine))

        val first = coordinator.dial()
        assertTrue(first.isSuccess)
        assertEquals(Unified911FlowState.CallFailed, engine.snapshot.subsystems.unified911)

        shouldFail = false
        val second = coordinator.dial()
        assertTrue(second.isSuccess)
        assertEquals(Unified911FlowState.Succeeded, engine.snapshot.subsystems.unified911)
    }

    @Test
    fun `retrying calls the dial action again each time`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val dialAction = FakeDialAction(Result.failure(IllegalStateException("always fails")))
        val coordinator = Unified911FlowCoordinator(dialAction = dialAction, reporter = reporterFor(engine))

        coordinator.dial()
        coordinator.dial()
        coordinator.dial()

        assertEquals(3, dialAction.callCount)
        assertEquals(Unified911FlowState.CallFailed, engine.snapshot.subsystems.unified911)
    }
}
