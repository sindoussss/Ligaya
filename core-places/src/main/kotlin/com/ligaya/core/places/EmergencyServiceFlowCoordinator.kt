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
 * 21/16, none of this ever blocks or is blocked by any other subsystem — a caller unwraps
 * whether a phone number came back from the reporter's own persisted snapshot in whatever way
 * that later step needs; this coordinator's only job is to drive the flow state correctly.
 */
class EmergencyServiceFlowCoordinator(
    private val nearbySearchSource: NearbySearchSource,
    private val placeDetailsSource: PlaceDetailsSource,
    private val reporter: EmergencyServiceFlowReporter,
) {
    suspend fun run(incidentType: IncidentType, location: GeoCoordinates): Result<EmergencySnapshot> {
        reporter.reportEmergencyServiceFlow(EmergencyServiceFlowState.InProgress)

        val category = IncidentTypeToPlacesCategoryMapper.categoryFor(incidentType)
            ?: return reporter.reportEmergencyServiceFlow(EmergencyServiceFlowState.LookupFailed)

        val nearest = runCatching { nearbySearchSource.search(location, category) }
            .getOrNull()
            ?.minByOrNull { distanceMeters(location, it.coordinates) }
            ?: return reporter.reportEmergencyServiceFlow(EmergencyServiceFlowState.LookupFailed)

        val details = runCatching { placeDetailsSource.getDetails(nearest.placeId) }.getOrNull()
        return reporter.reportEmergencyServiceFlow(
            if (details != null) EmergencyServiceFlowState.Succeeded else EmergencyServiceFlowState.LookupFailed,
        )
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
