package com.ligaya.core.places

/**
 * A place's contact information, once looked up. `phoneNumber` is nullable by design: section
 * 16's own rule is "if no trustworthy/public contact exists, do not invent a number" — a
 * successful lookup with no phone number is a valid, expected outcome, not a failure.
 */
data class PlaceDetails(val phoneNumber: String?)

/**
 * Abstracts the Places Details call so it's unit-testable without any network access.
 * GooglePlacesDetailsSource is the real implementation. Returns null only when the lookup itself
 * could not be completed (e.g. the place no longer exists) — not when the place exists but has
 * no phone number, which PlaceDetails.phoneNumber = null already covers.
 */
interface PlaceDetailsSource {
    suspend fun getDetails(placeId: String): PlaceDetails?
}
