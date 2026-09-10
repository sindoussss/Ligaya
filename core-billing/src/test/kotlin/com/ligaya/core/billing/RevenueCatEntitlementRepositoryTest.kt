package com.ligaya.core.billing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2's rule ("a failed or unconfigured SDK degrades to 'not subscribed,' never the reverse" —
 * see EntitlementRepository's own doc comment) proven directly: nothing in this codebase ever
 * calls `Purchases.configure()` yet (no real RevenueCat project exists — see
 * ACCOUNT_ACTIONS_NEEDED.md), so `Purchases.sharedInstance` throws
 * `UninitializedPropertyAccessException` on every real call these tests make. The point of this
 * test is exactly that: proving [RevenueCatEntitlementRepository] absorbs that and reports a safe
 * default instead of crashing its caller.
 */
class RevenueCatEntitlementRepositoryTest {

    private val repository = RevenueCatEntitlementRepository(entitlementId = "ligaya_plus")

    @Test
    fun `isSubscribed reports false when the SDK was never configured, rather than throwing`() = runTest {
        assertFalse(repository.isSubscribed())
    }

    @Test
    fun `restorePurchases reports Failure when the SDK was never configured, rather than throwing`() = runTest {
        val result = repository.restorePurchases()
        assertTrue("expected Failure, got $result", result is PurchaseOutcome.Failure)
    }
}
