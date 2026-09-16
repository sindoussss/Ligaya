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
import com.ligaya.core.places.EmergencyServiceLookupResult
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


    @Test
    fun aSucceededLookupWithAPhoneNumberShowsTheRealNameDistanceAndContactHonestly() {
        val snapshotFlow = MutableStateFlow<EmergencySnapshot?>(
            EmergencySnapshot(
                state = EmergencyState.EMERGENCY_ACTIVE,
                subsystems = ConcurrentSubsystemStates(
                    unified911 = Unified911FlowState.Succeeded,
                    emergencyService = EmergencyServiceFlowState.Succeeded,
                ),
            ),
        )
        val fake = FakeEmergencyController(snapshotFlow)
        val result = MutableStateFlow<EmergencyServiceLookupResult?>(
            EmergencyServiceLookupResult(
                name = "Barangay Health Center",
                address = "123 Rizal St",
                phoneNumber = "0917 123 4567",
                distanceMeters = 450.0,
            ),
        )

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = fake,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                onMarkedSafe = {},
                emergencyServiceResult = result,
            )
        }

        // The real place, its real distance, and a phone number described as a public contact —
        // never "verified" or "dispatch" (section 16) — with 911 shown separately, above it.
        composeTestRule.onNodeWithContentDescription(
            "Nearby Emergency Service: Barangay Health Center, 450 m away. " +
                "Public contact available: 0917 123 4567.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("911: Connected to 911").assertIsDisplayed()
    }

    @Test
    fun aSucceededLookupWithNoPhoneNumberNeverInventsOneAndPointsBackTo911() {
        val snapshotFlow = MutableStateFlow<EmergencySnapshot?>(
            EmergencySnapshot(
                state = EmergencyState.EMERGENCY_ACTIVE,
                subsystems = ConcurrentSubsystemStates(emergencyService = EmergencyServiceFlowState.Succeeded),
            ),
        )
        val fake = FakeEmergencyController(snapshotFlow)
        val result = MutableStateFlow<EmergencyServiceLookupResult?>(
            EmergencyServiceLookupResult(name = "Barangay Health Center", address = null, phoneNumber = null, distanceMeters = 900.0),
        )

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = fake,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                onMarkedSafe = {},
                emergencyServiceResult = result,
            )
        }

        // Succeeded is still the true engine state (a place was found), but with nothing to call,
        // this must read as "no number," never as an invented one and never as a plain success card.
        composeTestRule.onNodeWithContentDescription(
            "No phone number available for this service. Please call 911 directly.",
        ).assertIsDisplayed()
    }

    @Test
    fun aSucceededLookupWithNoResultYetFallsBackToTheGenericStatusRatherThanCrashing() {
        // The theoretical gap between the state flipping to Succeeded and the separate result flow
        // catching up (see MainActivity's own ordering comment on why this should not happen in
        // practice) — the screen must still render something true, not null-pointer.
        val snapshotFlow = MutableStateFlow<EmergencySnapshot?>(
            EmergencySnapshot(
                state = EmergencyState.EMERGENCY_ACTIVE,
                subsystems = ConcurrentSubsystemStates(emergencyService = EmergencyServiceFlowState.Succeeded),
            ),
        )
        val fake = FakeEmergencyController(snapshotFlow)

        composeTestRule.setContent {
            EmergencyActiveScreen(
                emergencyController = fake,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = MutableStateFlow(VoicePipelinePhase.IDLE),
                onMarkedSafe = {},
                emergencyServiceResult = MutableStateFlow(null),
            )
        }

        composeTestRule.onNodeWithContentDescription("Nearby Emergency Service: Nearby service found").assertIsDisplayed()
    }
}
