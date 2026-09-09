package com.ligaya.core.telephony

import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.Unified911FlowState

/**
 * Drives section 15's Philippines Unified 911 dial flow: fire the dial action, record whether it
 * fired or failed. "Succeeded" here means only "the dialer opened, pre-filled with 911" — never
 * that the call connected or 911 was reached. Per section 15's own explicit rule ("Never
 * automatically claim '911 contacted' unless the implementation provides a real confirmation
 * mechanism") and the roadmap's confirmed ACTION_DIAL approach (Issue B: Android disallows a
 * silent ACTION_CALL to emergency numbers from a third-party app), this class can only ever
 * observe whether the hand-off to the system dialer itself succeeded — never the call's outcome.
 *
 * The diagram draws two forks — "911 action available?" (no app can handle the dial intent at
 * all) and "call action failed" (the hand-off itself failed) — but core-emergency-engine's
 * Unified911FlowState (Step 9) has only one failure state, CallFailed, with no separate
 * Unavailable. Both forks collapse onto CallFailed here: every real target device is a phone,
 * which always ships a dialer, so "unavailable" is an unusual-configuration edge case rather
 * than a distinct outcome worth its own state. Unified911DialAction still checks resolvability
 * before dialing so it never launches a doomed intent (see IntentUnified911DialAction).
 *
 * Retry ("Allow retry" in the diagram) is not a separate method — it is calling dial() again,
 * exactly as the diagram draws it (the failure state loops back into the same flow), and
 * core-emergency-engine already allows re-reporting a subsystem state freely within the
 * open-session window (Step 9/10) — there is nothing here for a dedicated retry method to add.
 */
class Unified911FlowCoordinator(
    private val dialAction: Unified911DialAction,
    private val reporter: Unified911FlowReporter,
) {
    suspend fun dial(): Result<EmergencySnapshot> {
        reporter.reportUnified911Flow(Unified911FlowState.InProgress)
        return reporter.reportUnified911Flow(
            if (dialAction.dial().isSuccess) Unified911FlowState.Succeeded else Unified911FlowState.CallFailed,
        )
    }
}
