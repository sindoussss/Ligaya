package com.ligaya.core.emergencyengine

/**
 * One of section 25's five concurrent subsystems (section 15). CALL_FAILED is named explicitly
 * in the architecture diagram, with a retry-or-alternate-action loop back into this same flow —
 * modeled here as a state a later step's UI/logic can observe and react to by calling
 * EmergencyStateMachine.updateUnified911Flow again, not as an automatic retry this class
 * performs itself (the engine is deterministic state-tracking, not an action executor).
 */
sealed interface Unified911FlowState {
    data object Pending : Unified911FlowState
    data object InProgress : Unified911FlowState
    data object Succeeded : Unified911FlowState
    data object CallFailed : Unified911FlowState
}
