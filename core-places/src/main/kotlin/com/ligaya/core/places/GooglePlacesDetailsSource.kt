package com.ligaya.core.places

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * The real Place Details adapter, over the New Places API's `places/{placeId}` endpoint.
 * Requires the same Google Cloud API key as GooglePlacesNearbySearchSource — see that class's
 * doc comment.
 *
 * `nationalPhoneNumber` missing from the response (rather than an HTTP error) is section 16's
 * normal "no public contact information" outcome — mapped to PlaceDetails(phoneNumber = null),
 * not to returning null from this function, which is reserved for the lookup itself failing.
 * `displayName`/`formattedAddress` follow the same rule: real, standard Places API fields, each
 * mapped to null rather than a placeholder string when the response omits it, per the same
 * "never invent" rule this class already applies to the phone number.
 */
class GooglePlacesDetailsSource(
    private val apiKey: String,
) : PlaceDetailsSource {

    override suspend fun getDetails(placeId: String): PlaceDetails? = withContext(Dispatchers.IO) {
        val connection = (URL("$PLACES_BASE_URL/$placeId").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty("X-Goog-FieldMask", "nationalPhoneNumber,displayName,formattedAddress")
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@withContext null
            }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val response = lenientJson.decodeFromString<PlaceDetailsResponse>(responseBody)
            PlaceDetails(
                phoneNumber = response.nationalPhoneNumber,
                name = response.displayName?.text,
                address = response.formattedAddress,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data class PlaceDetailsResponse(
        val nationalPhoneNumber: String? = null,
        val displayName: LocalizedText? = null,
        val formattedAddress: String? = null,
    )

    @Serializable
    private data class LocalizedText(val text: String? = null, val languageCode: String? = null)

    companion object {
        private const val PLACES_BASE_URL = "https://places.googleapis.com/v1/places"
        private val lenientJson = Json { ignoreUnknownKeys = true }
    }
}
