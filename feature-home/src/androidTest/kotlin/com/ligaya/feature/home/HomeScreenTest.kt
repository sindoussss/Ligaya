package com.ligaya.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.LigayaSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home (visual design, screen 2): the header SOS pill still triggers Step 13's flow directly, every
 * destination stays reachable (menu and tab bar), the ask bar and Voice shortcut hand off to Ligaya,
 * and the live voice/AI-unavailable states still show.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

    private fun setHome(
        fake: EmergencyController = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)),
        otherDestinations: List<NavigableDestination> = emptyList(),
        onSosActivated: () -> Unit = {},
        onNavigateToSafetyCircle: () -> Unit = {},
        onNavigateToCompanion: () -> Unit = {},
        onNavigateToRoute: (String) -> Unit = {},
        voicePhase: StateFlow<VoicePipelinePhase> = idleVoicePhase,
        voiceAiUnavailable: StateFlow<Boolean> = noAiUnavailableNotice,
        userName: String? = null,
        onAskText: (String) -> Unit = {},
        onStartVoice: () -> Unit = {},
        onNavigateToSafetyCircleTab: () -> Unit = {},
        onNavigateToProfile: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = otherDestinations,
                onSosActivated = onSosActivated,
                onNavigateToSafetyCircle = onNavigateToSafetyCircle,
                onNavigateToCompanion = onNavigateToCompanion,
                onNavigateToRoute = onNavigateToRoute,
                voicePhase = voicePhase,
                voiceAiUnavailable = voiceAiUnavailable,
                userName = userName,
                onAskText = onAskText,
                onStartVoice = onStartVoice,
                onNavigateToSafetyCircleTab = onNavigateToSafetyCircleTab,
                onNavigateToProfile = onNavigateToProfile,
            )
        }
    }

    @Test
    fun tappingSosControlCallsTheControllerAndReportsActivation() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var activated = false
        setHome(fake = fake, onSosActivated = { activated = true })

        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { activated }

        assertEquals(1, fake.callCount)
        assertTrue(activated)
    }

    @Test
    fun tappingSosControlWhenAlreadyInProgressStillReportsActivationRatherThanBlockingTheUser() {
        val fake = FakeEmergencyController(SosResult.AlreadyInProgress(EmergencyState.EMERGENCY_ACTIVE))
        var activated = false
        setHome(fake = fake, onSosActivated = { activated = true })

        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { activated }

        assertTrue(activated)
    }

    @Test
    fun safetyCircleIsReachableFromTheMenuWithoutCallingTheController() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var navigated = false
        setHome(fake = fake, onNavigateToSafetyCircle = { navigated = true })

        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.onNodeWithText("Safety Circle").performClick()

        assertTrue(navigated)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun chatTabOpensTheCompanionWithoutCallingTheController() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var navigated = false
        setHome(fake = fake, onNavigateToCompanion = { navigated = true })

        composeTestRule.onNodeWithText("Chat").performClick()

        assertTrue(navigated)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun safetyCircleAndProfileTabsNavigate() {
        var circle = false
        var profile = false
        setHome(onNavigateToSafetyCircleTab = { circle = true }, onNavigateToProfile = { profile = true })

        // "Circle", not "Tools": the architecture's UX layer has no Tools surface, and this tab is the
        // Safety Circle of sections 6 and 8.
        composeTestRule.onNodeWithText("Circle").performClick()
        composeTestRule.onNodeWithText("Profile").performClick()

        assertTrue(circle)
        assertTrue(profile)
    }

    @Test
    fun otherDestinationsAreListedInTheMenuAndNavigable() {
        var navigatedRoute: String? = null
        setHome(
            otherDestinations = listOf(NavigableDestination("onboarding", "Onboarding")),
            onNavigateToRoute = { navigatedRoute = it },
        )

        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.onNodeWithText("Onboarding").performClick()

        assertEquals("onboarding", navigatedRoute)
    }

    @Test
    fun greetingUsesTheProfileNameAndFallsBackToKaibigan() {
        val name = MutableStateFlow<String?>(null)
        composeTestRule.setContent {
            val current = name.collectAsStateValue()
            HomeScreen(
                emergencyController = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)),
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = noAiUnavailableNotice,
                userName = current,
                // Held at mid-morning: this test is about the name, not the hour (HomeGreetingTest owns that).
                greetingAt = java.time.LocalTime.of(9, 0),
            )
        }

        composeTestRule.onNodeWithText("Hello,").assertExists()
        composeTestRule.onNodeWithText("kaibigan!").assertExists()

        name.value = "John Daniel"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("John Daniel!").assertExists()
    }

    @Test
    fun typedQuestionIsSentToLigayaAndTheBarClears() {
        var asked: String? = null
        setHome(onAskText = { asked = it })

        composeTestRule.onNode(hasSetTextAction()).performTextInput("  Is it safe to cross the flood?  ")
        composeTestRule.onNode(hasSetTextAction()).performImeAction()

        assertEquals("Is it safe to cross the flood?", asked)
        composeTestRule.onNodeWithText("Ask me anything...").assertExists()
    }

    @Test
    fun blankQuestionIsNotSent() {
        var asked = false
        setHome(onAskText = { asked = true })

        composeTestRule.onNode(hasSetTextAction()).performTextInput("   ")
        composeTestRule.onNode(hasSetTextAction()).performImeAction()

        assertTrue(!asked)
    }

    @Test
    fun micButtonAndVoiceChipStartAVoiceTurn() {
        var voiceTurns = 0
        setHome(onStartVoice = { voiceTurns++ })

        composeTestRule.onNodeWithContentDescription("Talk to Ligaya").performClick()
        composeTestRule.onNodeWithText("Voice").performClick()

        assertEquals(2, voiceTurns)
    }

    /** SOS lives in the header, so on the smallest supported phone it is on screen without scrolling. */
    @Test
    fun sosControlStaysFullyOnScreenOnASmallPhone() {
        val screenHeight = 568.dp
        composeTestRule.setContent {
            Box(Modifier.requiredSize(320.dp, screenHeight)) {
                HomeScreen(
                    emergencyController = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)),
                    otherDestinations = emptyList(),
                    onSosActivated = {},
                    onNavigateToSafetyCircle = {},
                    onNavigateToCompanion = {},
                    onNavigateToRoute = {},
                    voicePhase = idleVoicePhase,
                    voiceAiUnavailable = noAiUnavailableNotice,
                )
            }
        }

        val sos = composeTestRule.onNodeWithContentDescription("Send SOS emergency alert")
        sos.assertIsDisplayed()
        val bottom = sos.getUnclippedBoundsInRoot().bottom
        assertTrue("SOS bottom $bottom must be within the $screenHeight screen", bottom <= screenHeight)
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun tappableControlsMeetTheMinimumTouchTargetSize() {
        setHome()

        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert").assertHeightIsAtLeast(LigayaSpacing.minTouchTarget)
        composeTestRule.onNodeWithContentDescription("Talk to Ligaya").assertHeightIsAtLeast(LigayaSpacing.minTouchTarget)
        listOf("Voice", "Text", "Home", "Chat", "Circle", "Profile").forEach {
            composeTestRule.onNodeWithText(it).assertHeightIsAtLeast(LigayaSpacing.minTouchTarget)
        }
    }

    @Test
    fun voiceIndicatorReflectsTheRealListeningPhaseNotAStaticPlaceholder() {
        setHome(voicePhase = MutableStateFlow(VoicePipelinePhase.LISTENING))

        composeTestRule.onNodeWithContentDescription("Voice assistant listening").assertExists()
        composeTestRule.onNodeWithText("Listening for \"Ligaya\"…").assertExists()
    }

    @Test
    fun aiUnavailableBannerIsHiddenByDefaultAndAppearsOnlyWhenTheFlowSaysSo() {
        val aiUnavailable = MutableStateFlow(false)
        setHome(voiceAiUnavailable = aiUnavailable)

        val bannerText = "Voice assistant unavailable right now. Use the SOS button instead."
        composeTestRule.onNodeWithText(bannerText).assertDoesNotExist()

        aiUnavailable.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(bannerText).assertExists()

        aiUnavailable.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(bannerText).assertDoesNotExist()
    }
    @Test
    fun theEveningHomeGreetsTheNightAndSaysGoodnight() {
        // Visual design screen 11: the same Home after six, which is the only thing that separates it from
        // screen 2 besides the dark palette.
        composeTestRule.setContent {
            HomeScreen(
                emergencyController = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)),
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
                voicePhase = idleVoicePhase,
                voiceAiUnavailable = noAiUnavailableNotice,
                userName = "John Daniel",
                greetingAt = java.time.LocalTime.of(20, 15),
            )
        }

        composeTestRule.onNodeWithText("Hello,").assertExists()
        composeTestRule.onNodeWithText("John Daniel!").assertExists()
        composeTestRule.onNodeWithText("I'm Ligaya. Rest well, I'm always here when you need me.").assertExists()
        // SOS is on screen at night exactly as it is by day. Asked for by the header pill's own
        // description rather than the word "SOS", which the tab bar's SOS button also shows.
        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert").assertExists()
    }

}

@Composable
private fun <T> StateFlow<T>.collectAsStateValue(): T = collectAsState().value
