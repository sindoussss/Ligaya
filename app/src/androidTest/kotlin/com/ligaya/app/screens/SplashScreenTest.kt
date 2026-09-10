package com.ligaya.app.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design, screen 1. The assertion that actually matters here is [splashAlwaysHandsOff]:
 * the splash is the app's start destination, so if its animation ever failed to complete — or if
 * a future edit moved [SplashScreen]'s `onFinished` call inside the non-reduced-motion branch —
 * the app would simply never reach Home. That is a launch-blocking failure, and it is invisible to
 * a compile check, so it gets a real test rather than being left to the animation's good behaviour.
 *
 * The brand lockup itself is asserted by text rather than by pixel: the mark is a Canvas drawing
 * (LigayaLogo) with no semantics of its own by design — it's decorative next to the real wordmark
 * text, which is the thing a screen reader and this test both key off.
 */
@RunWith(AndroidJUnit4::class)
class SplashScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashShowsTheBrandLockup() {
        composeTestRule.setContent { SplashScreen(onFinished = {}) }

        composeTestRule.onNodeWithText("LIGAYA").assertExists()
        composeTestRule.onNodeWithText("Your voice. Their help.").assertExists()
    }

    @Test
    fun splashAlwaysHandsOff() {
        var finished = false
        composeTestRule.setContent { SplashScreen(onFinished = { finished = true }) }

        composeTestRule.waitUntil(timeoutMillis = HANDOFF_TIMEOUT_MILLIS) { finished }

        assertTrue("splash must hand off to the next screen on its own", finished)
    }

    private companion object {
        /** Comfortably beyond the splash's own ~2.6s entrance + hold + exit. */
        const val HANDOFF_TIMEOUT_MILLIS = 15_000L
    }
}
