package com.ligaya.feature.emergencyactive

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.LigayaDeliveryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 45's real Accessibility Test Framework pass on the Emergency Active screen — the most
 * information-dense, safety-critical screen in the app (this screen's own doc comment). The
 * snapshot below deliberately exercises every Step 41 failure treatment at once
 * (CallFailedCard/OfflineDegradedBanner/LookupFailedCard/DeliveryFailedCard, alongside the normal
 * StatusCard/DeliveryStateBadge path) so this one check covers the screen's full real surface,
 * not just its calmest state.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyActiveScreenAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class NoOpEmergencyController(private val snapshot: EmergencySnapshot) : EmergencyController {
        override suspend fun triggerSos(): SosResult = SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)
        override suspend fun markSafe(): Result<EmergencyState> = Result.success(EmergencyState.USER_MARKED_SAFE)
        override suspend fun retryCall(): Result<EmergencyState> = Result.success(EmergencyState.EMERGENCY_ACTIVE)
        override fun observeSnapshot(): Flow<EmergencySnapshot?> = flowOf(snapshot)
    }

    @Test
    fun emergencyActiveScreenHasNoAccessibilityFrameworkFindingsAcrossEveryFailureState() {
        val snapshot = EmergencySnapshot(
            state = EmergencyState.EMERGENCY_ACTIVE,
            subsystems = ConcurrentSubsystemStates(
                unified911 = Unified911FlowState.CallFailed,
                location = LocationFlowState.Unavailable,
                emergencyService = EmergencyServiceFlowState.LookupFailed,
            ),
        )
        val deliveryStatus = listOf(
            MemberDeliveryStatus(
                memberName = "Mother",
                channelStatuses = listOf(
                    ChannelDeliveryStatus(channelLabel = "SMS", state = LigayaDeliveryState.FAILED),
                    ChannelDeliveryStatus(channelLabel = "Push", state = LigayaDeliveryState.CONFIRMED),
                ),
            ),
        )

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = NoOpEmergencyController(snapshot),
                safetyCircleDeliveryStatus = deliveryStatus,
                voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.LISTENING),
                onMarkedSafe = {},
                onRetryCall = {},
            )
        }

        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
