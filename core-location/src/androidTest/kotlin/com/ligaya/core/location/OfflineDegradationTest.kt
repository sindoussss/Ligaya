package com.ligaya.core.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.LocationServices
import com.ligaya.core.permissions.AndroidPermissionChecker
import com.ligaya.core.permissions.PermissionRequestHistory
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 30's own acceptance criteria for this module's row of section 22's table: "Device
 * location where available" must function offline, and — the part a purely logical unit test
 * can't prove — must never silently hang when no fix is available, which is exactly the
 * condition an airplane-mode emulator run with no location provider configured actually
 * exercises. The `withTimeout` here isn't a tolerance for slowness; it's the test itself failing
 * loudly instead of hanging forever if VoiceCaptureCoordinator's underlying
 * FusedLocationProviderClient call turns out to have no built-in deadline.
 */
@RunWith(AndroidJUnit4::class)
class OfflineDegradationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val uiAutomation = instrumentation.uiAutomation

    private fun setAirplaneMode(enabled: Boolean) {
        uiAutomation.executeShellCommand("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}").close()
    }

    @After
    fun tearDown() {
        setAirplaneMode(enabled = false)
        // Deliberately NOT calling revokeRuntimePermission here: revoking a dangerous permission
        // from a process that's still running is enforced by killing that process immediately
        // (confirmed directly — it took down the whole instrumentation run, aborting every test
        // after this one). grantRuntimePermission's grant is therefore left in place for the rest
        // of this instrumented run; LocationFlowCoordinatorInstrumentedTest accounts for that by
        // skipping itself rather than assuming ACCESS_FINE_LOCATION always starts ungranted.
    }

    @Test
    fun locationCoordinatorNeverHangsUnderAirplaneModeEvenWhenPermissionIsGranted() = runTest {
        uiAutomation.grantRuntimePermission(context.packageName, "android.permission.ACCESS_FINE_LOCATION")
        setAirplaneMode(enabled = true)

        val permissionChecker = AndroidPermissionChecker(
            context,
            activity = null,
            history = object : PermissionRequestHistory {
                override fun hasRequestedBefore(permission: String) = true
                override fun markRequested(permission: String) {}
            },
        )
        val coordinator = LocationFlowCoordinator(
            locationSource = FusedLocationSource(LocationServices.getFusedLocationProviderClient(context)),
            permissionChecker = permissionChecker,
            reporter = LocationFlowReporter { Result.success(com.ligaya.core.emergencyengine.EmergencySnapshot()) },
        )

        val result = try {
            withTimeout(20_000) { coordinator.run() }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(
                "LocationFlowCoordinator.run() hung for 20s under airplane mode with no GPS " +
                    "fix available — it must resolve to Unavailable, not hang indefinitely.",
                e,
            )
        }

        assertTrue("expected a completed Result, got $result", result.isSuccess)
    }
}
