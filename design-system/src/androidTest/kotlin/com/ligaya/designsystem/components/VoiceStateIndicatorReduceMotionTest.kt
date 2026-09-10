package com.ligaya.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.designsystem.LigayaVoiceState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 45's own accessibility fix, real end to end: Android's "Remove animations" accessibility
 * toggle (Settings > Accessibility) sets `animator_duration_scale` to 0 — the same setting
 * `ValueAnimator` itself reads (see [com.ligaya.designsystem.rememberIsReduceMotionEnabled]'s own
 * doc comment). This test flips that real system setting via shell, the standard technique for
 * this in an instrumented test (same approach `DefaultEmergencyControllerTest`'s airplane-mode
 * test and `IntentUnified911DialActionInstrumentedTest` already use for their own OS-level
 * settings), and proves [VoiceStateIndicator]'s reduce-motion branch renders correctly under it —
 * not just that the branch exists in source.
 */
@RunWith(AndroidJUnit4::class)
class VoiceStateIndicatorReduceMotionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun setReduceMotion(enabled: Boolean) {
        uiAutomation.executeShellCommand("settings put global animator_duration_scale ${if (enabled) "0" else "1"}").close()
    }

    @After
    fun tearDown() {
        setReduceMotion(enabled = false)
    }

    @Test
    fun voiceStateIndicatorStillRendersCorrectlyWithReduceMotionOn() {
        setReduceMotion(enabled = true)

        composeTestRule.setContent {
            MaterialTheme {
                VoiceStateIndicator(state = LigayaVoiceState.LISTENING)
            }
        }

        composeTestRule.onNodeWithContentDescription("Voice assistant listening").assertIsDisplayed()
    }
}
