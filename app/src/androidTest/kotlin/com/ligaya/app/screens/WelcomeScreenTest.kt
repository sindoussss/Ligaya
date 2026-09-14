package com.ligaya.app.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.designsystem.LigayaSpacing
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Visual design, screen 1: the welcome screen shows the brand lockup and Get Started works. */
@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun welcomeShowsTheBrandLockup() {
        composeTestRule.setContent { WelcomeScreen(onGetStarted = {}) }

        composeTestRule.onNodeWithText("Ligaya").assertExists()
        composeTestRule.onNodeWithText("More than an assistant.\nA kaibigan, always.").assertExists()
        composeTestRule.onNodeWithText("Get Started").assertExists()
    }

    @Test
    fun getStartedHandsOffAndIsAComfortableTouchTarget() {
        var started = false
        composeTestRule.setContent { WelcomeScreen(onGetStarted = { started = true }) }

        // The pill is clickable, so its label merges into it: this node's bounds are the whole pill.
        composeTestRule.onNodeWithText("Get Started").assertHeightIsAtLeast(LigayaSpacing.minTouchTarget)
        composeTestRule.onNodeWithText("Get Started").performClick()

        assertTrue("Get Started must hand off to the next screen", started)
    }
}
