package com.ligaya.feature.companion

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design, screen 7 (Speaking): the design copy while she reads a reply out, and — section 23 — no claim of
 * speaking on a phone whose voice engine said nothing, where the reply is shown as text instead.
 */
@RunWith(AndroidJUnit4::class)
class VoiceSpeakingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val reply = "Of course! I'd be happy to help with your school project."

    @Test
    fun speakingAloudShowsTheDesignCopy() {
        composeTestRule.setContent { VoiceSpeakingScreen(reply = reply, aloud = true) }
        composeTestRule.onNodeWithText("Speaking...").assertExists()
        composeTestRule.onNodeWithText("Here's what I found for you.").assertExists()
    }

    @Test
    fun aSilentPhoneNeverClaimsSheIsSpeaking() {
        composeTestRule.setContent { VoiceSpeakingScreen(reply = reply, aloud = false) }
        composeTestRule.onNodeWithText("Speaking...").assertDoesNotExist()
        composeTestRule.onNodeWithText("Here's what I found for you.").assertDoesNotExist()
        composeTestRule.onNodeWithText("I can't speak out loud right now").assertExists()
    }

    @Test
    fun whenNothingIsAudibleTheReplyIsShownAsText() {
        composeTestRule.setContent { VoiceSpeakingScreen(reply = reply, aloud = false) }
        composeTestRule.onNodeWithText(reply).assertExists()
    }

    @Test
    fun whileSpeakingTheReplyIsHeardRatherThanPrinted() {
        composeTestRule.setContent { VoiceSpeakingScreen(reply = reply, aloud = true) }
        composeTestRule.onNodeWithText(reply).assertDoesNotExist()
    }

    @Test
    fun speakingScreenHasNoAccessibilityFrameworkFindings() {
        composeTestRule.setContent { VoiceSpeakingScreen(reply = reply, aloud = true) }
        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
