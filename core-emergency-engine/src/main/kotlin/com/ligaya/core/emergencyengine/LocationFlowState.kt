package com.ligaya.core.emergencyengine

/**
 * One of section 25's five concurrent subsystems entered on EMERGENCY_ACTIVE (section 14).
 * No named failure sub-state in the architecture diagram — "unavailable" is a valid end state
 * (last-known-location fallback exhausted), not a failure needing retry.
 */
sealed interface LocationFlowState {
    data object Pending : LocationFlowState
    data object InProgress : LocationFlowState
    data object Succeeded : LocationFlowState
    data object Unavailable : LocationFlowState
}
