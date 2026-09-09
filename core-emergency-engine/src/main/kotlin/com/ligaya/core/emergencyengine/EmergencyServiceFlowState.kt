package com.ligaya.core.emergencyengine

/**
 * One of section 25's five concurrent subsystems (section 16). LOOKUP_FAILED is named
 * explicitly, transitioning back to EMERGENCY_ACTIVE with "subsystem failure recorded" — per
 * section 21, this never blocks the 911 flow, which remains available regardless.
 */
sealed interface EmergencyServiceFlowState {
    data object Pending : EmergencyServiceFlowState
    data object InProgress : EmergencyServiceFlowState
    data object Succeeded : EmergencyServiceFlowState
    data object LookupFailed : EmergencyServiceFlowState
}
