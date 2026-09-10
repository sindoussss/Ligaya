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
 * Step 50's own explicit callout — the roadmap's permission matrix lists "location
 * (fine/coarse/background)" as three states worth testing separately, and this is the "coarse"
 * one: a real ACCESS_COARSE_LOCATION grant with ACCESS_FINE_LOCATION genuinely absent.
 *
 * Proves the *documented, current* behavior, not a redesign: [LocationFlowCoordinator] only ever
 * checks ACCESS_FINE_LOCATION (see its own doc comment/source) — a coarse-only grant is treated
 * the same as no grant at all, reporting Unavailable rather than attempting a lower-precision fix.
 * That's a real, existing simplification this step's own "No new features" scope isn't the place
 * to change; this test's job is only to confirm that simplification degrades safely (Unavailable,
 * never a crash) rather than, say, throwing when FusedLocationProviderClient is asked for a fix
 * with only coarse precision available.
 *
 * Same cross-test contamination risk LocationFlowCoordinatorInstrumentedTest already documents:
 * OfflineDegradationTest/FusedLocationSourceDiagnosticTest both grant ACCESS_FINE_LOCATION and
 * never revoke it (revoking a live process's own dangerous permission kills that process). If one
 * of those already ran first in this same instrumentation process, FINE is already granted here
 * too, and there's no genuine "coarse-only" state left to exercise — so this skips itself via
 * assumeTrue rather than asserting a precondition the environment can no longer satisfy.
 */
@RunWith(AndroidJUnit4::class)
class CoarseOnlyLocationInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val alreadyRequestedHistory = object : PermissionRequestHistory {
        override fun hasRequestedBefore(permission: String) = true
        override fun markRequested(permission: String) {}
    }

    @Test
    fun withOnlyCoarseLocationGrantedEngineReachesUnavailableNotACrash() = runTest {
        assumeTrue(
            "ACCESS_FINE_LOCATION was already granted by another test earlier in this " +
                "instrumentation run, and can't be safely revoked mid-run — skipping rather than " +
                "asserting a coarse-only precondition that no longer holds.",
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_DENIED,
        )

        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        assertEquals(
            "expected FINE to still be genuinely denied — this test only grants COARSE",
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION),
        )

        val engine = EmergencyStateMachine(initial = EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE))
        val permissionChecker = AndroidPermissionChecker(context, activity = null, history = alreadyRequestedHistory)
        val coordinator = LocationFlowCoordinator(
            locationSource = object : LocationSource {
                override suspend fun getCurrentLocation(): GeoCoordinates =
                    error("must not be called: only COARSE is granted, coordinator only checks FINE")

                override suspend fun getLastKnownLocation(): GeoCoordinates =
                    error("must not be called: only COARSE is granted, coordinator only checks FINE")
            },
            permissionChecker = permissionChecker,
            reporter = LocationFlowReporter { engine.updateLocationFlow(it) },
        )

        val result = coordinator.run()

        assertTrue("expected a completed Result, got $result — never a crash", result.isSuccess)
        assertEquals(LocationFlowState.Unavailable, engine.snapshot.subsystems.location)
    }
}
