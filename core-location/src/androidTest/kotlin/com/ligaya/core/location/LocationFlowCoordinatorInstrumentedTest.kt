package com.ligaya.core.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.EmergencyStateMachine
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.permissions.AndroidPermissionChecker
import com.ligaya.core.permissions.PermissionRequestHistory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves this step's acceptance criteria end-to-end against the real Android permission-checking
 * stack (core-permissions' AndroidPermissionChecker over the real Context), not just a fake
 * PermissionChecker as in LocationFlowCoordinatorTest.
 *
 * A freshly installed test APK has never had ACCESS_FINE_LOCATION granted or requested — the
 * same natural "not granted" starting point AndroidPermissionCheckerTest relies on — so this
 * exercises the denied path without ever calling grantRuntimePermission itself.
 *
 * That natural starting point can be consumed by another test in the same instrumented run: Step
 * 30's OfflineDegradationTest and FusedLocationSourceDiagnosticTest both need a real OS-level
 * grant of ACCESS_FINE_LOCATION for their own duration, and — found directly, the hard way —
 * revoking a dangerous permission from a still-running process is enforced by killing that
 * process outright, so they deliberately never revoke it afterward. When one of those has already
 * run first in this instrumentation session, the permission is genuinely, permanently granted for
 * the rest of the run, and this test has no real "not granted" case left to exercise — so it
 * skips itself via assumeTrue rather than asserting a precondition the environment can no longer
 * satisfy.
 */
@RunWith(AndroidJUnit4::class)
class LocationFlowCoordinatorInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val neverRequestedHistory = object : PermissionRequestHistory {
        override fun hasRequestedBefore(permission: String) = false
        override fun markRequested(permission: String) {}
    }

    @Test
    fun withRealPermissionCheckingAndLocationPermissionNotGrantedEngineReachesUnavailable() = runTest {
        assumeTrue(
            "ACCESS_FINE_LOCATION was already granted by another test earlier in this " +
                "instrumentation run, and can't be safely revoked mid-run — skipping rather than " +
                "asserting a 'not granted' precondition that no longer holds.",
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_DENIED,
        )

        val engine = EmergencyStateMachine(initial = EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE))
        val permissionChecker = AndroidPermissionChecker(context, activity = null, history = neverRequestedHistory)
        val coordinator = LocationFlowCoordinator(
            locationSource = object : LocationSource {
                override suspend fun getCurrentLocation(): GeoCoordinates = error("must not be called: permission is not granted")
                override suspend fun getLastKnownLocation(): GeoCoordinates = error("must not be called: permission is not granted")
            },
            permissionChecker = permissionChecker,
            reporter = LocationFlowReporter { engine.updateLocationFlow(it) },
        )

        val result = coordinator.run()

        assertTrue(result.isSuccess)
        assertEquals(LocationFlowState.Unavailable, engine.snapshot.subsystems.location)
    }
}
