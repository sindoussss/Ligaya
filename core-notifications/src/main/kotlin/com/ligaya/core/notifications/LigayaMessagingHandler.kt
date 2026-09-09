package com.ligaya.core.notifications

/**
 * The testable logic behind LigayaMessagingService's two FCM callbacks, kept separate from the
 * actual Service class (which Android itself constructs and which is impractical to unit-test
 * directly) — same "thin platform wrapper delegates to a plain Kotlin class" pattern used
 * throughout this codebase (e.g. core-location's LocationFlowCoordinator, Step 15).
 */
class LigayaMessagingHandler(
    private val currentUserId: () -> String?,
    private val pushTokenRepository: PushTokenRepository,
    private val notificationEventRepository: NotificationEventRepository,
) {
    /** A token refresh can happen before sign-in (or after sign-out) — silently does nothing
     *  then, since there is no user record yet to attach it to; the next onNewToken (Firebase
     *  fires one shortly after a fresh install/token rotation) or an explicit re-save at sign-in
     *  time covers that case. */
    suspend fun handleNewToken(token: String) {
        val userId = currentUserId() ?: return
        pushTokenRepository.saveToken(userId, token)
    }

    /** Returns null if the message didn't carry both IDs (not one of ours to confirm) rather
     *  than throwing — a malformed or unrelated push must never crash this service. */
    suspend fun handleMessageReceived(data: Map<String, String>): Result<Unit>? {
        val eventId = data["eventId"] ?: return null
        val notificationEventId = data["notificationEventId"] ?: return null
        return notificationEventRepository.confirmDelivery(eventId, notificationEventId)
    }
}
