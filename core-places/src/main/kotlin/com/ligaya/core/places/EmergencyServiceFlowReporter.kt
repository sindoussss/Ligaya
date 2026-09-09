package com.ligaya.core.places

import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState

/**
 * Abstracts "update the running emergency engine's emergency-service subsystem state" so
 * EmergencyServiceFlowCoordinator never needs to know whether it's talking to a bare
 * EmergencyStateMachine (unit tests, Step 9) or the persistence-backed wrapper the real app uses
 * (core-data's PersistedEmergencyStateMachine, Step 10) — same rationale as core-location's
 * LocationFlowReporter (Step 15) and core-telephony's Unified911FlowReporter (Step 16).
 */
fun interface EmergencyServiceFlowReporter {
    suspend fun reportEmergencyServiceFlow(state: EmergencyServiceFlowState): Result<EmergencySnapshot>
}
