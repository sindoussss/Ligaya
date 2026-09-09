package com.ligaya.core.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LigayaMessagingHandlerTest {

    private class RecordingPushTokenRepository : PushTokenRepository {
        val savedTokens = mutableListOf<Pair<String, String>>()
        override suspend fun saveToken(userId: String, token: String) {
            savedTokens += userId to token
        }
    }

    private class RecordingNotificationEventRepository : NotificationEventRepository {
        val confirmedCalls = mutableListOf<Pair<String, String>>()
        var confirmResult: Result<Unit> = Result.success(Unit)

        override fun observeStatus(eventId: String, notificationEventId: String): Flow<NotificationEventState?> = emptyFlow()

        override suspend fun confirmDelivery(eventId: String, notificationEventId: String): Result<Unit> {
            confirmedCalls += eventId to notificationEventId
            return confirmResult
        }
    }

    @Test
    fun `handleNewToken saves the token for the signed-in user`() = runTest {
        val tokens = RecordingPushTokenRepository()
        val handler = LigayaMessagingHandler(
            currentUserId = { "user-123" },
            pushTokenRepository = tokens,
            notificationEventRepository = RecordingNotificationEventRepository(),
        )

        handler.handleNewToken("token-abc")

        assertEquals(listOf("user-123" to "token-abc"), tokens.savedTokens)
    }

    @Test
    fun `handleNewToken does nothing when nobody is signed in`() = runTest {
        val tokens = RecordingPushTokenRepository()
        val handler = LigayaMessagingHandler(
            currentUserId = { null },
            pushTokenRepository = tokens,
            notificationEventRepository = RecordingNotificationEventRepository(),
        )

        handler.handleNewToken("token-abc")

        assertTrue(tokens.savedTokens.isEmpty())
    }

    @Test
    fun `handleMessageReceived confirms delivery when both ids are present`() = runTest {
        val notificationEvents = RecordingNotificationEventRepository()
        val handler = LigayaMessagingHandler(
            currentUserId = { "user-123" },
            pushTokenRepository = RecordingPushTokenRepository(),
            notificationEventRepository = notificationEvents,
        )

        val result = handler.handleMessageReceived(mapOf("eventId" to "event-1", "notificationEventId" to "notif-1"))

        assertEquals(listOf("event-1" to "notif-1"), notificationEvents.confirmedCalls)
        assertEquals(Result.success(Unit), result)
    }

    @Test
    fun `handleMessageReceived does nothing when eventId is missing`() = runTest {
        val notificationEvents = RecordingNotificationEventRepository()
        val handler = LigayaMessagingHandler(
            currentUserId = { "user-123" },
            pushTokenRepository = RecordingPushTokenRepository(),
            notificationEventRepository = notificationEvents,
        )

        val result = handler.handleMessageReceived(mapOf("notificationEventId" to "notif-1"))

        assertNull(result)
        assertTrue(notificationEvents.confirmedCalls.isEmpty())
    }

    @Test
    fun `handleMessageReceived does nothing when notificationEventId is missing`() = runTest {
        val notificationEvents = RecordingNotificationEventRepository()
        val handler = LigayaMessagingHandler(
            currentUserId = { "user-123" },
            pushTokenRepository = RecordingPushTokenRepository(),
            notificationEventRepository = notificationEvents,
        )

        val result = handler.handleMessageReceived(mapOf("eventId" to "event-1"))

        assertNull(result)
        assertTrue(notificationEvents.confirmedCalls.isEmpty())
    }

    @Test
    fun `handleMessageReceived propagates a confirmation failure rather than swallowing it`() = runTest {
        val notificationEvents = RecordingNotificationEventRepository().apply {
            confirmResult = Result.failure(IllegalStateException("callable failed"))
        }
        val handler = LigayaMessagingHandler(
            currentUserId = { "user-123" },
            pushTokenRepository = RecordingPushTokenRepository(),
            notificationEventRepository = notificationEvents,
        )

        val result = handler.handleMessageReceived(mapOf("eventId" to "event-1", "notificationEventId" to "notif-1"))

        assertTrue(result != null && result.isFailure)
    }
}
