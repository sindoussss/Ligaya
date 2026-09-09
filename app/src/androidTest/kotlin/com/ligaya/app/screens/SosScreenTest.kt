package com.ligaya.app.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the SOS button's UI wiring using a fake EmergencyController — deliberately not the
 * real one, so this test needs no database, no foreground service, and no MainActivity launch
 * (createComposeRule hosts the Composable directly). The real, authoritative proof of this
 * step's behavior (airplane mode, actual EMERGENCY_ACTIVE, actual notification) is
 * core-data's DefaultEmergencyControllerTest; this test exists to prove the button is wired to
 * the controller at all, independent of that.
 */
@RunWith(AndroidJUnit4::class)
class SosScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeEmergencyController(private val result: SosResult) : EmergencyController {
        var callCount = 0
            private set

        override suspend fun triggerSos(): SosResult {
            callCount++
            return result
        }

        override suspend fun markSafe(): Result<EmergencyState> = Result.success(EmergencyState.USER_MARKED_SAFE)

        override fun observeSnapshot(): Flow<EmergencySnapshot?> = flowOf(null)
    }

    @Test
    fun activatingSosCallsTheControllerAndNavigatesForward() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var activated = false

        composeTestRule.setContent {
            SosScreen(controller = fake, onActivated = { activated = true }, onBack = {})
        }

        composeTestRule.onNodeWithText("Activate SOS").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { activated }

        assertEquals(1, fake.callCount)
        assertTrue(activated)
    }

    @Test
    fun alreadyInProgressAlsoNavigatesForwardRatherThanBlockingTheUser() {
        val fake = FakeEmergencyController(SosResult.AlreadyInProgress(EmergencyState.EMERGENCY_ACTIVE))
        var activated = false

        composeTestRule.setContent {
            SosScreen(controller = fake, onActivated = { activated = true }, onBack = {})
        }

        composeTestRule.onNodeWithText("Activate SOS").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { activated }

        assertTrue(activated)
    }

    @Test
    fun backButtonInvokesOnBackWithoutCallingTheController() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var backCalled = false

        composeTestRule.setContent {
            SosScreen(controller = fake, onActivated = {}, onBack = { backCalled = true })
        }

        composeTestRule.onNodeWithText("Back").performClick()

        assertTrue(backCalled)
        assertEquals(0, fake.callCount)
    }
}
