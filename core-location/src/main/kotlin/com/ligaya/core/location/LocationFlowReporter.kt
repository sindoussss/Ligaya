package com.ligaya.core.location

import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.LocationFlowState

/**
 * Abstracts "update the running emergency engine's location subsystem state" so
 * LocationFlowCoordinator never needs to know whether it's talking to a bare
 * EmergencyStateMachine (unit tests, Step 9) or the persistence-backed wrapper the real app uses
 * (core-data's PersistedEmergencyStateMachine, Step 10). core-location intentionally has no
 * dependency on core-data — core-permissions' own build script documents the same rule: feature
 * modules depend on shared infrastructure, never on each other. Whichever step wires this
 * coordinator into a live episode supplies the real reporter, e.g.
 * `LocationFlowReporter(persistedMachine::updateLocationFlow)`.
 */
fun interface LocationFlowReporter {
    suspend fun reportLocationFlow(state: LocationFlowState): Result<EmergencySnapshot>
}
