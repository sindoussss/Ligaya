package com.ligaya.feature.companion

import com.ligaya.core.ai.CompanionResponseProvider
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.voice.EmergencyStatusMessage
import com.ligaya.core.voice.SpeechOutput
import com.ligaya.core.voice.SpeechResult
import com.ligaya.core.voice.SpeechTranscriber
import com.ligaya.core.voice.TranscriptionEvent
import com.ligaya.core.voice.TranscriptionFailureReason
import com.ligaya.core.voice.VoiceCaptureCoordinator
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.core.ai.ValidatedSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Step 38's own acceptance criterion, verified directly against the actual pipeline callbacks
 * (not a UI mock): [EmergencyCompanionCoordinator.phase] transitions through exactly
 * LISTENING -> PROCESSING -> SPEAKING, in that order, and always returns to IDLE — including on
 * the early-return paths (NoSpeechCaptured, ResponseBlocked), not just the successful one.
 */
class EmergencyCompanionCoordinatorPhaseTest {

    private class GrantedPermissionChecker : PermissionChecker {
        override fun currentState(permission: String) = PermissionState.Granted
    }

    private class RecordingSpeechOutput : SpeechOutput {
        override suspend fun speak(message: EmergencyStatusMessage) = SpeechResult.SPOKEN
        override suspend fun speak(speech: ValidatedSpeech) = SpeechResult.SPOKEN
    }

    private fun transcriberEmitting(vararg events: TranscriptionEvent) = SpeechTranscriber { flowOf(*events) }

    private fun captureCoordinatorEmitting(transcript: String) =
        VoiceCaptureCoordinator(
            transcriberEmitting(TranscriptionEvent.Success(transcript, isFinal = true)),
            GrantedPermissionChecker(),
        )

    private fun snapshotProviderFor(subsystems: ConcurrentSubsystemStates) =
        EmergencySnapshotProvider { EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE, subsystems = subsystems) }

    @Test
    fun `a successful turn transitions listening then processing then speaking then back to idle`() = runTest {
        val coordinator = EmergencyCompanionCoordinator(
            captureCoordinatorEmitting("Tulong, sunog!"),
            CompanionResponseProvider { _, _ -> "I'm right here with you. Can you tell me more?" },
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates(unified911 = Unified911FlowState.Succeeded)),
        )
        val recordedPhases = mutableListOf<VoicePipelinePhase>()
        val collectJob = launch(Dispatchers.Unconfined) { coordinator.phase.toList(recordedPhases) }

        assertEquals(VoicePipelinePhase.IDLE, coordinator.phase.value)
        coordinator.runOneTurn()
        collectJob.cancel()

        assertEquals(
            listOf(
                VoicePipelinePhase.IDLE,
                VoicePipelinePhase.LISTENING,
                VoicePipelinePhase.PROCESSING,
                VoicePipelinePhase.SPEAKING,
                VoicePipelinePhase.IDLE,
            ),
            recordedPhases,
        )
    }

    @Test
    fun `no final transcript stops at listening then returns to idle without reaching processing`() = runTest {
        val silentCapture = VoiceCaptureCoordinator(
            transcriberEmitting(TranscriptionEvent.Failure(TranscriptionFailureReason.NO_SPEECH_DETECTED)),
            GrantedPermissionChecker(),
        )
        val coordinator = EmergencyCompanionCoordinator(
            silentCapture,
            CompanionResponseProvider { _, _ -> "unused" },
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates()),
        )
        val recordedPhases = mutableListOf<VoicePipelinePhase>()
        val collectJob = launch(Dispatchers.Unconfined) { coordinator.phase.toList(recordedPhases) }

        val result = coordinator.runOneTurn()
        collectJob.cancel()

        assertEquals(CompanionTurnResult.NoSpeechCaptured, result)
        assertEquals(listOf(VoicePipelinePhase.IDLE, VoicePipelinePhase.LISTENING, VoicePipelinePhase.IDLE), recordedPhases)
    }

    @Test
    fun `a fully blocked response reaches processing but never speaking, then returns to idle`() = runTest {
        val coordinator = EmergencyCompanionCoordinator(
            captureCoordinatorEmitting("Tulong!"),
            CompanionResponseProvider { _, _ -> "911 has been contacted and help is on the way." },
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates()), // everything Pending -> unverified -> blocked
        )
        val recordedPhases = mutableListOf<VoicePipelinePhase>()
        val collectJob = launch(Dispatchers.Unconfined) { coordinator.phase.toList(recordedPhases) }

        val result = coordinator.runOneTurn()
        collectJob.cancel()

        assertEquals(CompanionTurnResult.ResponseBlocked, result)
        assertEquals(
            listOf(VoicePipelinePhase.IDLE, VoicePipelinePhase.LISTENING, VoicePipelinePhase.PROCESSING, VoicePipelinePhase.IDLE),
            recordedPhases,
        )
    }
}
