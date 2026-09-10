package com.ligaya.core.voice

import com.ligaya.core.ai.IntentProvider
import com.ligaya.core.ai.VoiceInterpretationOutcome
import com.ligaya.core.emergencyengine.EmergencyIntentDecision
import com.ligaya.core.emergencyengine.IncidentType
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivationCoordinatorTest {

    private class FixedTranscriber(private val events: Flow<TranscriptionEvent>) : SpeechTranscriber {
        override fun startListening(): Flow<TranscriptionEvent> = events
    }

    private class GrantedPermissionChecker : PermissionChecker {
        override fun currentState(permission: String) = PermissionState.Granted
    }

    private class RecordingIntentProvider(private val outcome: VoiceInterpretationOutcome) : IntentProvider {
        val transcriptsSeen = mutableListOf<String>()
        override suspend fun interpret(transcript: String): VoiceInterpretationOutcome {
            transcriptsSeen += transcript
            return outcome
        }
    }

    private class RecordingReporter : VoiceEmergencyIntentReporter {
        val reportedIntents = mutableListOf<StructuredEmergencyIntent>()
        override suspend fun reportVoiceIntent(intent: StructuredEmergencyIntent): EmergencyIntentDecision {
            reportedIntents += intent
            return EmergencyIntentDecision.Confirmed(intent)
        }
    }

    private fun coordinatorFor(
        events: Flow<TranscriptionEvent>,
        intentProvider: IntentProvider,
        reporter: VoiceEmergencyIntentReporter,
    ) = VoiceActivationCoordinator(
        captureCoordinator = VoiceCaptureCoordinator(FixedTranscriber(events), GrantedPermissionChecker()),
        intentProvider = intentProvider,
        reporter = reporter,
    )

    @Test
    fun `a final transcript containing the wake phrase is interpreted and reported`() = runTest {
        val fireIntent = StructuredEmergencyIntent(
            emergency = true,
            incidentType = IncidentType.FIRE,
            confidence = 0.95f,
            userContext = "May sunog.",
            requestedLocation = true,
        )
        val intentProvider = RecordingIntentProvider(VoiceInterpretationOutcome.Interpreted(fireIntent))
        val reporter = RecordingReporter()
        val coordinator = coordinatorFor(
            events = flowOf(TranscriptionEvent.Success("Ligaya, tulong. May sunog.", isFinal = true)),
            intentProvider = intentProvider,
            reporter = reporter,
        )

        val results = coordinator.listenForWakePhrase().toList()

        assertEquals(listOf("Ligaya, tulong. May sunog."), intentProvider.transcriptsSeen)
        assertEquals(listOf(fireIntent), reporter.reportedIntents)
        assertEquals(
            listOf(VoiceActivationResult.Decision(EmergencyIntentDecision.Confirmed(fireIntent))),
            results,
        )
    }

    @Test
    fun `an AI-unavailable outcome reports AiUnavailable without ever reaching the reporter`() = runTest {
        val intentProvider = RecordingIntentProvider(VoiceInterpretationOutcome.Unavailable)
        val reporter = RecordingReporter()
        val coordinator = coordinatorFor(
            events = flowOf(TranscriptionEvent.Success("Ligaya, tulong. May sunog.", isFinal = true)),
            intentProvider = intentProvider,
            reporter = reporter,
        )

        val results = coordinator.listenForWakePhrase().toList()

        assertEquals(listOf(VoiceActivationResult.AiUnavailable), results)
        assertTrue("AI-unavailable must never reach the engine intake", reporter.reportedIntents.isEmpty())
    }

    @Test
    fun `a final transcript without the wake phrase is never interpreted or reported`() = runTest {
        val intentProvider = RecordingIntentProvider(
            VoiceInterpretationOutcome.Interpreted(StructuredEmergencyIntent(false, null, 0f, "", false)),
        )
        val reporter = RecordingReporter()
        val coordinator = coordinatorFor(
            events = flowOf(TranscriptionEvent.Success("Kumusta ka?", isFinal = true)),
            intentProvider = intentProvider,
            reporter = reporter,
        )

        val results = coordinator.listenForWakePhrase().toList()

        assertTrue(intentProvider.transcriptsSeen.isEmpty())
        assertTrue(reporter.reportedIntents.isEmpty())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `a partial result containing the wake phrase is never interpreted — only a final one is`() = runTest {
        val intentProvider = RecordingIntentProvider(
            VoiceInterpretationOutcome.Interpreted(StructuredEmergencyIntent(false, null, 0f, "", false)),
        )
        val reporter = RecordingReporter()
        val coordinator = coordinatorFor(
            events = flowOf(
                TranscriptionEvent.Success("Ligaya", isFinal = false),
                TranscriptionEvent.Success("Ligaya, tulong", isFinal = false),
            ),
            intentProvider = intentProvider,
            reporter = reporter,
        )

        val results = coordinator.listenForWakePhrase().toList()

        assertTrue(intentProvider.transcriptsSeen.isEmpty())
        assertTrue(results.isEmpty())
    }

    // --- Step 53 audit follow-up: `phase`, driving Home's own always-visible voice indicator ---

    @Test
    fun `phase starts IDLE, reaches PROCESSING before interpret is called, and settles back to IDLE once collection completes`() = runTest {
        lateinit var coordinator: VoiceActivationCoordinator
        var phaseWhenInterpretWasCalled: VoicePipelinePhase? = null
        val fireIntent = StructuredEmergencyIntent(true, IncidentType.FIRE, 0.9f, "May sunog.", true)
        coordinator = coordinatorFor(
            events = flowOf(TranscriptionEvent.Success("Ligaya, tulong. May sunog.", isFinal = true)),
            intentProvider = object : IntentProvider {
                override suspend fun interpret(transcript: String): VoiceInterpretationOutcome {
                    phaseWhenInterpretWasCalled = coordinator.phase.value
                    return VoiceInterpretationOutcome.Interpreted(fireIntent)
                }
            },
            reporter = RecordingReporter(),
        )

        assertEquals(VoicePipelinePhase.IDLE, coordinator.phase.value)
        coordinator.listenForWakePhrase().toList()

        assertEquals(VoicePipelinePhase.PROCESSING, phaseWhenInterpretWasCalled)
        assertEquals(VoicePipelinePhase.IDLE, coordinator.phase.value)
    }

    @Test
    fun `phase never reaches PROCESSING when no transcript ever contains the wake phrase`() = runTest {
        val intentProvider = RecordingIntentProvider(
            VoiceInterpretationOutcome.Interpreted(StructuredEmergencyIntent(false, null, 0f, "", false)),
        )
        val coordinator = coordinatorFor(
            events = flowOf(TranscriptionEvent.Success("Kumusta ka?", isFinal = true)),
            intentProvider = intentProvider,
            reporter = RecordingReporter(),
        )

        coordinator.listenForWakePhrase().toList()

        assertTrue(intentProvider.transcriptsSeen.isEmpty())
        assertEquals(VoicePipelinePhase.IDLE, coordinator.phase.value)
    }

    @Test
    fun `phase settles back to IDLE after a failure event with no matching transcript at all`() = runTest {
        val coordinator = coordinatorFor(
            events = flowOf(TranscriptionEvent.Failure(TranscriptionFailureReason.NO_SPEECH_DETECTED)),
            intentProvider = RecordingIntentProvider(VoiceInterpretationOutcome.Unavailable),
            reporter = RecordingReporter(),
        )

        coordinator.listenForWakePhrase().toList()

        assertEquals(VoicePipelinePhase.IDLE, coordinator.phase.value)
    }

    @Test
    fun `a failure event is never interpreted or reported`() = runTest {
        val intentProvider = RecordingIntentProvider(
            VoiceInterpretationOutcome.Interpreted(StructuredEmergencyIntent(false, null, 0f, "", false)),
        )
        val reporter = RecordingReporter()
        val coordinator = coordinatorFor(
            events = flowOf(TranscriptionEvent.Failure(TranscriptionFailureReason.NO_SPEECH_DETECTED)),
            intentProvider = intentProvider,
            reporter = reporter,
        )

        val results = coordinator.listenForWakePhrase().toList()

        assertTrue(intentProvider.transcriptsSeen.isEmpty())
        assertTrue(results.isEmpty())
    }
}
