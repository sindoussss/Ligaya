package com.ligaya.feature.companion

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.ai.CompanionResponseProvider
import com.ligaya.core.ai.GeminiCompanionResponseProvider
import com.ligaya.core.ai.ValidatedSpeech
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.voice.EmergencyStatusMessage
import com.ligaya.core.voice.SpeechOutput
import com.ligaya.core.voice.SpeechResult
import com.ligaya.core.voice.SpeechTranscriber
import com.ligaya.core.voice.VoiceCaptureCoordinator
import com.ligaya.designsystem.components.LigayaTab
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design, screen 3 (Chat): greeting, honest status and notes, the answered tick, send enablement, and
 * the header/menu/tab callbacks.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyCompanionChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class GrantedPermissionChecker : PermissionChecker {
        override fun currentState(permission: String) = PermissionState.Granted
    }

    private class SilentSpeechOutput : SpeechOutput {
        override suspend fun speak(message: EmergencyStatusMessage) = SpeechResult.SPOKEN
        override suspend fun speak(speech: ValidatedSpeech) = SpeechResult.SPOKEN
    }

    private fun coordinator(reply: String) = EmergencyCompanionCoordinator(
        VoiceCaptureCoordinator(SpeechTranscriber { flow { throw AssertionError("chat typing never uses voice") } }, GrantedPermissionChecker()),
        CompanionResponseProvider { _, _ -> reply },
        SilentSpeechOutput(),
        // No subsystem has succeeded, so any claim that 911 was contacted is unverified and blocked.
        EmergencySnapshotProvider { EmergencySnapshot() },
    )

    private fun send(text: String) {
        composeTestRule.onNodeWithTag("companionTextInput").performTextInput(text)
        composeTestRule.onNodeWithTag("companionSendButton").performClick()
    }

    private fun waitForText(text: String, substring: Boolean = false) = composeTestRule.waitUntil(5_000) {
        composeTestRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun greetsByNameAndFallsBackToKaibigan() {
        composeTestRule.setContent { EmergencyCompanionScreen(coordinator = coordinator("Sure!"), userName = "John Daniel") }
        composeTestRule.onNodeWithText("Hi John Daniel!", substring = true).assertExists()
        composeTestRule.onNodeWithText("How can I help you today?", substring = true).assertExists()
    }

    @Test
    fun greetingWithoutANameUsesKaibigan() {
        composeTestRule.setContent { EmergencyCompanionScreen(coordinator = coordinator("Sure!")) }
        composeTestRule.onNodeWithText("Hi kaibigan!", substring = true).assertExists()
    }

    @Test
    fun duringAnEmergencyTheGreetingIsCalmAndOnTask() {
        composeTestRule.setContent { EmergencyCompanionScreen(coordinator = coordinator("Okay."), inEmergency = true) }
        composeTestRule.onNodeWithText("I'm here with you.", substring = true).assertExists()
        composeTestRule.onNodeWithText("How can I help you today?", substring = true).assertDoesNotExist()
    }

    @Test
    fun withoutGeminiTheStatusAndNoticeSayRepliesAreLimited() {
        composeTestRule.setContent { EmergencyCompanionScreen(coordinator = coordinator("Sure!"), aiAvailable = false) }
        composeTestRule.onNodeWithText("Limited replies").assertExists()
        composeTestRule.onNodeWithText("Online").assertDoesNotExist()
        composeTestRule.onNodeWithText("Smart replies are off right now", substring = true).assertExists()
    }

    @Test
    fun aFallbackReplyAlsoMarksRepliesAsLimited() {
        composeTestRule.setContent { EmergencyCompanionScreen(coordinator = coordinator(GeminiCompanionResponseProvider.SAFE_FALLBACK_RESPONSE)) }
        composeTestRule.onNodeWithText("Online").assertExists()

        send("Hello")
        waitForText(GeminiCompanionResponseProvider.SAFE_FALLBACK_RESPONSE)

        composeTestRule.onNodeWithText("Limited replies").assertExists()
    }

    @Test
    fun aBlockedReplyLeavesANoteInsteadOfSilence() {
        composeTestRule.setContent { EmergencyCompanionScreen(coordinator = coordinator("911 has been contacted.")) }

        send("Did you call for help?")
        waitForText("couldn't answer that safely", substring = true)

        composeTestRule.onNodeWithText("911 has been contacted.").assertDoesNotExist()
    }

    @Test
    fun yourMessageShowsTheAnsweredTickOnlyOnceLigayaReplies() {
        composeTestRule.setContent { EmergencyCompanionScreen(coordinator = coordinator("Of course! What subject is it for?")) }
        composeTestRule.onAllNodesWithContentDescription("Answered by Ligaya", substring = true).assertCountEquals(0)

        send("Can you help me with my school project?")
        waitForText("Of course! What subject is it for?")

        composeTestRule.onNodeWithText("Can you help me with my school project?").assertExists()
        composeTestRule.onNodeWithContentDescription("Answered by Ligaya", substring = true).assertExists()
    }

    @Test
    fun sendIsDisabledUntilThereIsSomethingToSend() {
        composeTestRule.setContent { EmergencyCompanionScreen(coordinator = coordinator("Sure!")) }
        val sendButton = composeTestRule.onNodeWithTag("companionSendButton")
        sendButton.assertIsNotEnabled().assertHeightIsAtLeast(48.dp)

        composeTestRule.onNodeWithTag("companionTextInput").performTextInput("   ")
        sendButton.assertIsNotEnabled()

        composeTestRule.onNodeWithTag("companionTextInput").performTextInput("hi")
        sendButton.assertIsEnabled()
    }

    @Test
    fun backTabsAndMenuActionsReachTheirCallbacks() {
        var back = 0
        val tabs = mutableListOf<LigayaTab>()
        var voice = 0
        var sos = 0
        composeTestRule.setContent {
            EmergencyCompanionScreen(
                coordinator = coordinator("Sure!"),
                onBack = { back++ },
                onSelectTab = { tabs += it },
                onStartVoice = { voice++ },
                onSos = { sos++ },
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.onNodeWithText("Circle").performClick()
        composeTestRule.onNodeWithText("Profile").performClick()

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Talk to Ligaya").performClick()
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Emergency SOS").performClick()

        assertEquals(1, back)
        // Chat is the current tab, so tapping it does nothing.
        assertEquals(listOf(LigayaTab.Home, LigayaTab.Circle, LigayaTab.Profile), tabs)
        assertEquals(1, voice)
        assertTrue(sos == 1)
    }
}
