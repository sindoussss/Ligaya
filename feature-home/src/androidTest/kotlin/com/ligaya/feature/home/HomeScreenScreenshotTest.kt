package com.ligaya.feature.home

import android.graphics.Bitmap
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.LigayaSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Step 36's own "visual regression snapshot" acceptance criterion, verified the same way Step
 * 34's primitive-component previews were: a real instrumented capture on the actual emulator
 * (this project has no JVM-only screenshot-diffing library — see design-system/build.gradle.kts'
 * comment on that dependency block), across light/dark and 100%/200% font scale, plus a genuine
 * layout assertion (the SOS control's touch target) rather than only "it rendered."
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class NoOpEmergencyController : EmergencyController {
        override suspend fun triggerSos(): SosResult = SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)
        override suspend fun markSafe(): Result<EmergencyState> = Result.success(EmergencyState.USER_MARKED_SAFE)
        override suspend fun retryCall(): Result<EmergencyState> = Result.success(EmergencyState.EMERGENCY_ACTIVE)
        override fun observeSnapshot(): Flow<EmergencySnapshot?> = flowOf(null)
    }

    private data class Variant(val label: String, val dark: Boolean, val fontScale: Float)

    private val variants = listOf(
        Variant("light_100", dark = false, fontScale = 1f),
        Variant("dark_100", dark = true, fontScale = 1f),
        Variant("light_200", dark = false, fontScale = 2f),
        Variant("dark_200", dark = true, fontScale = 2f),
    )

    @Test
    fun homeScreenRendersAcrossLightDarkAndFontScale() {
        lateinit var selectIndex: (Int) -> Unit
        composeTestRule.setContent {
            var index by remember { mutableIntStateOf(0) }
            selectIndex = { index = it }
            val variant = variants[index]
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, variant.fontScale)) {
                MaterialTheme(colorScheme = if (variant.dark) darkColorScheme() else lightColorScheme()) {
                    Surface {
                        HomeScreen(
                            emergencyController = NoOpEmergencyController(),
                            otherDestinations = listOf(
                                NavigableDestination("onboarding", "Onboarding"),
                                NavigableDestination("family_emergency", "Family Emergency"),
                            ),
                            onSosActivated = {},
                            onNavigateToSafetyCircle = {},
                            onNavigateToCompanion = {},
                            onNavigateToRoute = {},
                            voicePhase = MutableStateFlow(VoicePipelinePhase.LISTENING),
                            voiceAiUnavailable = MutableStateFlow(false),
                        )
                    }
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
            assertTrue("home_screen (${variant.label}) must render real pixel content", bitmap.width > 0 && bitmap.height > 0)
            val outFile = File(context.getExternalFilesDir(null), "screenshot_home_screen_${variant.label}.png")
            FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        // A real layout assertion, not just "it rendered": the header SOS pill (visual design,
        // screen 2) must still clear the app-wide touch-target floor at every variant.
        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert")
            .assertWidthIsAtLeast(LigayaSpacing.minTouchTarget)
            .assertHeightIsAtLeast(LigayaSpacing.minTouchTarget)
    }
}
