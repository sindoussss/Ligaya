package com.ligaya.core.notifications

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads and confirms a single NOTIFICATION_EVENT document. The sender's side observes
 * [observeStatus] to reflect CONFIRMED once the recipient acknowledges, and — as of Step 28 — to
 * tell a PUSH_FAILED apart from an SMS_FAILED, not just see a generic FAILED; the recipient's
 * side calls [confirmDelivery] from LigayaMessagingHandler once its own device actually receives
 * the push.
 */
interface NotificationEventRepository {
    fun observeStatus(eventId: String, notificationEventId: String): Flow<NotificationEventState?>
    suspend fun confirmDelivery(eventId: String, notificationEventId: String): Result<Unit>
}

class FirestoreNotificationEventRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : NotificationEventRepository {

    private fun notificationEventDoc(eventId: String, notificationEventId: String) =
        firestore.collection("emergencyEvents").document(eventId)
            .collection("notificationEvents").document(notificationEventId)

    override fun observeStatus(eventId: String, notificationEventId: String): Flow<NotificationEventState?> =
        callbackFlow {
            val registration = notificationEventDoc(eventId, notificationEventId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val status = snapshot?.getString("status")
                        ?.let { raw -> runCatching { NotificationDeliveryState.valueOf(raw) }.getOrNull() }
                    val channel = when (snapshot?.getString("channel")) {
                        "push" -> NotificationChannel.PUSH
                        "sms" -> NotificationChannel.SMS
                        else -> null
                    }
                    trySend(if (status != null && channel != null) NotificationEventState(channel, status) else null)
                }
            awaitClose { registration.remove() }
        }

    // The CONFIRMED write itself only ever happens through the trusted
    // confirmNotificationDeliveryCallable Cloud Function (backend/functions/index.js), never as
    // a direct client write — matching firestore.rules' own note that NOTIFICATION_EVENT state
    // transitions are backend-only.
    override suspend fun confirmDelivery(eventId: String, notificationEventId: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("confirmNotificationDeliveryCallable")
            .call(mapOf("eventId" to eventId, "notificationEventId" to notificationEventId))
            .await()
        Unit
    }
}
