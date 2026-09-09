package com.ligaya.feature.companion

import com.ligaya.core.ai.CompanionResponseProvider
import com.ligaya.core.ai.CompanionTurn
import com.ligaya.core.ai.ValidatedSpeech
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.voice.EmergencyStatusMessage
import com.ligaya.core.voice.SpeechOutput
import com.ligaya.core.voice.SpeechTranscriber
import com.ligaya.core.voice.TranscriptionEvent
import com.ligaya.core.voice.TranscriptionFailureReason
import com.ligaya.core.voice.VoiceCaptureCoordinator
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers this step's own acceptance criteria: the companion loop keeps functioning — produces a
 * spoken reply — with each of the other four concurrent subsystems (section 13) forced into a
 * failure state, one at a time. A fixed, always-safe reply is used throughout so these tests
 * isolate "does the loop itself keep running," not ResponseValidator's own claim-blocking logic,
 * which Step 25's own test suite already covers exhaustively.
 */
class EmergencyCompanionCoordinatorTest {

    private class GrantedPermissionChecker : PermissionChecker {
        override fun currentState(permission: String) = PermissionState.Granted
    }

    private class RecordingSpeechOutput : SpeechOutput {
        val spokenStatusMessages = mutableListOf<EmergencyStatusMessage>()
        val spokenValidatedSpeech = mutableListOf<ValidatedSpeech>()

        override suspend fun speak(message: EmergencyStatusMessage) {
            spokenStatusMessages += message
        }

        override suspend fun speak(speech: ValidatedSpeech) {
            spokenValidatedSpeech += speech
        }
    }

    private fun transcriberEmitting(vararg events: TranscriptionEvent) = SpeechTranscriber { flowOf(*events) }

    private fun captureCoordinatorEmitting(transcript: String) =
        VoiceCaptureCoordinator(
            transcriberEmitting(TranscriptionEvent.Success(transcript, isFinal = true)),
            GrantedPermissionChecker(),
        )

    private val safeReply = "I'm right here with you. Can you tell me more?"

    private fun fixedResponseProvider(reply: String) = CompanionResponseProvider { _, _ -> reply }

