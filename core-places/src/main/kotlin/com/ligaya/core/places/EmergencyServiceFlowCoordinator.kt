package com.ligaya.core.places

import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.IncidentType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Drives section 16's nearest-relevant-emergency-service flow: map the incident type to a Places
 * category (or apply Issue I's fallback), search nearby, rank candidates by actual distance
 * (never trusting API result order), then look up contact details for the nearest one.
 *
 * "Succeeded" is reported once a suitable place's details were actually retrieved, regardless of
 * whether it has a public phone number — a place with no listed number is section 16's own valid
 * "do not invent a number, 911 remains available" outcome, not a lookup failure. LookupFailed
 * covers: no Places category applies to this incident type (Issue I), the search itself
 * produced no usable candidate, or the details lookup itself could not be completed. Per section
 * 21/16, none of this ever blocks or is blocked by any other subsystem — [reporter] carries only
 * the bare Pending/InProgress/Succeeded/LookupFailed state (persisted as part of the state
 * machine's own schema), while [resultReporter] separately carries the actual place found, if
 * any: its name, distance and public contact number, none of it fabricated when the Places
 * response omits it.
 */
class EmergencyServiceFlowCoordinator(
    private val nearbySearchSource: NearbySearchSource,
    private val placeDetailsSource: PlaceDetailsSource,
    private val reporter: EmergencyServiceFlowReporter,
    // Defaulted to a no-op so every existing caller — this module's own tests included —
    // compiles unchanged; only a caller that actually wants the result (feature-emergency-active,
    // via :app's composition root) needs to pass one.
    private val resultReporter: EmergencyServiceResultReporter = EmergencyServiceResultReporter {},
) {
    suspend fun run(incidentType: IncidentType, location: GeoCoordinates): Result<EmergencySnapshot> {
        reporter.reportEmergencyServiceFlow(EmergencyServiceFlowState.InProgress)

        val category = IncidentTypeToPlacesCategoryMapper.categoryFor(incidentType)
            ?: return fail()

        val nearest = runCatching { nearbySearchSource.search(location, category) }
            .getOrNull()
            ?.minByOrNull { distanceMeters(location, it.coordinates) }
            ?: return fail()

        val details = runCatching { placeDetailsSource.getDetails(nearest.placeId) }.getOrNull()
            ?: return fail()

        resultReporter.reportEmergencyServiceResult(
            EmergencyServiceLookupResult(
                name = details.name,
                address = details.address,
                phoneNumber = details.phoneNumber,
                distanceMeters = distanceMeters(location, nearest.coordinates),
            ),
        )
        return reporter.reportEmergencyServiceFlow(EmergencyServiceFlowState.Succeeded)
    }

    /** Every failure path reports the same two things: no result to show, and LookupFailed. */
    private suspend fun fail(): Result<EmergencySnapshot> {
        resultReporter.reportEmergencyServiceResult(null)
        return reporter.reportEmergencyServiceFlow(EmergencyServiceFlowState.LookupFailed)
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /** Haversine distance — accurate enough for ranking a handful of nearby candidates. */
        internal fun distanceMeters(a: GeoCoordinates, b: GeoCoordinates): Double {
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val sinDLat = sin(dLat / 2)
            val sinDLon = sin(dLon / 2)
            val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
            val c = 2 * atan2(sqrt(h), sqrt(1 - h))
            return EARTH_RADIUS_METERS * c
        }
    }
}
