package com.ligaya.feature.family

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.data.family.FamilyEmergencyLocation
import com.ligaya.core.data.family.FamilyEmergencyView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 40's own acceptance criterion: "screen renders correctly with partial data (e.g., location
 * withheld by permission) without layout breakage." [partialPermissionPayload] mocks exactly that
 * — a viewer whose FamilyEmergencyView (Step 20) has no location and no notificationEvents yet,
 * which is ordinary, not an error state (see FamilyEmergencyScreen's own doc comment).
 */
@RunWith(AndroidJUnit4::class)
class FamilyEmergencyScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fullPermissionPayload = FamilyEmergencyView(
        incidentType = "FIRE",
        timeEpochMillis = 1_700_000_000_000L,
        status = "EMERGENCY_ACTIVE",
        alertState = "CONFIRMED",
        location = FamilyEmergencyLocation(latitude = 14.5995, longitude = 120.9842),
    )

    private val partialPermissionPayload = FamilyEmergencyView(
        incidentType = "MEDICAL",
        timeEpochMillis = 1_700_000_000_000L,
        status = "EMERGENCY_ACTIVE",
        alertState = null,
        location = null,
    )

    @Test
    fun rendersEveryFieldWhenFullyPermitted() {
        composeTestRule.setContent {
            FamilyEmergencyScreen(view = fullPermissionPayload)
        }

        composeTestRule.onNodeWithContentDescription("Incident type: Fire").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Status: Emergency Active").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delivery status: Confirmed").assertIsDisplayed()
        composeTestRule.onNodeWithText("14.59950, 120.98420").assertIsDisplayed()
    }

    @Test
    fun rendersWithoutLayoutBreakageWhenLocationAndAlertStateAreWithheld() {
        composeTestRule.setContent {
            FamilyEmergencyScreen(view = partialPermissionPayload)
        }

        composeTestRule.onNodeWithContentDescription("Incident type: Medical").assertIsDisplayed()
        composeTestRule.onNodeWithText("Location not shared").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not sent yet").assertIsDisplayed()
    }
}
