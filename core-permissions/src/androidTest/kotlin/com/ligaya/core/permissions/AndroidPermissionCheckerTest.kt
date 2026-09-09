package com.ligaya.core.permissions

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the real ContextCompat/ActivityCompat wiring feeds PermissionStateResolver correctly.
 *
 * Deliberately never calls UiAutomation.revokeRuntimePermission: this is a self-instrumenting
 * test (the JUnit code runs inside the same process/package under test), and revoking an
 * already-granted permission from a currently-running process makes Android's ActivityManager
 * force-kill that process outright ("permissions revoked") — confirmed via logcat
 * (`Killing ...com.ligaya.core.permissions.test... permissions revoked`) after this test
 * originally called revoke and crashed the whole instrumentation run. A freshly installed test
 * APK starts with POST_NOTIFICATIONS NOT granted by default, so the not-granted states are
 * exercised from that natural starting point instead, with grantRuntimePermission (safe; only
 * revoking a live process's own permission is fatal) used once, last, for the Granted case.
 *
 * The true PermanentlyDenied path (needing a real Activity + a real prior dialog interaction)
 * isn't exercised here either — PermissionStateResolverTest covers that branch deterministically
 * as a pure unit test instead.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPermissionCheckerTest {

    private val permission = "android.permission.POST_NOTIFICATIONS"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName

    private fun historyOf(hasRequestedBefore: Boolean) = object : PermissionRequestHistory {
        override fun hasRequestedBefore(permission: String) = hasRequestedBefore
        override fun markRequested(permission: String) {}
    }

    @Test
    fun observesNotRequestedThenDeniedThenGrantedInSequence() {
        // A freshly installed test APK has never had this permission granted or requested.
        val neverRequested = AndroidPermissionChecker(context, activity = null, history = historyOf(false))
        assertEquals(PermissionState.NotRequested, neverRequested.currentState(permission))

        // Still not granted, but our own history now says we asked before, and no Activity is
        // available to check shouldShowRequestPermissionRationale -> conservatively Denied.
        val previouslyRequested = AndroidPermissionChecker(context, activity = null, history = historyOf(true))
        assertEquals(PermissionState.Denied, previouslyRequested.currentState(permission))

        // Granting is safe (only revoking a live process's own permission kills it).
        instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        val nowGranted = AndroidPermissionChecker(context, activity = null, history = historyOf(true))
        assertEquals(PermissionState.Granted, nowGranted.currentState(permission))
    }
}
