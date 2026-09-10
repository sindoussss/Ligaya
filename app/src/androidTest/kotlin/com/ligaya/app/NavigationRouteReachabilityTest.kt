package com.ligaya.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
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
 *  fallback input only) — has no "Back" control either.
 *
 *  Step 48: MainActivity now requests RECORD_AUDIO for real in onCreate (the wake-word loop's own
 *  real "start listening" moment). [permissionRule] pre-grants it, ordered ahead of
 *  [composeTestRule], so the real system permission dialog never appears here and steals window
 *  focus — the exact same class of bug already found and fixed once, the first time an eager
 *  permission request was tried (Step 39).
 *
 *  Step 50: MainActivity also now requests POST_NOTIFICATIONS for real (chained after RECORD_AUDIO
 *  resolves — see MainActivity's own doc comment on why it's chained rather than requested
 *  back-to-back). Added to the same [permissionRule] for the same reason as RECORD_AUDIO above —
 *  confirmed necessary directly: with a genuinely fresh, uninstalled test package (not just a
 *  fresh permission grant), this test failed exactly like the original RECORD_AUDIO case once did,
 *  the real "Allow Ligaya to send you notifications?" dialog appearing and stealing focus. */
@RunWith(AndroidJUnit4::class)
class NavigationRouteReachabilityTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Visual design, screen 1: the app now launches to Splash, which hands off to Home on its own
     * after its entrance animation. So "launches to Home" became "settles on Home" — waiting for
     * it is the honest assertion, not a workaround: an immediate assert would be asserting the app
     * has no launch screen, which is no longer true. The timeout is deliberately far longer than
     * the splash's own ~2.6s so this never becomes a timing-flaky test on a slow emulator.
     */
    private fun awaitHome() {
        composeTestRule.waitUntil(timeoutMillis = SPLASH_HANDOFF_TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText(LigayaDestination.Home.title)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun appLaunchesAndSettlesOnHome() {
        awaitHome()
        composeTestRule.onNodeWithText(LigayaDestination.Home.title).assertIsDisplayed()
    }

    @Test
    fun everyRouteIsReachableAndReturnsToHomeWithoutCrashing() {
        awaitHome()

        val destinations = LigayaDestination.all.filter {
            it != LigayaDestination.Home &&
                it != LigayaDestination.Sos &&
                it != LigayaDestination.EmergencyActive &&
                it != LigayaDestination.EmergencyCompanion &&
                // Visual design screen 2, excluded for the same reason as the three above: the
                // route now renders the real OnboardingIntroScreen, which by design shows neither
                // the literal title text "Onboarding" nor a "Back" control (its exits are "Skip"
                // and the primary CTA). OnboardingIntroScreenTest covers it directly instead.
                it != LigayaDestination.Onboarding
        }

        for (destination in destinations) {
            composeTestRule.onNodeWithText(destination.title).performClick()
            composeTestRule.onNodeWithText(destination.title).assertIsDisplayed()

            composeTestRule.onNodeWithText("Back").performClick()
            composeTestRule.onNodeWithText(LigayaDestination.Home.title).assertIsDisplayed()
        }
    }

    private companion object {
        const val SPLASH_HANDOFF_TIMEOUT_MILLIS = 15_000L
    }
}
