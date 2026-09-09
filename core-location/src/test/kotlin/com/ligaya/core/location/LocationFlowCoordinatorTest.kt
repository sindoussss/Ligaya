package com.ligaya.core.location

import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.EmergencyStateMachine
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers this step's acceptance criteria against the real EmergencyStateMachine (Step 9) — not a
 * fake — so a passing test actually proves the engine reaches Unavailable/Succeeded, not just
 * that this class calls a mock correctly.
 */
class LocationFlowCoordinatorTest {

    private class FakePermissionChecker(private val state: PermissionState) : PermissionChecker {
        override fun currentState(permission: String): PermissionState = state
    }

    private class FakeLocationSource(
        private val current: GeoCoordinates?,
        private val lastKnown: GeoCoordinates?,
    ) : LocationSource {
        override suspend fun getCurrentLocation(): GeoCoordinates? = current
        override suspend fun getLastKnownLocation(): GeoCoordinates? = lastKnown
    }

    private fun engineAt(state: EmergencyState) = EmergencyStateMachine(initial = EmergencySnapshot(state = state))

    private fun reporterFor(engine: EmergencyStateMachine) = LocationFlowReporter { engine.updateLocationFlow(it) }

    private fun assertOtherSubsystemsUntouched(engine: EmergencyStateMachine) {
        assertEquals(Unified911FlowState.Pending, engine.snapshot.subsystems.unified911)
        assertEquals(EmergencyServiceFlowState.Pending, engine.snapshot.subsystems.emergencyService)
        assertEquals(FamilyAlertFlowState.Pending, engine.snapshot.subsystems.familyAlert)
        assertEquals(EmergencyCompanionState.Pending, engine.snapshot.subsystems.companion)
    }

    @Test
    fun `permission denied reaches Unavailable without ever consulting the location source`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val coordinator = LocationFlowCoordinator(
            locationSource = object : LocationSource {
                override suspend fun getCurrentLocation(): GeoCoordinates = error("must not be called when permission is denied")
                override suspend fun getLastKnownLocation(): GeoCoordinates = error("must not be called when permission is denied")
            },
            permissionChecker = FakePermissionChecker(PermissionState.Denied),
            reporter = reporterFor(engine),
        )

        val result = coordinator.run()

        assertTrue(result.isSuccess)
        assertEquals(LocationFlowState.Unavailable, engine.snapshot.subsystems.location)
        assertOtherSubsystemsUntouched(engine)
    }

    @Test
    fun `current GPS fix available reaches Succeeded`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val coordinator = LocationFlowCoordinator(
            locationSource = FakeLocationSource(
                current = GeoCoordinates(14.5995, 120.9842),
                lastKnown = null,
            ),
            permissionChecker = FakePermissionChecker(PermissionState.Granted),
            reporter = reporterFor(engine),
        )

        val result = coordinator.run()

        assertTrue(result.isSuccess)
        assertEquals(LocationFlowState.Succeeded, engine.snapshot.subsystems.location)
        assertOtherSubsystemsUntouched(engine)
    }

    @Test
    fun `no current fix but last-known available still reaches Succeeded`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val coordinator = LocationFlowCoordinator(
            locationSource = FakeLocationSource(
                current = null,
                lastKnown = GeoCoordinates(14.5995, 120.9842),
            ),
            permissionChecker = FakePermissionChecker(PermissionState.Granted),
            reporter = reporterFor(engine),
        )

        val result = coordinator.run()

        assertTrue(result.isSuccess)
        assertEquals(LocationFlowState.Succeeded, engine.snapshot.subsystems.location)
        assertOtherSubsystemsUntouched(engine)
    }

    @Test
    fun `neither current nor last-known available reaches Unavailable`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val coordinator = LocationFlowCoordinator(
            locationSource = FakeLocationSource(current = null, lastKnown = null),
            permissionChecker = FakePermissionChecker(PermissionState.Granted),
            reporter = reporterFor(engine),
        )

        val result = coordinator.run()

        assertTrue(result.isSuccess)
        assertEquals(LocationFlowState.Unavailable, engine.snapshot.subsystems.location)
        assertOtherSubsystemsUntouched(engine)
    }

    @Test
    fun `reports InProgress before resolving to a terminal state`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val observedStates = mutableListOf<LocationFlowState>()
        val coordinator = LocationFlowCoordinator(
            locationSource = FakeLocationSource(current = null, lastKnown = null),
            permissionChecker = FakePermissionChecker(PermissionState.Granted),
            reporter = LocationFlowReporter { state ->
                observedStates += state
                engine.updateLocationFlow(state)
            },
        )

        coordinator.run()

        assertEquals(listOf(LocationFlowState.InProgress, LocationFlowState.Unavailable), observedStates)
    }
}
