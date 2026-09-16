package com.ligaya.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.permissions.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design screen 4. The failure mode this guards against is specific: offering "Allow" when
 * Android will no longer show a dialog, which produces a button that visibly does nothing. Each
 * test therefore pins the action offered for one [PermissionState], not just that the screen
 * renders.
 */
@RunWith(AndroidJUnit4::class)
class LocationPermissionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setScreen(
        state: PermissionState,
        onRequestPermission: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onContinue: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            LocationPermissionScreen(
                permissionState = state,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                onContinue = onContinue,
                onBack = {},
            )
        }
    }

    @Test
    fun explainsWhyBeforeAskingAtAll() {
        setScreen(PermissionState.NotRequested)

        composeTestRule.onNodeWithText("Allow location access").assertExists()
        composeTestRule.onNodeWithText("Find nearby emergency services").assertExists()
        composeTestRule.onNodeWithText("Share your location with your Safety Circle").assertExists()
        composeTestRule.onNodeWithText("Provide faster, more accurate help").assertExists()
    }

    @Test
    fun notYetRequestedOffersTheRealSystemPrompt() {
        var requested = 0
        setScreen(PermissionState.NotRequested, onRequestPermission = { requested++ })

        composeTestRule.onNodeWithText("Allow While Using App").performScrollTo().performClick()

        assertEquals(1, requested)
    }

    @Test
    fun aPlainDenialCanStillBeAskedAgain() {
        var requested = 0
        setScreen(PermissionState.Denied, onRequestPermission = { requested++ })

        composeTestRule.onNodeWithText("Allow While Using App").performScrollTo().performClick()

        assertEquals("a soft denial is re-askable, so the primer must still offer the dialog", 1, requested)
    }

    /** The case that motivates the whole state split — see this class's own doc comment. */
    @Test
    fun permanentlyDeniedRoutesToSettingsInsteadOfADialogThatWontAppear() {
        var requested = 0
        var settingsOpened = 0
        setScreen(
            PermissionState.PermanentlyDenied,
            onRequestPermission = { requested++ },
            onOpenSettings = { settingsOpened++ },
        )

        composeTestRule.onNodeWithText("Allow While Using App").assertDoesNotExist()
        composeTestRule.onNodeWithText("Open Settings").performScrollTo().performClick()

        assertEquals(1, settingsOpened)
        assertEquals("must never call a request that Android will silently ignore", 0, requested)
    }

    @Test
    fun permanentlyDeniedSaysWhatStillWorksRatherThanJustWhatIsBlocked() {
        setScreen(PermissionState.PermanentlyDenied)

        composeTestRule.onNodeWithText(
            "Location is currently blocked for Ligaya, so we can't ask again from here. " +
                "You can turn it on in Settings. Without it, an emergency still starts and still " +
                "calls 911. We just cannot share where you are.",
        ).assertExists()
    }

    @Test
    fun alreadyGrantedNeverAsksAgain() {
        var requested = 0
        var continued = 0
        setScreen(PermissionState.Granted, onRequestPermission = { requested++ }, onContinue = { continued++ })

        composeTestRule.onNodeWithText("Location access is on").assertExists()
        composeTestRule.onNodeWithText("Allow While Using App").assertDoesNotExist()
        composeTestRule.onNodeWithText("Not Now").assertDoesNotExist()

        composeTestRule.onNodeWithText("Continue").performScrollTo().performClick()
        assertEquals(1, continued)
        assertEquals(0, requested)
    }

    /** §3: denial explains reduced functionality, it never blocks onboarding. */
    @Test
    fun notNowContinuesWithoutGrantingAnything() {
        var continued = 0
        var requested = 0
        setScreen(PermissionState.NotRequested, onRequestPermission = { requested++ }, onContinue = { continued++ })

        composeTestRule.onNodeWithText("Not Now").performScrollTo().performClick()

        assertTrue("declining must move onboarding forward", continued == 1)
        assertEquals(0, requested)
    }
}
