package com.ligaya.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Step 36's acceptance criterion: "SOS control tap triggers Step 13's flow." Same fake-controller
 * pattern as app/screens/SosScreenTest.kt (Step 13) — no database, no foreground service, no
 * MainActivity launch needed to prove the control is wired to the controller at all.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

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
    fun tappingSosControlCallsTheControllerAndReportsActivation() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var activated = false

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = { activated = true },
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { activated }

        assertEquals(1, fake.callCount)
        assertTrue(activated)
    }

    @Test
    fun tappingSosControlWhenAlreadyInProgressStillReportsActivationRatherThanBlockingTheUser() {
        val fake = FakeEmergencyController(SosResult.AlreadyInProgress(EmergencyState.EMERGENCY_ACTIVE))
        var activated = false

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = { activated = true },
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Send SOS emergency alert").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { activated }

        assertTrue(activated)
    }

    @Test
    fun tappingSafetyCircleCardNavigatesWithoutCallingTheController() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var navigated = false

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = { navigated = true },
                onNavigateToCompanion = {},
                onNavigateToRoute = {},
            )
        }

        composeTestRule.onNodeWithText("Safety Circle").performClick()

        assertTrue(navigated)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun tappingCompanionCardNavigatesWithoutCallingTheController() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var navigated = false

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = emptyList(),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = { navigated = true },
                onNavigateToRoute = {},
            )
        }

        composeTestRule.onNodeWithText("Emergency Companion").performClick()

        assertTrue(navigated)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun otherDestinationsAreRenderedAndNavigable() {
        val fake = FakeEmergencyController(SosResult.Activated(EmergencyState.EMERGENCY_ACTIVE))
        var navigatedRoute: String? = null

        composeTestRule.setContent {
            HomeScreen(
                emergencyController = fake,
                otherDestinations = listOf(NavigableDestination("onboarding", "Onboarding")),
                onSosActivated = {},
                onNavigateToSafetyCircle = {},
                onNavigateToCompanion = {},
                onNavigateToRoute = { navigatedRoute = it },
            )
        }

        composeTestRule.onNodeWithText("Onboarding").performClick()

        assertEquals("onboarding", navigatedRoute)
    }
}
