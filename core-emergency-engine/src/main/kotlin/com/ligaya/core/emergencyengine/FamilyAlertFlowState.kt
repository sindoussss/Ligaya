package com.ligaya.core.emergencyengine

/**
 * One of section 25's five concurrent subsystems (section 17). DELIVERY_FAILED is named
 * explicitly, and per the diagram can lead either back into FAMILY_ALERT_FLOW (a fallback
 * channel exists — e.g. SMS after push failed) or to EMERGENCY_ACTIVE ("failure recorded", no
 * fallback available). Both outcomes are reachable from DeliveryFailed via the normal
 * updateFamilyAlertFlow API; this class does not decide which applies — that is per-channel
 * logic owned by the family-alert step itself.
 */
sealed interface FamilyAlertFlowState {
    data object Pending : FamilyAlertFlowState
    data object InProgress : FamilyAlertFlowState
    data object Succeeded : FamilyAlertFlowState
    data object DeliveryFailed : FamilyAlertFlowState
}
