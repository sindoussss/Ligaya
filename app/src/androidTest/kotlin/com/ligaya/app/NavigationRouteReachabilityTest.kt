package com.ligaya.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.app.navigation.LigayaDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers this step's acceptance criteria: app launches to Home; every route is reachable and
 *  navigable without crashing.
 *
 *  Sos is excluded from the generic loop below as of Step 36: Home's own SosControl is now a
 *  real, directly-functional SOS action (feature-home's own HomeScreenTest covers it), and a
 *  second generic "SOS"-titled button to the older standalone Sos screen is genuinely ambiguous
 *  on the same screen — proven directly (Compose UI Test's onNodeWithText("SOS") found two
 *  matching nodes), not just a hypothetical concern. The Sos route/screen are untouched; they're
 *  just no longer offered as a redundant path from Home.
 *
 *  EmergencyActive is excluded as of Step 37 for the same class of reason: it now routes to the
 *  real EmergencyActiveScreen (feature-emergency-active's own EmergencyActiveScreenTest covers
 *  it), which has no "Back" button (only the persistent "I'm safe" control, by design — see that
 *  screen's own doc comment) and no literal "Emergency Active" title text — this generic
 *  Back-button-and-title-text loop doesn't fit its real shape, the same reason Sos was excluded
 *  rather than force-fitted.
 *
 *  EmergencyCompanion is excluded as of Step 39 for the same reason again: it now routes to the
 *  real EmergencyCompanionScreen (feature-companion's own EmergencyCompanionScreenTextFallbackTest
 *  covers it directly), which — per that step's own spec (transcript, voice indicator, text
 *  fallback input only) — has no "Back" control either. */
@RunWith(AndroidJUnit4::class)
class NavigationRouteReachabilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesToHome() {
        composeTestRule.onNodeWithText(LigayaDestination.Home.title).assertIsDisplayed()
    }

    @Test
    fun everyRouteIsReachableAndReturnsToHomeWithoutCrashing() {
        val destinations = LigayaDestination.all.filter {
            it != LigayaDestination.Home &&
                it != LigayaDestination.Sos &&
                it != LigayaDestination.EmergencyActive &&
                it != LigayaDestination.EmergencyCompanion
        }

        for (destination in destinations) {
            composeTestRule.onNodeWithText(destination.title).performClick()
            composeTestRule.onNodeWithText(destination.title).assertIsDisplayed()

            composeTestRule.onNodeWithText("Back").performClick()
            composeTestRule.onNodeWithText(LigayaDestination.Home.title).assertIsDisplayed()
        }
    }
}
