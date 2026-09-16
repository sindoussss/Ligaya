package com.ligaya.feature.companion

import com.ligaya.core.ai.CompanionResponseProvider
import com.ligaya.core.ai.CompanionTurn
import com.ligaya.core.ai.ValidatedSpeech
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.voice.EmergencyStatusMessage
import com.ligaya.core.voice.SpeechOutput
import com.ligaya.core.voice.SpeechResult
import com.ligaya.core.voice.SpeechTranscriber
import com.ligaya.core.voice.VoiceCaptureCoordinator
import com.ligaya.core.voice.VoicePipelinePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Step 39's own acceptance criterion at the coordinator level: "a user who never speaks can
 * still hold a full companion conversation via the text fallback." [runOneTurnWithText] is
 * proven here to never touch [VoiceCaptureCoordinator] at all (a poisoned capture coordinator
 * that fails the test if collected), to skip the LISTENING phase entirely, and to share the same
 * running transcript a voice turn would.
 */
class EmergencyCompanionCoordinatorTextFallbackTest {

    private class GrantedPermissionChecker : PermissionChecker {
        override fun currentState(permission: String) = PermissionState.Granted
    }

    private class RecordingSpeechOutput : SpeechOutput {
        val spokenValidatedSpeech = mutableListOf<ValidatedSpeech>()
        override suspend fun speak(message: EmergencyStatusMessage) = SpeechResult.SPOKEN
        override suspend fun speak(speech: ValidatedSpeech): SpeechResult {
            spokenValidatedSpeech += speech
            return SpeechResult.SPOKEN
        }
    }

    /** A capture coordinator whose Flow fails the test the moment anything collects it — a text
     *  fallback turn must never reach this at all. */
    private fun poisonedCaptureCoordinator() = VoiceCaptureCoordinator(
        SpeechTranscriber { flow { throw AssertionError("a text fallback turn must never use voice capture") } },
        GrantedPermissionChecker(),
    )

    private fun snapshotProviderFor(subsystems: ConcurrentSubsystemStates) =
        EmergencySnapshotProvider { EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE, subsystems = subsystems) }

    private val safeReply = "I'm right here with you. Can you tell me more?"

    @Test
    fun `runOneTurnWithText produces a spoken reply without ever touching voice capture`() = runTest {
        val speechOutput = RecordingSpeechOutput()
        val coordinator = EmergencyCompanionCoordinator(
            poisonedCaptureCoordinator(),
            CompanionResponseProvider { _, _ -> safeReply },
            speechOutput,
            snapshotProviderFor(ConcurrentSubsystemStates(unified911 = Unified911FlowState.Succeeded)),
        )

        val result = coordinator.runOneTurnWithText("Tulong, sunog!")

        assertEquals(CompanionTurnResult.Spoken(safeReply), result)
        assertEquals(listOf(safeReply), speechOutput.spokenValidatedSpeech.map { it.text })
    }

    @Test
    fun `transcript exposes both the user's typed utterance and Ligaya's spoken reply`() = runTest {
        val coordinator = EmergencyCompanionCoordinator(
            poisonedCaptureCoordinator(),
            CompanionResponseProvider { _, _ -> safeReply },
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates(unified911 = Unified911FlowState.Succeeded)),
        )

        coordinator.runOneTurnWithText("Tulong!")

        val transcript = coordinator.transcript.value
        assertEquals(2, transcript.size)
        assertEquals(CompanionTurn.Speaker.USER, transcript[0].speaker)
        assertEquals("Tulong!", transcript[0].text)
        assertEquals(CompanionTurn.Speaker.LIGAYA, transcript[1].speaker)
        assertEquals(safeReply, transcript[1].text)
    }

    @Test
    fun `a text fallback turn skips LISTENING entirely, going straight from idle to processing`() = runTest {
        val coordinator = EmergencyCompanionCoordinator(
            poisonedCaptureCoordinator(),
            CompanionResponseProvider { _, _ -> safeReply },
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates(unified911 = Unified911FlowState.Succeeded)),
        )
        val recordedPhases = mutableListOf<VoicePipelinePhase>()
        val collectJob = launch(Dispatchers.Unconfined) { coordinator.phase.toList(recordedPhases) }

        coordinator.runOneTurnWithText("Tulong!")
        collectJob.cancel()

        assertEquals(
            listOf(
                VoicePipelinePhase.IDLE,
                VoicePipelinePhase.PROCESSING,
                VoicePipelinePhase.SPEAKING,
                VoicePipelinePhase.IDLE,
            ),
            recordedPhases,
        )
    }

    @Test
    fun `text fallback turns accumulate into the same running transcript across multiple turns`() = runTest {
        val coordinator = EmergencyCompanionCoordinator(
            poisonedCaptureCoordinator(),
            CompanionResponseProvider { _, _ -> safeReply },
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates(unified911 = Unified911FlowState.Succeeded)),
        )

        coordinator.runOneTurnWithText("Unang mensahe.")
        coordinator.runOneTurnWithText("Pangalawang mensahe.")

        val transcript = coordinator.transcript.value
        assertEquals(4, transcript.size)
        assertEquals("Unang mensahe.", transcript[0].text)
        assertEquals("Pangalawang mensahe.", transcript[2].text)
    }
}
