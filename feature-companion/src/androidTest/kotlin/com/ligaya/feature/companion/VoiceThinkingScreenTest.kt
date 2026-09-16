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

/** Visual design, screen 5 (Thinking): the design copy, and honest wording when smart replies are off or the wait is long. */
@RunWith(AndroidJUnit4::class)
class VoiceThinkingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsTheDesignCopy() {
        composeTestRule.setContent { VoiceThinkingScreen(aiAvailable = true) }
        composeTestRule.onNodeWithText("Thinking...").assertExists()
        composeTestRule.onNodeWithText("Let me think about that for a moment.").assertExists()
    }

    @Test
    fun saysSoWhenSmartRepliesAreOff() {
        composeTestRule.setContent { VoiceThinkingScreen(aiAvailable = false) }
        composeTestRule.onNodeWithText("Thinking...").assertExists()
        composeTestRule.onNodeWithText("Smart replies are off", substring = true).assertExists()
        composeTestRule.onNodeWithText("Let me think about that for a moment.").assertDoesNotExist()
    }

    @Test
    fun aLongWaitIsAcknowledged() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { VoiceThinkingScreen(aiAvailable = true) }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("Let me think about that for a moment.").assertExists()

        composeTestRule.mainClock.advanceTimeBy(THINKING_SLOW_AFTER_MILLIS + 500)
        composeTestRule.onNodeWithText("Still thinking", substring = true).assertExists()
    }

    @Test
    fun thinkingScreenHasNoAccessibilityFrameworkFindings() {
        composeTestRule.setContent { VoiceThinkingScreen(aiAvailable = true) }
        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
