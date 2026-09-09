package com.ligaya.core.backend.profile

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ligaya.core.data.profile.EmergencyProfile
import com.ligaya.core.data.profile.EmergencyProfileSerializer
import kotlinx.coroutines.tasks.await

/**
 * Syncs EmergencyProfile to/from the users/{userId} Firestore document's emergency_profile
 * field (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24), governed by the owner-only rule
 * already in backend/firestore.rules from Step 2 — no rule changes were needed for this step.
 */
interface UserProfileRepository {
    suspend fun saveEmergencyProfile(userId: String, profile: EmergencyProfile)
    suspend fun getEmergencyProfile(userId: String): EmergencyProfile?
}

class FirestoreUserProfileRepository(
    private val firestore: FirebaseFirestore,
) : UserProfileRepository {

    override suspend fun saveEmergencyProfile(userId: String, profile: EmergencyProfile) {
        firestore.collection("users").document(userId)
            .set(mapOf("emergency_profile" to EmergencyProfileSerializer.toJson(profile)), SetOptions.merge())
            .await()
    }

    override suspend fun getEmergencyProfile(userId: String): EmergencyProfile? {
        val snapshot = firestore.collection("users").document(userId).get().await()
        val raw = snapshot.getString("emergency_profile") ?: return null
        return EmergencyProfileSerializer.fromJson(raw)
    }
}
