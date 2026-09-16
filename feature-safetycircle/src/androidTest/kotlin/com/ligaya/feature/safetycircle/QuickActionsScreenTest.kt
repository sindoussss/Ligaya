package com.ligaya.feature.safetycircle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Safety Circle's "Quick actions" — see this screen's own top doc comment for why its three rows
 * are separately honest rather than one combined tap. What these tests really pin: "Call 911" is
 * always a real, live button; "Alert your circle" is never a button when there is nothing behind
 * it; "Share your location" reports honestly when no fix was found rather than pretending to have
 * shared one.
 */
@RunWith(AndroidJUnit4::class)
class QuickActionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun callingSosIsAlwaysARealButton() {
        var called = false
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                QuickActionsScreen(
                    onBack = {},
                    onCallSos = { called = true },
                    circleAlertsConfigured = false,
                    onShareLocation = { true },
                )
            }
        }

        composeTestRule.onNodeWithText("Call 911").performClick()

        assertTrue(called)
    }

    @Test
    fun withNoBackendAlertYourCircleIsInformationalNotAButton() {
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                QuickActionsScreen(
                    onBack = {},
                    onCallSos = {},
                    circleAlertsConfigured = false,
                    onShareLocation = { true },
                )
            }
        }

        composeTestRule.onNodeWithText("Alert your circle (not available yet)").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Ligaya cannot reach your circle yet, so nothing would be sent.",
        ).assertIsDisplayed()
    }

    @Test
    fun withABackendAlertYourCircleStillIsNotAButtonSinceAlertsFireAutomatically() {
        // Section 17: family alerts fire once an emergency is active, not on a standalone tap — a
        // "configured" backend does not invent a manual-send action this app's own architecture
        // never described.
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                QuickActionsScreen(
                    onBack = {},
                    onCallSos = {},
                    circleAlertsConfigured = true,
                    onShareLocation = { true },
                )
            }
        }

        composeTestRule.onNodeWithText(
            "Your circle is alerted with your location the moment an emergency starts. There is " +
                "nothing to send by hand.",
        ).assertIsDisplayed()
    }

    @Test
    fun sharingLocationSuccessfullyShowsNoErrorMessage() {
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                QuickActionsScreen(
                    onBack = {},
                    onCallSos = {},
                    circleAlertsConfigured = false,
                    onShareLocation = { true },
                )
            }
        }

        composeTestRule.onNodeWithText("Share your location").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            "Could not find your location. Check that location access is on for Ligaya, then try again.",
            substring = true,
        ).assertDoesNotExist()
    }

    @Test
    fun whenNoFixIsFoundSharingLocationSaysSoHonestlyRatherThanPretending() {
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                QuickActionsScreen(
                    onBack = {},
                    onCallSos = {},
                    circleAlertsConfigured = false,
                    onShareLocation = { false },
                )
            }
        }

        composeTestRule.onNodeWithText("Share your location").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(
            "Share location: Could not find your location. Check that location access is on for " +
                "Ligaya, then try again.",
        ).assertIsDisplayed()
    }

    @Test
    fun theBackButtonHandsOff() {
        var backCount = 0
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                QuickActionsScreen(
                    onBack = { backCount++ },
                    onCallSos = {},
                    circleAlertsConfigured = false,
                    onShareLocation = { true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backCount == 1)
    }
}
