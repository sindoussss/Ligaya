package com.ligaya.core.places

/** A candidate returned by a Nearby Search — just enough to rank it and look up its details. */
data class PlaceCandidate(val placeId: String, val coordinates: GeoCoordinates)

/**
 * Abstracts the Places Nearby Search call so EmergencyServiceFlowCoordinator's own
 * ranking/filtering logic (section 16: "do not assume the first API result is automatically the
 * nearest") is unit-testable without any network access. GooglePlacesNearbySearchSource is the
 * real implementation.
 *
 * Returns candidates in whatever order the source produced them — deliberately not required to
 * be nearest-first, since the coordinator itself must never assume that.
 */
interface NearbySearchSource {
    suspend fun search(near: GeoCoordinates, category: PlacesCategory): List<PlaceCandidate>
}
