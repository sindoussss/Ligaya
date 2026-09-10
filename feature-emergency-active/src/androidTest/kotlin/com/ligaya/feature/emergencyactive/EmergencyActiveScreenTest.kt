package com.ligaya.feature.emergencyactive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 37's own required test: "UI test simulating slow/failed Places and SMS responses,
 * asserting the rest of the screen renders immediately and 'I'm safe' remains tappable
 * throughout." "Places" is EmergencyServiceFlowState (core-places' nearby-service lookup);
 * "SMS" is FamilyAlertFlowState (the Safety Circle alert flow, whose fallback channel is SMS —
 * Steps 17/19). A fake EmergencyController backed by a MutableStateFlow drives these states
 * directly — no real coordinator, no network, no timing dependency on an actual slow response.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyActiveScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeEmergencyController(
        private val snapshotFlow: MutableStateFlow<EmergencySnapshot?>,
    ) : EmergencyController {
        var markSafeCallCount = 0
            private set

        override suspend fun triggerSos(): SosResult = SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)

        override suspend fun markSafe(): Result<EmergencyState> {
            markSafeCallCount++
            return Result.success(EmergencyState.USER_MARKED_SAFE)
        }

        override suspend fun retryCall(): Result<EmergencyState> = Result.success(EmergencyState.EMERGENCY_ACTIVE)

        override fun observeSnapshot(): Flow<EmergencySnapshot?> = snapshotFlow
    }

    @Test
    fun restOfScreenRendersImmediatelyAndImSafeStaysTappableWhilePlacesAndSmsAreSlow() {
        val snapshotFlow = MutableStateFlow<EmergencySnapshot?>(
            EmergencySnapshot(
                state = EmergencyState.EMERGENCY_ACTIVE,
                subsystems = ConcurrentSubsystemStates(
                    location = LocationFlowState.Succeeded,
                    unified911 = Unified911FlowState.Succeeded,
                    emergencyService = EmergencyServiceFlowState.InProgress, // "slow" Places
                    familyAlert = FamilyAlertFlowState.InProgress, // "slow" SMS/family alert
                ),
            ),
        )
        val fake = FakeEmergencyController(snapshotFlow)

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = fake,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                onMarkedSafe = {},
            )
        }

        // The rest of the screen (911, unaffected by the slow subsystems) is already showing.
        composeTestRule.onNodeWithContentDescription("911: Connected to 911").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("I'm safe").assertIsDisplayed().assertIsEnabled()

        composeTestRule.onNodeWithContentDescription("I'm safe").performClick()
        assertEquals(1, fake.markSafeCallCount)
    }

    @Test
    fun restOfScreenRendersImmediatelyAndImSafeStaysTappableWhilePlacesAndSmsHaveFailed() {
        val snapshotFlow = MutableStateFlow<EmergencySnapshot?>(
            EmergencySnapshot(
                state = EmergencyState.EMERGENCY_ACTIVE,
                subsystems = ConcurrentSubsystemStates(
                    location = LocationFlowState.Succeeded,
                    unified911 = Unified911FlowState.Succeeded,
                    emergencyService = EmergencyServiceFlowState.LookupFailed, // "failed" Places
                    familyAlert = FamilyAlertFlowState.DeliveryFailed, // "failed" SMS/family alert
                ),
            ),
        )
        val fake = FakeEmergencyController(snapshotFlow)

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = fake,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                onMarkedSafe = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("911: Connected to 911").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Location: Location acquired").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("I'm safe").assertIsDisplayed().assertIsEnabled()

        composeTestRule.onNodeWithContentDescription("I'm safe").performClick()
        assertTrue(fake.markSafeCallCount == 1)
    }

    @Test
    fun screenRendersImmediatelyEvenBeforeAnySnapshotHasBeenEmitted() {
        // No emission at all yet (a genuinely empty MutableStateFlow's initial null) — the
        // screen must still render, not wait.
        val fake = FakeEmergencyController(MutableStateFlow(null))

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = fake,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                onMarkedSafe = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("I'm safe").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun markingSafeInvokesTheCallbackAfterTheControllerCall() {
        var markedSafe = false
        val fake = FakeEmergencyController(MutableStateFlow(null))

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = fake,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                onMarkedSafe = { markedSafe = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("I'm safe").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { markedSafe }

        assertTrue(markedSafe)
        assertEquals(1, fake.markSafeCallCount)
    }
}
