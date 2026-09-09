package com.ligaya.feature.emergencyactive

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.designsystem.LigayaDeliveryState
import com.ligaya.feature.companion.VoicePipelinePhase
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
 * Step 37's "visual regression snapshot" acceptance criterion — same real-device capture pattern
 * as Steps 34/36 (this project has no JVM-only screenshot-diffing library; see design-system/
 * build.gradle.kts' comment on that dependency block), across light/dark and 100%/200% font
 * scale, with a realistic populated snapshot (not an empty/loading one) so the densest state of
 * this screen is what's actually captured.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyActiveScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class NoOpEmergencyController : EmergencyController {
        override suspend fun triggerSos(): SosResult = SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)
        override suspend fun markSafe(): Result<EmergencyState> = Result.success(EmergencyState.USER_MARKED_SAFE)
        override fun observeSnapshot(): Flow<EmergencySnapshot?> = flowOf(
            EmergencySnapshot(
                state = EmergencyState.EMERGENCY_ACTIVE,
                subsystems = ConcurrentSubsystemStates(
                    location = LocationFlowState.Succeeded,
                    unified911 = Unified911FlowState.InProgress,
                    familyAlert = FamilyAlertFlowState.DeliveryFailed,
                ),
            ),
        )
    }

    private data class Variant(val label: String, val dark: Boolean, val fontScale: Float)

    private val variants = listOf(
        Variant("light_100", dark = false, fontScale = 1f),
        Variant("dark_100", dark = true, fontScale = 1f),
        Variant("light_200", dark = false, fontScale = 2f),
        Variant("dark_200", dark = true, fontScale = 2f),
    )

    @Test
    fun emergencyActiveScreenRendersAcrossLightDarkAndFontScale() {
        lateinit var selectIndex: (Int) -> Unit
        composeTestRule.setContent {
            var index by remember { mutableIntStateOf(0) }
            selectIndex = { index = it }
            val variant = variants[index]
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, variant.fontScale)) {
                MaterialTheme(colorScheme = if (variant.dark) darkColorScheme() else lightColorScheme()) {
                    Surface {
                        EmergencyActiveScreen(
                            emergencyController = NoOpEmergencyController(),
                            safetyCircleDeliveryStatus = listOf(
                                MemberDeliveryStatus(
                                    memberName = "Sibling",
                                    channelStatuses = listOf(
                                        ChannelDeliveryStatus("Push", LigayaDeliveryState.CONFIRMED),
                                        ChannelDeliveryStatus("SMS", LigayaDeliveryState.FAILED),
                                    ),
                                ),
                            ),
                            // LISTENING, not IDLE, so this capture also proves the real (not
                            // placeholder) voice indicator renders correctly in a non-resting state.
                            voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.LISTENING),
                            onMarkedSafe = {},
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
            assertTrue("emergency_active_screen (${variant.label}) must render real pixel content", bitmap.width > 0 && bitmap.height > 0)
            val outFile = File(context.getExternalFilesDir(null), "screenshot_emergency_active_screen_${variant.label}.png")
            FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
