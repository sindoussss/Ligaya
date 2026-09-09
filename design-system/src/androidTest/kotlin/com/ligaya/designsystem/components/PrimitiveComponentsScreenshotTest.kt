package com.ligaya.designsystem.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.designsystem.LigayaDeliveryState
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaVoiceState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Step 34's own acceptance criterion, verified against the real device rather than a JVM-only
 * renderer (no such library is set up in this project — see design-system/build.gradle.kts'
 * comment on this dependency block): "each component renders correctly in a Compose preview
 * across light/dark and multiple font-scale settings." Each of the four axis combinations
 * (light/dark x 100%/200% font scale) is exercised for every primitive component below, a real
 * screenshot is captured and saved for each (readable from the device's external files dir, e.g.
 * via `adb pull`, for actual visual inspection — not just "didn't throw"), and at least one real
 * layout/semantics assertion per component proves it isn't just visually present but structurally
 * correct (touch target size, accessible description).
 *
 * Compose UI Test only allows calling setContent once per test, and stacking all four variants
 * into one composition at once overflows the real screen (captureToImage needs a node actually
 * on screen). So instead: setContent is called once per test, rendering whichever variant a
 * mutableIntStateOf index currently selects; the index is advanced and the root recaptured for
 * each of the four variants in turn — the standard pattern for "many sequential snapshots, one
 * setContent" in Compose UI Test.
 */
@RunWith(AndroidJUnit4::class)
class PrimitiveComponentsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private data class Variant(val label: String, val dark: Boolean, val fontScale: Float)

    private val variants = listOf(
        Variant("light_100", dark = false, fontScale = 1f),
        Variant("dark_100", dark = true, fontScale = 1f),
        Variant("light_200", dark = false, fontScale = 2f),
        Variant("dark_200", dark = true, fontScale = 2f),
    )

    /** Renders [content] once per [variants] entry (selected by a state index this composable
     *  owns), then, for each, waits for idle, captures the root, and saves a PNG named
     *  `screenshot_<name>_<variant label>.png` under the app's external files dir. */
    private fun renderCaptureAllVariants(name: String, content: @Composable (Variant) -> Unit) {
        lateinit var selectIndex: (Int) -> Unit
        composeTestRule.setContent {
            var index by remember { mutableIntStateOf(0) }
            selectIndex = { index = it }
            val variant = variants[index]
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, variant.fontScale)) {
                MaterialTheme(colorScheme = if (variant.dark) darkColorScheme() else lightColorScheme()) {
                    Surface { content(variant) }
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((i, variant) in variants.withIndex()) {
            if (i != 0) {
                composeTestRule.runOnUiThread { selectIndex(i) }
            }
            composeTestRule.waitForIdle()
            val bitmap: Bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
            assertTrue("$name (${variant.label}) must render real pixel content", bitmap.width > 0 && bitmap.height > 0)
            val outFile = File(context.getExternalFilesDir(null), "screenshot_${name}_${variant.label}.png")
            FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun sosControlIdleRendersAcrossLightDarkAndFontScale() {
        renderCaptureAllVariants("sos_control_idle") {
            SosControl(state = SosControlState.Idle, onClick = {})
        }
        // A real layout assertion, not just "it rendered": the emergency touch target from
        // LigayaSpacing must actually be honored (checked against whichever variant the
        // composition is left on — the token itself doesn't vary by variant).
        composeTestRule.onAllNodesWithContentDescription("Send SOS emergency alert").onFirst()
            .assertWidthIsAtLeast(LigayaSpacing.emergencyTouchTarget)
            .assertHeightIsAtLeast(LigayaSpacing.emergencyTouchTarget)
    }

    @Test
    fun sosControlPressedStateRendersAcrossLightDarkAndFontScale() {
        renderCaptureAllVariants("sos_control_pressed") {
            SosControl(state = SosControlState.Pressed, onClick = {})
        }
        composeTestRule.onAllNodesWithContentDescription("Send SOS emergency alert").onFirst().assertExists()
    }

    @Test
    fun sosControlActivatingStateRendersAcrossLightDarkAndFontScale() {
        renderCaptureAllVariants("sos_control_activating") {
            SosControl(state = SosControlState.Activating, onClick = {})
        }
        composeTestRule.onAllNodesWithContentDescription("Activating emergency alert").onFirst().assertExists()
    }

    @Test
    fun statusCardRendersEveryToneAcrossLightDarkAndFontScale() {
        renderCaptureAllVariants("status_card") {
            Column {
                StatusCard("911 Call", "Dialing…", StatusTone.Pending)
                StatusCard("Location", "GPS fix acquired", StatusTone.Success)
                StatusCard("911 Call", "Call failed — tap to retry", StatusTone.Failure)
                StatusCard("Companion", "Idle", StatusTone.Neutral)
            }
        }
        composeTestRule.onAllNodesWithContentDescription("911 Call: Dialing…").onFirst().assertExists()
        composeTestRule.onAllNodesWithContentDescription("911 Call: Call failed — tap to retry").onFirst().assertExists()
    }

    @Test
    fun statusChipRendersEveryToneAcrossLightDarkAndFontScale() {
        renderCaptureAllVariants("status_chip") {
            Row {
                StatusChip("Pending", StatusTone.Pending)
                StatusChip("Confirmed", StatusTone.Success)
                StatusChip("Failed", StatusTone.Failure)
            }
        }
        composeTestRule.onAllNodesWithContentDescription("Failed").onFirst().assertExists()
    }

    @Test
    fun voiceStateIndicatorRendersEveryStateAcrossLightDarkAndFontScale() {
        renderCaptureAllVariants("voice_state_indicator") {
            Row {
                LigayaVoiceState.entries.forEach { VoiceStateIndicator(state = it) }
            }
        }
        composeTestRule.onAllNodesWithContentDescription("Voice assistant listening").onFirst().assertExists()
        composeTestRule.onAllNodesWithContentDescription("Voice assistant speaking").onFirst().assertExists()
    }

    @Test
    fun deliveryStateBadgeRendersEveryStateAcrossLightDarkAndFontScale() {
        renderCaptureAllVariants("delivery_state_badge") {
            Row {
                LigayaDeliveryState.entries.forEach { DeliveryStateBadge(state = it) }
            }
        }
        composeTestRule.onAllNodesWithContentDescription("Delivery status: Confirmed").onFirst().assertExists()
        composeTestRule.onAllNodesWithContentDescription("Delivery status: Failed").onFirst().assertExists()
    }
}
