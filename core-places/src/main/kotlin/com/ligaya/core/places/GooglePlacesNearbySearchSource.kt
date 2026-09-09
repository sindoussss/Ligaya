package com.ligaya.core.places

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * The real Nearby Search adapter, over the New Places API's `places:searchNearby` endpoint.
 * Requires a Google Cloud API key with the Places API (New) enabled — `apiKey` is a plain
 * constructor parameter rather than anything hardcoded here, since obtaining one is an account
 * action only the app's own operator can take (see this step's report for what's needed).
 *
 * Uses a raw HttpURLConnection + kotlinx.serialization rather than adding a full HTTP client
 * library for what is currently two endpoints — see GooglePlacesDetailsSource, the only other
 * caller.
 */
class GooglePlacesNearbySearchSource(
    private val apiKey: String,
    private val searchRadiusMeters: Double = 5_000.0,
    private val maxResultCount: Int = 5,
) : NearbySearchSource {

    override suspend fun search(near: GeoCoordinates, category: PlacesCategory): List<PlaceCandidate> =
        withContext(Dispatchers.IO) {
            val requestBody = Json.encodeToString(
                SearchNearbyRequest(
                    includedTypes = listOf(category.placesType),
                    maxResultCount = maxResultCount,
                    locationRestriction = LocationRestriction(
                        circle = Circle(
                            center = LatLng(latitude = near.latitude, longitude = near.longitude),
                            radius = searchRadiusMeters,
                        ),
                    ),
                ),
            )

            val connection = (URL(SEARCH_NEARBY_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", apiKey)
                setRequestProperty("X-Goog-FieldMask", "places.id,places.location")
            }

            try {
                connection.outputStream.use { it.write(requestBody.toByteArray()) }
                if (connection.responseCode !in 200..299) {
                    return@withContext emptyList()
                }
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val response = lenientJson.decodeFromString<SearchNearbyResponse>(responseBody)
                response.places.mapNotNull { place ->
                    val loc = place.location ?: return@mapNotNull null
                    PlaceCandidate(placeId = place.id, coordinates = GeoCoordinates(loc.latitude, loc.longitude))
                }
            } finally {
                connection.disconnect()
            }
        }

    @Serializable
    private data class SearchNearbyRequest(
        val includedTypes: List<String>,
        val maxResultCount: Int,
        val locationRestriction: LocationRestriction,
    )

    @Serializable
    private data class LocationRestriction(val circle: Circle)

    @Serializable
    private data class Circle(val center: LatLng, val radius: Double)

    @Serializable
    private data class LatLng(val latitude: Double, val longitude: Double)

    @Serializable
    private data class SearchNearbyResponse(val places: List<Place> = emptyList())

    @Serializable
    private data class Place(val id: String, val location: LatLng? = null)

    companion object {
        private const val SEARCH_NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby"
        private val lenientJson = Json { ignoreUnknownKeys = true }
    }
}
