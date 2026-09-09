package com.ligaya.core.backend.family

import com.google.firebase.functions.FirebaseFunctions
import com.ligaya.core.data.family.FamilyEmergencyLocation
import com.ligaya.core.data.family.FamilyEmergencyView
import kotlinx.coroutines.tasks.await

/**
 * Fetches section 18's family emergency view (Step 20) — the ONLY path to this data, since there
 * is no Firestore rule letting a client read the underlying emergencyEvents/locationEvents
 * documents for someone else's emergency directly. A permission-denied field is never present in
 * the raw callable response at all (backend/functions/family-emergency-view.js), so there is
 * nothing for this class to additionally redact — it only ever maps what the backend already
 * decided to send.
 */
interface FamilyEmergencyViewRepository {
    suspend fun getFamilyEmergencyView(eventId: String): FamilyEmergencyView
}

class FirebaseFamilyEmergencyViewRepository(
    private val functions: FirebaseFunctions,
) : FamilyEmergencyViewRepository {

    override suspend fun getFamilyEmergencyView(eventId: String): FamilyEmergencyView {
        val result = functions.getHttpsCallable("getFamilyEmergencyView")
            .call(mapOf("eventId" to eventId))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as Map<String, Any?>
        return parseFamilyEmergencyView(data)
    }
}

/**
 * Split out from the repository so this mapping — the part most likely to have a subtle bug
 * (numeric type casts, nested map access) — is unit-testable without a real FirebaseFunctions
 * call. `data` is exactly the shape of a Firebase Functions callable's raw JSON result: a
 * loosely-typed Map<String, Any?>, never a generated model class.
 */
internal fun parseFamilyEmergencyView(data: Map<String, Any?>): FamilyEmergencyView {
    val locationMap = data["location"] as? Map<*, *>
    return FamilyEmergencyView(
        incidentType = data["incidentType"] as String,
        timeEpochMillis = (data["time"] as Number).toLong(),
        status = data["status"] as String,
        alertState = data["alertState"] as String?,
        location = locationMap?.let {
            FamilyEmergencyLocation(
                latitude = (it["latitude"] as Number).toDouble(),
                longitude = (it["longitude"] as Number).toDouble(),
            )
        },
    )
}
