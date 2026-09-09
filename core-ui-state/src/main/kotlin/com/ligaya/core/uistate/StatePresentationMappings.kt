package com.ligaya.core.uistate

import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState

/**
 * Step 35's mapping layer: every engine/domain state defined through Step 28 (core-emergency-
 * engine's EmergencyState plus all five of ConcurrentSubsystemStates' fields) to a
 * [StatePresentation]. One `when` per type, each exhaustive over a sealed type or enum — the
 * compiler itself enforces this step's "100% of engine states" acceptance criterion for any type
 * added or extended after this file was written, not just at the moment it was written.
 *
 * core-ui-state "keeps safety logic out of the UI layer" (this step's own purpose): every mapping
 * below is pure text/tone, never a decision that changes engine behavior — this file only
 * describes how an already-decided state should look.
 */

fun EmergencyState.toPresentation(): StatePresentation = when (this) {
    EmergencyState.IDLE -> StatePresentation("Idle", PresentationTone.NEUTRAL)
    EmergencyState.EMERGENCY_DETECTED -> StatePresentation("Emergency detected", PresentationTone.EMERGENCY)
    EmergencyState.EMERGENCY_CONFIRMED -> StatePresentation("Emergency confirmed", PresentationTone.EMERGENCY)
    EmergencyState.EMERGENCY_ACTIVE -> StatePresentation("Emergency active", PresentationTone.EMERGENCY)
    EmergencyState.USER_MARKED_SAFE -> StatePresentation("Marked safe", PresentationTone.SUCCESS)
    EmergencyState.EMERGENCY_RESOLVED -> StatePresentation("Resolved", PresentationTone.SUCCESS)
    EmergencyState.CLOSED -> StatePresentation("Closed", PresentationTone.NEUTRAL)
}

/** "LocationState" in the roadmap's own naming. */
fun LocationFlowState.toPresentation(): StatePresentation = when (this) {
    LocationFlowState.Pending -> StatePresentation("Waiting to acquire location", PresentationTone.PENDING)
    LocationFlowState.InProgress -> StatePresentation("Acquiring location…", PresentationTone.IN_PROGRESS)
    LocationFlowState.Succeeded -> StatePresentation("Location acquired", PresentationTone.SUCCESS)
    LocationFlowState.Unavailable -> StatePresentation("Location unavailable", PresentationTone.FAILURE)
}

/** "CallState" in the roadmap's own naming — the Philippines Unified 911 flow (Step 16). */
fun Unified911FlowState.toPresentation(): StatePresentation = when (this) {
    Unified911FlowState.Pending -> StatePresentation("Waiting to call 911", PresentationTone.PENDING)
    Unified911FlowState.InProgress -> StatePresentation("Dialing 911…", PresentationTone.IN_PROGRESS)
    Unified911FlowState.Succeeded -> StatePresentation("Connected to 911", PresentationTone.SUCCESS)
    Unified911FlowState.CallFailed -> StatePresentation("911 call failed", PresentationTone.FAILURE)
}

/** Nearby emergency-service lookup (Step 17) — not individually named in this step's own "What's
 *  implemented" bullet, but still one of the engine states the acceptance criterion covers. */
fun EmergencyServiceFlowState.toPresentation(): StatePresentation = when (this) {
    EmergencyServiceFlowState.Pending -> StatePresentation("Waiting to look up nearby services", PresentationTone.PENDING)
    EmergencyServiceFlowState.InProgress -> StatePresentation("Looking up nearby services…", PresentationTone.IN_PROGRESS)
    EmergencyServiceFlowState.Succeeded -> StatePresentation("Nearby service found", PresentationTone.SUCCESS)
    EmergencyServiceFlowState.LookupFailed -> StatePresentation("Lookup failed", PresentationTone.FAILURE)
}

/** "NotificationState" in the roadmap's own naming — the Safety Circle family-alert flow. */
fun FamilyAlertFlowState.toPresentation(): StatePresentation = when (this) {
    FamilyAlertFlowState.Pending -> StatePresentation("Waiting to alert Safety Circle", PresentationTone.PENDING)
    FamilyAlertFlowState.InProgress -> StatePresentation("Alerting Safety Circle…", PresentationTone.IN_PROGRESS)
    FamilyAlertFlowState.Succeeded -> StatePresentation("Safety Circle alerted", PresentationTone.SUCCESS)
    FamilyAlertFlowState.DeliveryFailed -> StatePresentation("Alert delivery failed", PresentationTone.FAILURE)
}

/** "VoicePipelineState" in the roadmap's own naming — the Emergency Companion subsystem. No
 *  named failure sub-state exists at the engine level (see EmergencyCompanionState's own doc
 *  comment), so there is no FAILURE case to map here — that isn't a gap, it's this type's whole
 *  shape. */
fun EmergencyCompanionState.toPresentation(): StatePresentation = when (this) {
    EmergencyCompanionState.Pending -> StatePresentation("Companion not yet engaged", PresentationTone.PENDING)
    EmergencyCompanionState.Active -> StatePresentation("Companion active", PresentationTone.IN_PROGRESS)
}
