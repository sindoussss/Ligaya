package com.ligaya.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design screen 12. Covers the behaviour the carousel's own design introduces — that the CTA
 * advances rather than exiting until the last page, and that Skip always leaves immediately —
 * since NavigationRouteReachabilityTest's generic "click the title, then Back" walk no longer fits
 * this route (see its own comment on why Onboarding is excluded there).
 *
 * Asserted through content rather than page index: the pager's index is an implementation detail,
 * but "the user is still being shown intro copy and hasn't been dropped into setup yet" is the
 * actual contract.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingIntroScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun opensOnTheFirstPageWithTheAdvanceLabelNotTheFinalOne() {
        composeTestRule.setContent {
            OnboardingIntroScreen(onGetStarted = {}, onSkip = {})
        }

        composeTestRule.onNodeWithText("I'm your\nFilipino AI\nassistant.").assertExists()
        composeTestRule.onNodeWithText(
            "I'm here to help with school, home, safety, and more. Just talk to me, I'm here!",
        ).assertExists()
        composeTestRule.onNodeWithText("Next").assertExists()
        composeTestRule.onNodeWithContentDescription("Page 1 of 4").assertExists()
    }

    /**
     * The CTA walks the pages and only leaves on the last one — so the label change is not
     * cosmetic, it is the difference between advancing and exiting. Asserting [started] stays
     * false through every advance is what would catch a regression where "Next" started
     * dropping people into setup early.
     */
    @Test
    fun nextAdvancesThroughEveryPageAndOnlyThenOffersGetStarted() {
        var started = false
        composeTestRule.setContent {
            OnboardingIntroScreen(onGetStarted = { started = true }, onSkip = {})
        }

        // Four pages: three advances land on the last one.
        repeat(3) {
            composeTestRule.onNodeWithText("Next").performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithContentDescription("Page 4 of 4").assertExists()

        assertFalse("advancing must never itself leave onboarding", started)
        composeTestRule.onNodeWithText("Get Started").assertExists()

        composeTestRule.onNodeWithText("Get Started").performClick()
        assertTrue(started)
    }

    @Test
    fun skipLeavesImmediatelyFromTheFirstPage() {
        var skipped = false
        composeTestRule.setContent {
            OnboardingIntroScreen(onGetStarted = {}, onSkip = { skipped = true })
        }

        composeTestRule.onNodeWithText("Skip").performClick()

        assertTrue(skipped)
    }

    @Test
    fun theSecondPageIsTheOneAboutTheWakePhrase() {
        // Proves the pager actually moves rather than the label alone changing, and pins the one page whose
        // copy makes a promise about how the app behaves.
        composeTestRule.setContent {
            OnboardingIntroScreen(onGetStarted = {}, onSkip = {})
        }

        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Your voice\nstarts the call\nfor help.").assertExists()
        composeTestRule.onNodeWithContentDescription("Page 2 of 4").assertExists()
    }
}
