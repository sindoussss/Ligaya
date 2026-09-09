package com.ligaya.core.telephony

/**
 * Abstracts firing the Unified 911 dial action so Unified911FlowCoordinator's fired-vs-failed
 * branching is unit-testable without launching any real Activity. IntentUnified911DialAction is
 * the real implementation.
 */
fun interface Unified911DialAction {
    fun dial(): Result<Unit>
}
