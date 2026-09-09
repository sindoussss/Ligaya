package com.ligaya.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

private class FakePermissionRequestHistory : PermissionRequestHistory {
    private val requested = mutableSetOf<String>()
    override fun hasRequestedBefore(permission: String): Boolean = permission in requested
    override fun markRequested(permission: String) { requested += permission }
}

/** Covers this step's acceptance criteria: a test permission can be requested, denied, and
 *  permanently denied, with each state distinctly observable — deterministically, since every
 *  input (grant status, request history, rationale-showable) is under this test's control. */
class PermissionStateResolverTest {

    private val permission = "android.permission.POST_NOTIFICATIONS"

    @Test
    fun `granted permission resolves to Granted regardless of history`() {
        val resolver = PermissionStateResolver(
            history = FakePermissionRequestHistory(),
            isGranted = { true },
            shouldShowRationale = { error("must not be consulted once granted") },
        )

        assertEquals(PermissionState.Granted, resolver.resolve(permission))
    }

    @Test
    fun `never-requested, not-granted permission resolves to NotRequested`() {
        val resolver = PermissionStateResolver(
            history = FakePermissionRequestHistory(),
            isGranted = { false },
            shouldShowRationale = { error("must not be consulted before any request") },
        )

        assertEquals(PermissionState.NotRequested, resolver.resolve(permission))
    }

    @Test
    fun `requested-before, not-granted, rationale-showable resolves to Denied`() {
        val history = FakePermissionRequestHistory().apply { markRequested(permission) }
        val resolver = PermissionStateResolver(
            history = history,
            isGranted = { false },
            shouldShowRationale = { true },
        )

        assertEquals(PermissionState.Denied, resolver.resolve(permission))
    }

    @Test
    fun `requested-before, not-granted, rationale-NOT-showable resolves to PermanentlyDenied`() {
        val history = FakePermissionRequestHistory().apply { markRequested(permission) }
        val resolver = PermissionStateResolver(
            history = history,
            isGranted = { false },
            shouldShowRationale = { false },
        )

        assertEquals(PermissionState.PermanentlyDenied, resolver.resolve(permission))
    }

    @Test
    fun `denied then permanently denied then granted reflects each transition in order`() {
        val history = FakePermissionRequestHistory()
        var granted = false
        var rationaleShowable = true
        val resolver = PermissionStateResolver(
            history = history,
            isGranted = { granted },
            shouldShowRationale = { rationaleShowable },
        )

        assertEquals(PermissionState.NotRequested, resolver.resolve(permission))

        history.markRequested(permission)
        assertEquals(PermissionState.Denied, resolver.resolve(permission))

        rationaleShowable = false
        assertEquals(PermissionState.PermanentlyDenied, resolver.resolve(permission))

        granted = true
        assertEquals(PermissionState.Granted, resolver.resolve(permission))
    }
}
