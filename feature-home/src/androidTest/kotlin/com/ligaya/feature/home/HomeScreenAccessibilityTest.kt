package com.ligaya.feature.home

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 45's real Accessibility Test Framework pass on the Home screen — "zero critical findings
 * on a TalkBack traversal" made automated rather than only a self-authored contentDescription
 * sweep. `enableAccessibilityChecks()` runs Google's own Accessibility Test Framework against the
 * real rendered semantics tree and throws on any Error-level result (missing labels, insufficient
 * contrast, undersized touch targets, and more — not just the checks this codebase's own
 * contentDescription/touch-target audit already knew to look for).
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class NoOpEmergencyController : EmergencyController {
        override suspend fun triggerSos(): SosResult = SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)
        override suspend fun markSafe(): Result<EmergencyState> = Result.success(EmergencyState.USER_MARKED_SAFE)
        override suspend fun retryCall(): Result<EmergencyState> = Result.success(EmergencyState.EMERGENCY_ACTIVE)
        override fun observeSnapshot(): Flow<EmergencySnapshot?> = flowOf(null)
    }

    @Test
    fun homeScreenHasNoAccessibilityFrameworkFindings() {
        composeTestRule.setContent {
            HomeScreen(
                emergencyController = NoOpEmergencyController(),
                otherDestinations = listOf(NavigableDestination("onboarding", "Onboarding")),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                voiceAiUnavailable = MutableStateFlow(false),
            )
        }

        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }

    /** Step 53 audit follow-up: the AI-unavailable banner is new, conditionally-rendered content
     *  the test above never exercises (its flow is fixed to false) — a real pass specifically
     *  with it visible, not just the steady-state screen. */
    @Test
    fun homeScreenHasNoAccessibilityFrameworkFindingsWithTheAiUnavailableBannerVisible() {
        composeTestRule.setContent {
            HomeScreen(
                emergencyController = NoOpEmergencyController(),
                otherDestinations = listOf(NavigableDestination("onboarding", "Onboarding")),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                voiceAiUnavailable = MutableStateFlow(true),
            )
        }

        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
