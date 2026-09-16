package com.ligaya.feature.companion

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.ai.CompanionResponseProvider
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.voice.EmergencyStatusMessage
import com.ligaya.core.voice.SpeechOutput
import com.ligaya.core.voice.SpeechTranscriber
import com.ligaya.core.voice.VoiceCaptureCoordinator
import com.ligaya.core.ai.ValidatedSpeech
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 39's own acceptance criterion at the screen level: "UI test completing a conversation
 * using only the text-input fallback." Mirrors feature-companion's own coordinator-level
 * EmergencyCompanionCoordinatorTextFallbackTest (a poisoned VoiceCaptureCoordinator that fails the
 * test if collected) one layer up, proving the *screen* drives a full turn through
 * [EmergencyCompanionScreen]'s text input row and Send button alone, never through voice capture.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyCompanionScreenTextFallbackTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class GrantedPermissionChecker : PermissionChecker {
        override fun currentState(permission: String) = PermissionState.Granted
    }

    private class RecordingSpeechOutput : SpeechOutput {
        override suspend fun speak(message: EmergencyStatusMessage) {}
        override suspend fun speak(speech: ValidatedSpeech) {}
    }

    /** A capture coordinator whose Flow fails the test the moment anything collects it — the
     *  screen's text fallback row must never reach this at all. */
    private fun poisonedCaptureCoordinator() = VoiceCaptureCoordinator(
        SpeechTranscriber { flow { throw AssertionError("the text fallback row must never use voice capture") } },
        GrantedPermissionChecker(),
    )

    private fun snapshotProviderFor(subsystems: ConcurrentSubsystemStates) =
        EmergencySnapshotProvider { EmergencySnapshot(state = EmergencyState.EMERGENCY_ACTIVE, subsystems = subsystems) }

    private val safeReply = "I'm right here with you. Can you tell me more?"

    @Test
    fun completingAConversationThroughTheTextInputAloneNeverTouchesVoiceCapture() {
        val coordinator = EmergencyCompanionCoordinator(
            poisonedCaptureCoordinator(),
            CompanionResponseProvider { _, _ -> safeReply },
            RecordingSpeechOutput(),
            snapshotProviderFor(ConcurrentSubsystemStates(unified911 = Unified911FlowState.Succeeded)),
        )

        composeTestRule.setContent {
            EmergencyCompanionScreen(coordinator = coordinator)
        }

        composeTestRule.onNodeWithText("Online").assertExists()

        composeTestRule.onNodeWithTag("companionTextInput").performTextInput("Tulong, sunog!")
        composeTestRule.onNodeWithTag("companionSendButton").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(safeReply).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Tulong, sunog!").assertExists()
        composeTestRule.onNodeWithText(safeReply).assertExists()
        composeTestRule.onNodeWithText("Online").assertExists()
    }
}
