package com.ligaya.core.places

/**
 * A place's contact information, once looked up. `phoneNumber` is nullable by design: section
 * 16's own rule is "if no trustworthy/public contact exists, do not invent a number" — a
 * successful lookup with no phone number is a valid, expected outcome, not a failure.
 *
 * [name] and [address] are the same kind of optional, never-fabricated field: the Places API can
 * omit either, and a missing one is shown as absent rather than guessed at. Appended after
 * [phoneNumber] with defaults so every existing single-arg `PlaceDetails(phoneNumber)` call —
 * this whole module's own tests included — keeps compiling unchanged.
 */
data class PlaceDetails(val phoneNumber: String?, val name: String? = null, val address: String? = null)

/**
 * Abstracts the Places Details call so it's unit-testable without any network access.
 * GooglePlacesDetailsSource is the real implementation. Returns null only when the lookup itself
 * could not be completed (e.g. the place no longer exists) — not when the place exists but has
 * no phone number, which PlaceDetails.phoneNumber = null already covers.
 */
interface PlaceDetailsSource {
    suspend fun getDetails(placeId: String): PlaceDetails?
}
