package com.ligaya.feature.companion

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
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

/**
 * Visual design, screen 8 ("Oops..."): the design copy, and — sections 21 and 23 — a card that names the cause the
 * app actually established, never guessing at the network.
 */
@RunWith(AndroidJUnit4::class)
class VoiceTroubleScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var retries = 0

    private fun show(reason: TroubleReason) {
        composeTestRule.setContent { VoiceTroubleScreen(reason = reason, onTryAgain = { retries++ }) }
    }

    @Test
    fun showsTheDesignCopy() {
        show(TroubleReason.Offline)
        composeTestRule.onNodeWithText("Oops...").assertExists()
        composeTestRule.onNodeWithText("I'm having trouble", substring = true).assertExists()
    }

    @Test
    fun beingOfflineIsNamedAsTheCause() {
        show(TroubleReason.Offline)
        composeTestRule.onNodeWithText("No internet connection").assertExists()
        composeTestRule.onNodeWithText("Check your connection and try again.").assertExists()
    }

    @Test
    fun anUnreachableAssistantNeverBlamesTheConnection() {
        show(TroubleReason.AssistantUnavailable)
        composeTestRule.onNodeWithText("No internet connection").assertDoesNotExist()
        composeTestRule.onNodeWithText("I couldn't reach my assistant").assertExists()
        composeTestRule.onNodeWithText("SOS and emergency calling still work", substring = true).assertExists()
    }

    @Test
    fun aBlockedReplySaysWhyRatherThanBlamingTheNetwork() {
        show(TroubleReason.ReplyBlocked)
        composeTestRule.onNodeWithText("No internet connection").assertDoesNotExist()
        composeTestRule.onNodeWithText("I couldn't answer that safely").assertExists()
    }

    @Test
    fun tryAgainReachesItsCallbackAndIsAFullTouchTarget() {
        show(TroubleReason.Offline)
        composeTestRule.onNodeWithText("Try Again").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun troubleScreenHasNoAccessibilityFrameworkFindings() {
        show(TroubleReason.AssistantUnavailable)
        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
