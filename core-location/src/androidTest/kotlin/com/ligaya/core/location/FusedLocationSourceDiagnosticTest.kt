package com.ligaya.core.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FusedLocationSourceDiagnosticTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val uiAutomation = instrumentation.uiAutomation

    private fun setAirplaneMode(enabled: Boolean) {
        uiAutomation.executeShellCommand("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}").close()
    }

    @After
    fun tearDown() {
        setAirplaneMode(enabled = false)
        // See OfflineDegradationTest's tearDown: revoking here would kill this still-running
        // process instead of just this test.
    }

    @Test
    fun diagnoseGetCurrentLocationDirectly() = runTest {
        uiAutomation.grantRuntimePermission(context.packageName, "android.permission.ACCESS_FINE_LOCATION")
        setAirplaneMode(enabled = true)

        val source = FusedLocationSource(LocationServices.getFusedLocationProviderClient(context))

        try {
            withTimeout(20_000) {
                val result = source.getCurrentLocation()
                println("DIAGNOSTIC: getCurrentLocation returned $result")
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("DIAGNOSTIC: getCurrentLocation() itself hung past 20s", e)
        }
    }
}
