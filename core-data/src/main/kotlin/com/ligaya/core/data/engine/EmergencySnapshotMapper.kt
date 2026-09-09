package com.ligaya.core.data.engine

import com.ligaya.core.data.entity.EmergencyStateSnapshotEntity
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState

/**
 * Explicit, exhaustive (compiler-checked `when`, no `else` branch) mapping between
 * core-emergency-engine's pure Kotlin state types and the plain-string columns of
 * EmergencyStateSnapshotEntity. Deliberately hand-written rather than reflection-based
 * (kotlinx.serialization) so core-emergency-engine never needs a serialization dependency, and
 * so a renamed/added state is a compile error here, not a silent runtime mismatch.
 */
fun EmergencySnapshot.toEntity(emergencyEventId: String?, updatedAtEpochMillis: Long): EmergencyStateSnapshotEntity =
    EmergencyStateSnapshotEntity(
        emergencyEventId = emergencyEventId,
        mainState = state.name,
        locationFlowState = subsystems.location.toColumnValue(),
        unified911FlowState = subsystems.unified911.toColumnValue(),
        emergencyServiceFlowState = subsystems.emergencyService.toColumnValue(),
        familyAlertFlowState = subsystems.familyAlert.toColumnValue(),
        companionState = subsystems.companion.toColumnValue(),
        lastUpdatedAtEpochMillis = updatedAtEpochMillis,
    )

fun EmergencyStateSnapshotEntity.toSnapshot(): EmergencySnapshot = EmergencySnapshot(
    state = EmergencyState.valueOf(mainState),
    subsystems = ConcurrentSubsystemStates(
        location = locationFlowState.toLocationFlowState(),
        unified911 = unified911FlowState.toUnified911FlowState(),
        emergencyService = emergencyServiceFlowState.toEmergencyServiceFlowState(),
        familyAlert = familyAlertFlowState.toFamilyAlertFlowState(),
        companion = companionState.toEmergencyCompanionState(),
    ),
)

private fun LocationFlowState.toColumnValue(): String = when (this) {
    LocationFlowState.Pending -> "PENDING"
    LocationFlowState.InProgress -> "IN_PROGRESS"
    LocationFlowState.Succeeded -> "SUCCEEDED"
    LocationFlowState.Unavailable -> "UNAVAILABLE"
}

private fun String.toLocationFlowState(): LocationFlowState = when (this) {
    "PENDING" -> LocationFlowState.Pending
    "IN_PROGRESS" -> LocationFlowState.InProgress
    "SUCCEEDED" -> LocationFlowState.Succeeded
    "UNAVAILABLE" -> LocationFlowState.Unavailable
    else -> throw IllegalArgumentException("Unknown LocationFlowState column value: $this")
}

private fun Unified911FlowState.toColumnValue(): String = when (this) {
    Unified911FlowState.Pending -> "PENDING"
    Unified911FlowState.InProgress -> "IN_PROGRESS"
    Unified911FlowState.Succeeded -> "SUCCEEDED"
    Unified911FlowState.CallFailed -> "CALL_FAILED"
}

private fun String.toUnified911FlowState(): Unified911FlowState = when (this) {
    "PENDING" -> Unified911FlowState.Pending
    "IN_PROGRESS" -> Unified911FlowState.InProgress
    "SUCCEEDED" -> Unified911FlowState.Succeeded
    "CALL_FAILED" -> Unified911FlowState.CallFailed
    else -> throw IllegalArgumentException("Unknown Unified911FlowState column value: $this")
}

private fun EmergencyServiceFlowState.toColumnValue(): String = when (this) {
    EmergencyServiceFlowState.Pending -> "PENDING"
    EmergencyServiceFlowState.InProgress -> "IN_PROGRESS"
    EmergencyServiceFlowState.Succeeded -> "SUCCEEDED"
    EmergencyServiceFlowState.LookupFailed -> "LOOKUP_FAILED"
}

private fun String.toEmergencyServiceFlowState(): EmergencyServiceFlowState = when (this) {
    "PENDING" -> EmergencyServiceFlowState.Pending
    "IN_PROGRESS" -> EmergencyServiceFlowState.InProgress
    "SUCCEEDED" -> EmergencyServiceFlowState.Succeeded
    "LOOKUP_FAILED" -> EmergencyServiceFlowState.LookupFailed
    else -> throw IllegalArgumentException("Unknown EmergencyServiceFlowState column value: $this")
}

private fun FamilyAlertFlowState.toColumnValue(): String = when (this) {
    FamilyAlertFlowState.Pending -> "PENDING"
    FamilyAlertFlowState.InProgress -> "IN_PROGRESS"
    FamilyAlertFlowState.Succeeded -> "SUCCEEDED"
    FamilyAlertFlowState.DeliveryFailed -> "DELIVERY_FAILED"
}

private fun String.toFamilyAlertFlowState(): FamilyAlertFlowState = when (this) {
    "PENDING" -> FamilyAlertFlowState.Pending
    "IN_PROGRESS" -> FamilyAlertFlowState.InProgress
    "SUCCEEDED" -> FamilyAlertFlowState.Succeeded
    "DELIVERY_FAILED" -> FamilyAlertFlowState.DeliveryFailed
    else -> throw IllegalArgumentException("Unknown FamilyAlertFlowState column value: $this")
}

private fun EmergencyCompanionState.toColumnValue(): String = when (this) {
    EmergencyCompanionState.Pending -> "PENDING"
    EmergencyCompanionState.Active -> "ACTIVE"
}

private fun String.toEmergencyCompanionState(): EmergencyCompanionState = when (this) {
    "PENDING" -> EmergencyCompanionState.Pending
    "ACTIVE" -> EmergencyCompanionState.Active
    else -> throw IllegalArgumentException("Unknown EmergencyCompanionState column value: $this")
}
