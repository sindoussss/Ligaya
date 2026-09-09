package com.ligaya.core.telephony

import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.Unified911FlowState

/**
 * Abstracts "update the running emergency engine's Unified 911 subsystem state" so
 * Unified911FlowCoordinator never needs to know whether it's talking to a bare
 * EmergencyStateMachine (unit tests, Step 9) or the persistence-backed wrapper the real app uses
 * (core-data's PersistedEmergencyStateMachine, Step 10) — same rationale as core-location's
 * LocationFlowReporter (Step 15): core-telephony has no dependency on core-data. Whichever step
 * wires this coordinator into a live episode supplies the real reporter, e.g.
 * `Unified911FlowReporter(persistedMachine::updateUnified911Flow)`.
 */
fun interface Unified911FlowReporter {
    suspend fun reportUnified911Flow(state: Unified911FlowState): Result<EmergencySnapshot>
}
