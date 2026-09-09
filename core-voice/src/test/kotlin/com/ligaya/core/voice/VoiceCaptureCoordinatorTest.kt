package com.ligaya.core.voice

import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCaptureCoordinatorTest {

    private class FakePermissionChecker(private val state: PermissionState) : PermissionChecker {
        override fun currentState(permission: String): PermissionState = state
    }

    private class RecordingTranscriber(private val events: Flow<TranscriptionEvent>) : SpeechTranscriber {
        var wasCalled = false
            private set

        override fun startListening(): Flow<TranscriptionEvent> {
            wasCalled = true
            return events
        }
    }

    @Test
    fun `permission denied reports Failure PERMISSION_DENIED without ever starting the transcriber`() = runTest {
        val transcriber = RecordingTranscriber(
            flowOf(TranscriptionEvent.Success("should never be reached", isFinal = true)),
        )
        val coordinator = VoiceCaptureCoordinator(transcriber, FakePermissionChecker(PermissionState.Denied))

        val events = coordinator.startListening().toList()

        assertFalse(transcriber.wasCalled)
        assertEquals(listOf(TranscriptionEvent.Failure(TranscriptionFailureReason.PERMISSION_DENIED)), events)
    }

    @Test
    fun `permission granted delegates to and forwards the transcriber's events`() = runTest {
        val expectedEvents = listOf(
            TranscriptionEvent.Success("Ligaya, tulong", isFinal = false),
            TranscriptionEvent.Success("Ligaya, tulong. May sunog.", isFinal = true),
        )
        val transcriber = RecordingTranscriber(flowOf(*expectedEvents.toTypedArray()))
        val coordinator = VoiceCaptureCoordinator(transcriber, FakePermissionChecker(PermissionState.Granted))

        val events = coordinator.startListening().toList()

        assertTrue(transcriber.wasCalled)
        assertEquals(expectedEvents, events)
    }
}
