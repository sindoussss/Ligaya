package com.ligaya.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design screen 2. Covers the behaviour the carousel's own design introduces — that the CTA
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

        composeTestRule.onNodeWithText("Be ready,\neven before\nyou need to be.").assertExists()
        composeTestRule.onNodeWithText("Continue").assertExists()
    }

    /**
     * The CTA walks the pages and only leaves on the last one — so the label change is not
     * cosmetic, it is the difference between advancing and exiting. Asserting [started] stays
     * false through every advance is what would catch a regression where "Continue" started
     * dropping people into setup early.
     */
    @Test
    fun continueAdvancesThroughEveryPageAndOnlyThenOffersGetStarted() {
        var started = false
        composeTestRule.setContent {
            OnboardingIntroScreen(onGetStarted = { started = true }, onSkip = {})
        }

        // Four pages: three advances land on the last one.
        repeat(3) {
            composeTestRule.onNodeWithText("Continue").performClick()
            composeTestRule.waitForIdle()
        }

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
}
