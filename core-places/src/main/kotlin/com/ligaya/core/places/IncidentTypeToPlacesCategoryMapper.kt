package com.ligaya.core.places

import com.ligaya.core.emergencyengine.IncidentType

/** Google Places "Table A" type strings relevant to emergency-service discovery. */
enum class PlacesCategory(val placesType: String) {
    FIRE_STATION("fire_station"),
    POLICE("police"),
    HOSPITAL("hospital"),
}

/**
 * Resolves this step's own flagged ambiguity (the architecture review's "Issue I"):
 * incident-type-to-Places-category mapping is not one-to-one. FIRE and POLICE map directly onto
 * Google Places' own taxonomy; MEDICAL maps to "hospital" — Places' Table A has no distinct
 * "ambulance" type, and a hospital is the closest real category to an emergency medical
 * responder.
 *
 * RESCUE and OTHER have no matching category at all: Places has nothing resembling a generic
 * "rescue" type, and guessing a loosely-related one (e.g. defaulting to "police" for a water
 * rescue) risks pointing the user at the wrong kind of responder — worse than not showing a
 * supplementary result. This mapper returns null for exactly those two incident types;
 * EmergencyServiceFlowCoordinator's defined fallback is to skip the Places call entirely and
 * report LookupFailed rather than sending a query with no meaningful category — the roadmap's
 * own required behavior ("the defined fallback behavior triggers instead of an empty/broken
 * query"). Section 16's own rule already covers the consequence: the 911 path remains available
 * regardless of this subsystem's outcome.
 */
object IncidentTypeToPlacesCategoryMapper {
    fun categoryFor(incidentType: IncidentType): PlacesCategory? = when (incidentType) {
        IncidentType.FIRE -> PlacesCategory.FIRE_STATION
        IncidentType.POLICE -> PlacesCategory.POLICE
        IncidentType.MEDICAL -> PlacesCategory.HOSPITAL
        IncidentType.RESCUE -> null
        IncidentType.OTHER -> null
    }
}
