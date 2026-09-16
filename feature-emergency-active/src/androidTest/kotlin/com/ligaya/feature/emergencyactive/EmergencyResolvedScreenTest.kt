package com.ligaya.feature.emergencyactive

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.designsystem.components.LigayaTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design, screen 6 ("That's great!"): the design copy, and — sections 20 and 23 — only ever what the system
 * has actually confirmed. A pending subsystem is never celebrated, and a failed one is never hidden.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyResolvedScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeEmergencyController(
        private val snapshotFlow: MutableStateFlow<EmergencySnapshot?>,
    ) : EmergencyController {
        override suspend fun triggerSos(): SosResult = SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE)
        override suspend fun markSafe(): Result<EmergencyState> = Result.success(EmergencyState.USER_MARKED_SAFE)
        override suspend fun retryCall(): Result<EmergencyState> = Result.success(EmergencyState.EMERGENCY_ACTIVE)
        override fun observeSnapshot(): Flow<EmergencySnapshot?> = snapshotFlow
    }

    private fun show(subsystems: ConcurrentSubsystemStates, onSelectTab: (LigayaTab) -> Unit = {}) {
        val fake = FakeEmergencyController(
            MutableStateFlow(EmergencySnapshot(state = EmergencyState.USER_MARKED_SAFE, subsystems = subsystems)),
        )
        composeTestRule.setContent { EmergencyResolvedScreen(emergencyController = fake, onSelectTab = onSelectTab) }
    }

    @Test
    fun showsTheDesignCopyAndWhatTheUserActuallyDid() {
        show(ConcurrentSubsystemStates())
        composeTestRule.onNodeWithText("That's great!").assertExists()
        composeTestRule.onNodeWithText("I'm happy I could help!").assertExists()
        composeTestRule.onNodeWithText("You marked yourself safe").assertExists()
        composeTestRule.onNodeWithText("Emergency resolved by you.").assertExists()
    }

    @Test
    fun pendingSubsystemsAreNeverClaimedAsDone() {
        show(
            ConcurrentSubsystemStates(
                unified911 = Unified911FlowState.InProgress,
                familyAlert = FamilyAlertFlowState.Pending,
            ),
        )
        composeTestRule.onNodeWithText("911", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Safety Circle", substring = true).assertDoesNotExist()
    }

    @Test
    fun confirmedSubsystemsAreStated() {
        show(
            ConcurrentSubsystemStates(
                unified911 = Unified911FlowState.Succeeded,
                familyAlert = FamilyAlertFlowState.Succeeded,
            ),
        )
        composeTestRule.onNodeWithText("911 call placed").assertExists()
        composeTestRule.onNodeWithText("Your Safety Circle was notified").assertExists()
    }

    @Test
    fun failuresAreShownRatherThanHiddenBehindTheCelebration() {
        show(
            ConcurrentSubsystemStates(
                unified911 = Unified911FlowState.CallFailed,
                familyAlert = FamilyAlertFlowState.DeliveryFailed,
            ),
        )
        composeTestRule.onNodeWithText("The 911 call didn't go through").assertExists()
        composeTestRule.onNodeWithText("Your Safety Circle wasn't notified").assertExists()
        composeTestRule.onNodeWithText("911 call placed").assertDoesNotExist()
        composeTestRule.onNodeWithText("Your Safety Circle was notified").assertDoesNotExist()
    }

    @Test
    fun tabsReachTheirCallback() {
        var picked: LigayaTab? = null
        show(ConcurrentSubsystemStates(), onSelectTab = { picked = it })
        composeTestRule.onNodeWithText("Chat").performClick()
        assertEquals(LigayaTab.Chat, picked)
    }

    @Test
    fun resolvedScreenHasNoAccessibilityFrameworkFindings() {
        show(ConcurrentSubsystemStates(unified911 = Unified911FlowState.Succeeded))
        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
