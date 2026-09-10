package com.ligaya.feature.home

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.LigayaSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 36's acceptance criterion: "SOS control tap triggers Step 13's flow." Same fake-controller
 * pattern as app/screens/SosScreenTest.kt (Step 13) — no database, no foreground service, no
 * MainActivity launch needed to prove the control is wired to the controller at all.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Fixed IDLE/false — voice-state behavior itself is VoiceActivationCoordinatorTest's own
    // scope (core-voice); these tests only need HomeScreen to accept and render some value.
    private val idleVoicePhase = MutableStateFlow(VoicePipelinePhase.IDLE)
    private val noAiUnavailableNotice = MutableStateFlow(false)

    private class FakeEmergencyController(private val result: SosResult) : EmergencyController {
        var callCount = 0
            private set

        override suspend fun triggerSos(): SosResult {
            callCount++
            return result
        }

        override suspend fun markSafe(): Result<EmergencyState> = Result.success(EmergencyState.USER_MARKED_SAFE)

        override suspend fun retryCall(): Result<EmergencyState> = Result.success(EmergencyState.EMERGENCY_ACTIVE)

        override fun observeSnapshot(): Flow<EmergencySnapshot?> = flowOf(null)
    }

    @Test
    fun tappingSosControlCallsTheControllerAndReportsActivation() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var activated = false

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = { activated = true },
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = noAiUnavailableNotice,
            )
        }

        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { activated }

        assertEquals(1, fake.callCount)
        assertTrue(activated)
    }

    @Test
    fun tappingSosControlWhenAlreadyInProgressStillReportsActivationRatherThanBlockingTheUser() {
        val fake = FakeEmergencyController(SosResult.AlreadyInProgress(EmergencyState.EMERGENCY_ACTIVE))
        var activated = false

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = { activated = true },
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = noAiUnavailableNotice,
            )
        }

        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { activated }

        assertTrue(activated)
    }

    @Test
    fun tappingSafetyCircleCardNavigatesWithoutCallingTheController() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var navigated = false

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = { navigated = true },
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = noAiUnavailableNotice,
            )
        }

        composeTestRule.onNodeWithText("Safety Circle").performClick()

        assertTrue(navigated)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun tappingCompanionCardNavigatesWithoutCallingTheController() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var navigated = false

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = { navigated = true },
                onNavigateToRoute = {},
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = noAiUnavailableNotice,
            )
        }

        composeTestRule.onNodeWithText("Emergency Companion").performClick()

        assertTrue(navigated)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun otherDestinationsAreRenderedAndNavigable() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var navigatedRoute: String? = null

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = listOf(NavigableDestination("onboarding", "Onboarding")),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = { navigatedRoute = it },
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = noAiUnavailableNotice,
            )
        }

        composeTestRule.onNodeWithText("Onboarding").performClick()

        assertEquals("onboarding", navigatedRoute)
    }

    /** Step 45's accessibility fix: GlanceableCard's touch target must hold at the 48dp floor
     *  regardless of its text content's own incidental height, not just happen to clear it — see
     *  HomeScreen.kt's own comment on why an explicit heightIn was added. */
    @Test
    fun glanceableCardsMeetTheMinimumTouchTargetSize() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = noAiUnavailableNotice,
            )
        }

        composeTestRule.onNodeWithText("Safety Circle").assertHeightIsAtLeast(LigayaSpacing.minTouchTarget)
        composeTestRule.onNodeWithText("Emergency Companion").assertHeightIsAtLeast(LigayaSpacing.minTouchTarget)
    }

    // --- Step 53 audit follow-up: the always-on wake-word loop's own voice indicator on Home ---

    @Test
    fun voiceIndicatorReflectsTheRealListeningPhaseNotAStaticPlaceholder() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = MutableStateFlow(VoicePipelinePhase.LISTENING),
                voiceAiUnavailable = noAiUnavailableNotice,
            )
        }

        composeTestRule.onNodeWithContentDescription("Voice assistant listening").assertExists()
        composeTestRule.onNodeWithText("Listening for \"Ligaya\"…").assertExists()
    }

    @Test
    fun aiUnavailableBannerIsHiddenByDefaultAndAppearsOnlyWhenTheFlowSaysSo() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        val aiUnavailable = MutableStateFlow(false)

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = aiUnavailable,
            )
        }

        val bannerText = "Voice assistant unavailable right now — use the SOS button instead."
        composeTestRule.onNodeWithText(bannerText).assertDoesNotExist()

        aiUnavailable.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(bannerText).assertExists()

        aiUnavailable.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(bannerText).assertDoesNotExist()
    }
}