    private fun snapshotProviderFor(subsystems: ConcurrentSubsystemStates) =
        EmergencySnapshotProvider { EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE, subsystems = subsystems) }

    private fun coordinatorFor(
        transcript: String,
        reply: String,
        subsystems: ConcurrentSubsystemStates,
        speechOutput: RecordingSpeechOutput,
    ) = EmergencyCompanionCoordinator(
        captureCoordinator = captureCoordinatorEmitting(transcript),
        responseProvider = fixedResponseProvider(reply),
        speechOutput = speechOutput,
        snapshotProvider = snapshotProviderFor(subsystems),
    )

    // --- The acceptance criterion, proven one subsystem at a time ---

    @Test
    fun `companion loop keeps functioning with the 911 subsystem failed`() = runTest {
        val speechOutput = RecordingSpeechOutput()
        val coordinator = coordinatorFor(
            "May sunog pa rin.",
            safeReply,
            ConcurrentSubsystemStates(unified911 = Unified911FlowState.CallFailed),
            speechOutput,
        )

        val result = coordinator.runOneTurn()

        assertEquals(CompanionTurnResult.Spoken(safeReply), result)
        assertEquals(listOf(safeReply), speechOutput.spokenValidatedSpeech.map { it.text })
    }

    @Test
    fun `companion loop keeps functioning with the location subsystem failed`() = runTest {
        val speechOutput = RecordingSpeechOutput()
        val coordinator = coordinatorFor(
            "Hindi ko alam kung saan ako.",
            safeReply,
            ConcurrentSubsystemStates(location = LocationFlowState.Unavailable),
            speechOutput,
        )

        val result = coordinator.runOneTurn()

        assertEquals(CompanionTurnResult.Spoken(safeReply), result)
    }

    @Test
    fun `companion loop keeps functioning with the emergency-service (Places) subsystem failed`() = runTest {
        val speechOutput = RecordingSpeechOutput()
        val coordinator = coordinatorFor(
            "Malapit ba ang ospital?",
            safeReply,
            ConcurrentSubsystemStates(emergencyService = EmergencyServiceFlowState.LookupFailed),
            speechOutput,
        )

        val result = coordinator.runOneTurn()

        assertEquals(CompanionTurnResult.Spoken(safeReply), result)
    }

    @Test
    fun `companion loop keeps functioning with the family-alert subsystem failed`() = runTest {
        val speechOutput = RecordingSpeechOutput()
        val coordinator = coordinatorFor(
            "Nasabihan mo na ba ang pamilya ko?",
            safeReply,
            ConcurrentSubsystemStates(familyAlert = FamilyAlertFlowState.DeliveryFailed),
            speechOutput,
        )

        val result = coordinator.runOneTurn()

        assertEquals(CompanionTurnResult.Spoken(safeReply), result)
    }

    @Test
    fun `companion loop keeps functioning with all four other subsystems failed at once`() = runTest {
        val speechOutput = RecordingSpeechOutput()
        val coordinator = coordinatorFor(
            "Ano na ang mangyayari?",
            safeReply,
            ConcurrentSubsystemStates(
                location = LocationFlowState.Unavailable,
                unified911 = Unified911FlowState.CallFailed,
                emergencyService = EmergencyServiceFlowState.LookupFailed,
                familyAlert = FamilyAlertFlowState.DeliveryFailed,
            ),
            speechOutput,
        )

        val result = coordinator.runOneTurn()

        assertEquals(CompanionTurnResult.Spoken(safeReply), result)
        assertEquals(1, speechOutput.spokenValidatedSpeech.size)
    }

    // --- Basic loop mechanics ---

    @Test
    fun `history accumulates both the user's utterance and Ligaya's reply across turns`() = runTest {
        var capturedHistory: List<CompanionTurn>? = null
        val responseProvider = CompanionResponseProvider { history, _ ->
            capturedHistory = history
            safeReply
        }
        val coordinator = EmergencyCompanionCoordinator(
            captureCoordinatorEmitting("Ligaya, tulong."),
            responseProvider,
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates()),
        )

        coordinator.runOneTurn()
        assertTrue("expected empty history on the first turn", capturedHistory!!.isEmpty())

        coordinator.runOneTurn()
        val secondTurnHistory = capturedHistory!!
        assertEquals(2, secondTurnHistory.size)
        assertEquals(CompanionTurn.Speaker.USER, secondTurnHistory[0].speaker)
        assertEquals(CompanionTurn.Speaker.LIGAYA, secondTurnHistory[1].speaker)
        assertEquals(safeReply, secondTurnHistory[1].text)
    }

    @Test
    fun `a fully blocked response reports ResponseBlocked, not an exception`() = runTest {
        val coordinator = EmergencyCompanionCoordinator(
            captureCoordinatorEmitting("Tulong!"),
            fixedResponseProvider("911 has been contacted and help is on the way."),
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates()), // everything Pending -> unverified
        )

        val result = coordinator.runOneTurn()

        assertEquals(CompanionTurnResult.ResponseBlocked, result)
    }

    @Test
    fun `no final transcript reports NoSpeechCaptured without calling Gemini or TTS`() = runTest {
        var responseProviderCalled = false
        val responseProvider = CompanionResponseProvider { _, _ -> responseProviderCalled = true; safeReply }
        val speechOutput = RecordingSpeechOutput()
        val silentCapture = VoiceCaptureCoordinator(
            transcriberEmitting(TranscriptionEvent.Failure(TranscriptionFailureReason.NO_SPEECH_DETECTED)),
            GrantedPermissionChecker(),
        )
        val coordinator = EmergencyCompanionCoordinator(
            silentCapture,
            responseProvider,
            speechOutput,
            snapshotProviderFor(ConcurrentSubsystemStates()),
        )

        val result = coordinator.runOneTurn()

        assertEquals(CompanionTurnResult.NoSpeechCaptured, result)
        assertTrue(!responseProviderCalled)
        assertTrue(speechOutput.spokenValidatedSpeech.isEmpty())
    }
}
