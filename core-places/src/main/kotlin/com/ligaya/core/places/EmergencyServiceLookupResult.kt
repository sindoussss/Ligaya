package com.ligaya.core.places

/**
 * What [EmergencyServiceFlowCoordinator] actually found, for whatever later step shows it — a
 * name and a distance, never a claim about dispatch. [EmergencyServiceFlowState] itself stays a
 * bare status marker (section 25: the persisted state-machine schema, unchanged by this class)
 * because it is queried, migrated and crash-recovered as part of that machine; this result is
 * supplementary display detail with none of those requirements, reported alongside it rather
 * than folded into it.
 *
 * [name] and [phoneNumber] are independently nullable: a real place can be missing either from
 * the Places API response, and each is shown as genuinely absent rather than backfilled with the
 * other. [distanceMeters] always accompanies a non-null result — the coordinator only ever
 * constructs one once it has picked an actual nearest candidate to measure from.
 */
data class EmergencyServiceLookupResult(
    val name: String?,
    val address: String?,
    val phoneNumber: String?,
    val distanceMeters: Double,
)

/**
 * Same shape and reasoning as [EmergencyServiceFlowReporter]: abstracts "hand the looked-up result
 * to whatever owns showing it" so this module never depends on Android or on how that later layer
 * stores or renders it. Reported with `null` on every path that does not end in a real place (no
 * category for this incident type, no candidate found, details lookup failed) — the coordinator's
 * one rule for this reporter is that it is never called with a result the app cannot back up.
 */
fun interface EmergencyServiceResultReporter {
    suspend fun reportEmergencyServiceResult(result: EmergencyServiceLookupResult?)
}
