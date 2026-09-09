package com.ligaya.core.data.family

/**
 * Section 18's Family emergency screen field set, exactly as the backend's
 * getFamilyEmergencyView callable (Step 20, backend/functions/family-emergency-view.js) returns
 * it — already filtered server-side per the viewer's own permissions. [location] being null is
 * deliberately ambiguous between "you don't have permission to see it" and "no location has been
 * recorded yet": the whole point of this data contract is that this class can never distinguish
 * those two cases, because the backend never sends the real coordinates for the former.
 */
data class FamilyEmergencyView(
    val incidentType: String,
    val timeEpochMillis: Long,
    val status: String,
    val alertState: String?,
    val location: FamilyEmergencyLocation?,
)

data class FamilyEmergencyLocation(val latitude: Double, val longitude: Double)
