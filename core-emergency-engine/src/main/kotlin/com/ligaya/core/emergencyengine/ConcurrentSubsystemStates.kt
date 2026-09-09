package com.ligaya.core.emergencyengine

/**
 * The five independent subsystems section 13 fans out into once EMERGENCY_ACTIVE is entered —
 * "None of them blocks another, and none of them is a prerequisite for another." Each field
 * defaults to Pending, the state before that subsystem's owning step (15-19, 26) has been
 * implemented and starts reporting real updates.
 */
data class ConcurrentSubsystemStates(
    val location: LocationFlowState = LocationFlowState.Pending,
    val unified911: Unified911FlowState = Unified911FlowState.Pending,
    val emergencyService: EmergencyServiceFlowState = EmergencyServiceFlowState.Pending,
    val familyAlert: FamilyAlertFlowState = FamilyAlertFlowState.Pending,
    val companion: EmergencyCompanionState = EmergencyCompanionState.Pending,
)
