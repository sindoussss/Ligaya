package com.ligaya.core.notifications

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Registers this device's current FCM token against the signed-in user's own backend record, so
 * the family-alert Cloud Function (backend/functions/family-alerts.js) knows where to send a
 * push. Written to `users/{userId}.fcm_token` — already covered by the existing
 * `users/{userId}` Firestore rule (owner-only read/write, Step 2), so no rules change was needed
 * for this step.
 */
interface PushTokenRepository {
    suspend fun saveToken(userId: String, token: String)
}

class FirestorePushTokenRepository(
    private val firestore: FirebaseFirestore,
) : PushTokenRepository {

    override suspend fun saveToken(userId: String, token: String) {
        firestore.collection("users").document(userId)
            .set(mapOf("fcm_token" to token), SetOptions.merge())
            .await()
    }
}
