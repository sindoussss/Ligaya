package com.ligaya.feature.companion

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Visual design, screen 4 (Listening): each state says what's really happening, and the mic and close work. */
@RunWith(AndroidJUnit4::class)
class VoiceListeningScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var micTaps = 0
    private var closes = 0

    private fun show(state: ListeningState, level: Float = 0.5f) {
        composeTestRule.setContent {
            VoiceListeningScreen(state = state, inputLevel = level, onMicTap = { micTaps++ }, onClose = { closes++ })
        }
    }

    @Test
    fun listeningShowsTheDesignCopyAndTheMicStopsIt() {
        show(ListeningState.Listening)
        composeTestRule.onNodeWithText("Listening...").assertExists()
        composeTestRule.onNodeWithText("I'm here. Just say what you need.").assertExists()

        composeTestRule.onNodeWithContentDescription("Stop listening").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, micTaps)
    }

    @Test
    fun pausedInvitesYouToTalk() {
        show(ListeningState.Paused, level = 0f)
        composeTestRule.onNodeWithText("Tap to talk").assertExists()
        composeTestRule.onNodeWithContentDescription("Start listening").performClick()
        assertEquals(1, micTaps)
    }

    @Test
    fun notHeardSaysSoInsteadOfPretendingToListen() {
        show(ListeningState.NotHeard, level = 0f)
        composeTestRule.onNodeWithText("I didn't catch that").assertExists()
        composeTestRule.onNodeWithText("Listening...").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Start listening").assertExists()
    }

    @Test
    fun blockedMicrophoneIsExplainedAndTheMicLeadsToSettings() {
        show(ListeningState.MicBlocked, level = 0f)
        composeTestRule.onNodeWithText("Microphone is off").assertExists()
        composeTestRule.onNodeWithText("Allow microphone access in Settings", substring = true).assertExists()
        composeTestRule.onNodeWithContentDescription("Open settings to allow the microphone").performClick()
        assertEquals(1, micTaps)
    }

    @Test
    fun closeReachesItsCallback() {
        show(ListeningState.Listening)
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun listeningScreenHasNoAccessibilityFrameworkFindings() {
        show(ListeningState.Listening)
        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
