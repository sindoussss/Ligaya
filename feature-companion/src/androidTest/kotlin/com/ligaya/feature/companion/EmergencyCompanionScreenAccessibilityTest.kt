package com.ligaya.feature.companion

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.ai.CompanionResponseProvider
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
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 45's real Accessibility Test Framework pass on the Emergency Companion screen — after a
 * real turn through the text-input fallback, so the transcript is non-empty and the check covers
 * the screen's real, populated state, not just its empty starting layout.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyCompanionScreenAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class GrantedPermissionChecker : PermissionChecker {
        override fun currentState(permission: String) = PermissionState.Granted
    }

    private class RecordingSpeechOutput : SpeechOutput {
        override suspend fun speak(message: EmergencyStatusMessage) = SpeechResult.SPOKEN
        override suspend fun speak(speech: ValidatedSpeech) = SpeechResult.SPOKEN
    }

    private fun poisonedCaptureCoordinator() = VoiceCaptureCoordinator(
        SpeechTranscriber { flow { throw AssertionError("not used by this test") } },
        GrantedPermissionChecker(),
    )

    private val safeReply = "I'm right here with you. Can you tell me more?"

    @Test
    fun emergencyCompanionScreenHasNoAccessibilityFrameworkFindingsWithAPopulatedTranscript() {
        val coordinator = EmergencyCompanionCoordinator(
            poisonedCaptureCoordinator(),
            CompanionResponseProvider { _, _ -> safeReply },
            RecordingSpeechOutput(),
            EmergencySnapshotProvider {
                EmergencySnapshot(
                    state = EmergencyState.EMERGENCY_ACTIVE,
                    subsystems = ConcurrentSubsystemStates(unified911 = Unified911FlowState.Succeeded),
                )
            },
        )

        composeTestRule.setContent {
            EmergencyCompanionScreen(coordinator = coordinator)
        }

        composeTestRule.onNodeWithTag("companionTextInput").performTextInput("Tulong, sunog!")
        composeTestRule.onNodeWithTag("companionSendButton").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(safeReply).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
