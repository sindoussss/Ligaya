package com.ligaya.core.billing

import android.app.Activity
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore

/**
 * §2's rule made concrete: "RevenueCat confirms entitlement; the backend decides what that
 * entitlement unlocks" — this interface only ever answers "is this device's RevenueCat customer
 * currently entitled," the same question backend/firestore.rules' read-only SUBSCRIPTION
 * document already enforces server-side (Step 2). No client, this one included, can self-grant
 * entitlement; a failed or unconfigured SDK degrades to "not subscribed," never the reverse.
 *
 * [RevenueCatEntitlementRepository] wraps the real RevenueCat Android SDK
 * (com.revenuecat.purchases:purchases, verified against RevenueCat's own current documentation:
 * `Purchases.configure`/`awaitCustomerInfo`/`awaitOfferings`/`awaitPurchase`/`awaitRestore` are
 * real, current API). It compiles and its fallback paths are tested, but a real purchase or
 * entitlement check needs `Purchases.configure()` to have already run with a real RevenueCat API
 * key — which needs a real RevenueCat project (and, per the architecture doc's own Issue note, "a
 * real Play Console listing even for testing"). Nothing in this codebase calls `Purchases.configure`
 * yet; see ACCOUNT_ACTIONS_NEEDED.md.
 */
interface EntitlementRepository {
    suspend fun isSubscribed(): Boolean
    suspend fun purchase(activity: Activity): PurchaseOutcome
    suspend fun restorePurchases(): PurchaseOutcome
}

sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data class Failure(val message: String) : PurchaseOutcome
}

/**
 * [entitlementId] is the RevenueCat dashboard's entitlement identifier (e.g. "ligaya_plus") —
 * itself a value that only exists once a real RevenueCat project is configured, so it's a plain
 * constructor parameter rather than a hardcoded guess.
 *
 * Every method below is defensive about the SDK not being configured (`Purchases.sharedInstance`
 * throws `UninitializedPropertyAccessException` before `Purchases.configure()` has ever run) —
 * this class never crashes the caller for that reason, it reports the same "not subscribed" /
 * Failure outcome a real, correctly-configured SDK call that legitimately failed would.
 */
class RevenueCatEntitlementRepository(
    private val entitlementId: String,
) : EntitlementRepository {

    override suspend fun isSubscribed(): Boolean = runCatching {
        Purchases.sharedInstance.awaitCustomerInfo().entitlements[entitlementId]?.isActive == true
    }.getOrDefault(false)

    override suspend fun purchase(activity: Activity): PurchaseOutcome = runCatching {
        val offerings = Purchases.sharedInstance.awaitOfferings()
        val packageToPurchase = offerings.current?.availablePackages?.firstOrNull()
            ?: return PurchaseOutcome.Failure("No Ligaya+ package is currently available.")
        Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, packageToPurchase).build())
        PurchaseOutcome.Success
    }.getOrElse { e -> PurchaseOutcome.Failure(purchaseFailureMessage(e)) }

    override suspend fun restorePurchases(): PurchaseOutcome = runCatching {
        Purchases.sharedInstance.awaitRestore()
        PurchaseOutcome.Success
    }.getOrElse { e -> PurchaseOutcome.Failure(purchaseFailureMessage(e)) }

    private fun purchaseFailureMessage(e: Throwable): String =
        if (e is PurchasesException) e.error.message else (e.message ?: "Something went wrong. Please try again.")
}
