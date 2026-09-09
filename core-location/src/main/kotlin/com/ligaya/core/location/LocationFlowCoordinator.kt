package com.ligaya.core.location

import android.Manifest
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives section 14's location flow as an independent unit that never blocks any other
 * subsystem: request current location, fall back to last-known, else report unavailable. A
 * location failure only ever updates its own field on the shared snapshot (see
 * EmergencyStateMachine.updateLocationFlow, Step 9) — it can never fail activateEmergency or any
 * other subsystem's own transitions.
 *
 * "Never invent coordinates" (section 14): Unavailable is only reported when permission is
 * missing or neither the location provider nor its last-known fallback produced a real fix —
 * this class never fabricates one.
 */
class LocationFlowCoordinator(
    private val locationSource: LocationSource,
    private val permissionChecker: PermissionChecker,
    private val reporter: LocationFlowReporter,
) {
    suspend fun run(): Result<EmergencySnapshot> {
        reporter.reportLocationFlow(LocationFlowState.InProgress)

        val hasPermission = permissionChecker.currentState(
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PermissionState.Granted
        if (!hasPermission) {
            return reporter.reportLocationFlow(LocationFlowState.Unavailable)
        }

        // This coordinator, not FusedLocationSource, is what owns section 14/22's "never a silent
        // hang" guarantee — so it enforces one overall deadline across both calls itself, rather
        // than trusting that two independently-tuned leaf-level timeouts will always sum to
        // something reasonable. (They didn't: found via Step 30's OfflineDegradationTest, which
        // kept failing even after both LocationSource methods got their own timeout guards,
        // because their worst cases stack sequentially here.) A blown-out combined wait is also
        // just bad UX for an emergency flow regardless of what any test's own bound is.
        val fix = withTimeoutOrNull(OVERALL_LOCATION_FLOW_TIMEOUT_MILLIS) {
            locationSource.getCurrentLocation() ?: locationSource.getLastKnownLocation()
        }
        return reporter.reportLocationFlow(
            if (fix != null) LocationFlowState.Succeeded else LocationFlowState.Unavailable,
        )
    }

    companion object {
        private const val OVERALL_LOCATION_FLOW_TIMEOUT_MILLIS = 12_000L
    }
}
