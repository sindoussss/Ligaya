package com.ligaya.feature.emergencyactive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.voice.VoicePipelinePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 38's own "instrumented test verifying indicator state transitions match actual pipeline
 * callbacks" — the UI-binding half. feature-companion's own EmergencyCompanionCoordinatorPhaseTest
 * proves the coordinator emits the right phase at the right pipeline stage; this test proves the
 * screen's rendered indicator actually tracks a changing phase source, updating live rather than
 * only reflecting whatever value it happened to be composed with.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyActiveScreenVoiceIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class NoOpEmergencyController : EmergencyController {
        override suspend fun triggerSos(): SosResult = SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)
        override suspend fun markSafe(): Result<EmergencyState> = Result.success(EmergencyState.USER_MARKED_SAFE)
        override suspend fun retryCall(): Result<EmergencyState> = Result.success(EmergencyState.EMERGENCY_ACTIVE)
        override fun observeSnapshot(): Flow<EmergencySnapshot?> = flowOf(null)
    }

    @Test
    fun voiceIndicatorTracksThePhaseFlowLiveThroughAFullTurn() {
        val phaseFlow = MutableStateFlow(VoicePipelinePhase.IDLE)

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = NoOpEmergencyController(),
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = phaseFlow,
                onMarkedSafe = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Voice assistant idle").assertIsDisplayed()

        phaseFlow.value = VoicePipelinePhase.LISTENING
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Voice assistant listening").assertIsDisplayed()

        phaseFlow.value = VoicePipelinePhase.PROCESSING
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Voice assistant processing").assertIsDisplayed()

        phaseFlow.value = VoicePipelinePhase.SPEAKING
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Voice assistant speaking").assertIsDisplayed()

        phaseFlow.value = VoicePipelinePhase.IDLE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Voice assistant idle").assertIsDisplayed()
    }
}
