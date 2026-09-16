package com.ligaya.app.screens.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaThemeMode
import com.ligaya.designsystem.components.LigayaMascotPreferences
import com.ligaya.designsystem.components.LigayaMotionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design, screen 9 and its six destinations. The point of these tests is that every row leads
 * somewhere real and every choice is reported — the thing the screen would otherwise only appear to do.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun everySettingsRowOpensItsOwnSection() {
        val opened = mutableListOf<SettingsSection>()
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SettingsScreen(
                    userName = "Maria",
                    userEmail = "maria@example.com",
                    appearanceValue = "System",
                    onBack = {},
                    onOpenSection = { opened += it },
                    onOpenProfile = {},
                )
            }
        }

        val rows = mapOf(
            "General" to SettingsSection.General,
            "Appearance, System" to SettingsSection.Appearance,
            "Voice & Speech" to SettingsSection.Voice,
            "Character & Animation" to SettingsSection.Character,
            "Privacy & Security" to SettingsSection.Privacy,
            "About Ligaya" to SettingsSection.About,
        )
        rows.forEach { (description, _) ->
            composeTestRule.onNodeWithContentDescription(description).performClick()
        }

        assertEquals(rows.values.toList(), opened)
    }

    @Test
    fun profileCardSaysSoWhenNobodyIsSignedIn() {
        var openedProfile = false
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SettingsScreen(
                    userName = null,
                    userEmail = null,
                    appearanceValue = "Light",
                    onBack = {},
                    onOpenSection = {},
                    onOpenProfile = { openedProfile = true },
                )
            }
        }

        composeTestRule.onNodeWithText("You are not signed in").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign in to keep your emergency profile").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            "You are not signed in. Sign in to keep your emergency profile",
        ).performClick()
        assertTrue(openedProfile)
    }

    @Test
    fun appearanceMarksTheSavedChoiceAndReportsANewOne() {
        var chosen: LigayaThemeMode? = null
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                AppearanceSettingsScreen(
                    mode = LigayaThemeMode.Dark,
                    onSelectMode = { chosen = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("System. Follows your phone, so it changes with it.")
            .performClick()
        assertEquals(LigayaThemeMode.System, chosen)
    }

    @Test
    fun voiceScreenTogglesTheWakePhraseAndSaysWhenItCannotListen() {
        var enabled: Boolean? = null
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                VoiceSettingsScreen(
                    wakePhraseEnabled = true,
                    onWakePhraseChange = { enabled = it },
                    micPermitted = false,
                    smartRepliesConfigured = true,
                    onOpenSystemSettings = {},
                    onBack = {},
                )
            }
        }

        // On, but the microphone was never granted: the screen has to say she is not listening.
        composeTestRule.onNodeWithText(
            "She is not listening right now, because Ligaya does not have microphone access on this phone.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            "Listen for the wake phrase. While Ligaya is open, she listens so you can speak without touching " +
                "the phone.",
        ).performClick()
        assertEquals(false, enabled)
    }

    @Test
    fun characterScreenReportsEachChoice() {
        var speed: Float? = null
        var depth: Float? = null
        var motion: LigayaMotionMode? = null
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                CharacterSettingsScreen(
                    preferences = LigayaMascotPreferences(),
                    reduceMotionOnPhone = true,
                    onAnimationSpeedChange = { speed = it },
                    onDepthStrengthChange = { depth = it },
                    onMotionChange = { motion = it },
                    onBack = {},
                )
            }
        }

        // Three cards down one scrolling screen: each has to be brought into view before it can be tapped,
        // exactly as a person would have to scroll to it.
        composeTestRule.onNodeWithContentDescription("Lively. She breathes and blinks a little faster.")
            .performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Flat. No parallax at all.")
            .performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Hold still. She stays still except when she speaks.")
            .performScrollTo().performClick()

        assertEquals(1.4f, speed)
        assertEquals(0f, depth)
        assertEquals(LigayaMotionMode.Still, motion)
        // The phone's own reduce-motion setting overrules all three, and the screen admits it.
        composeTestRule.onNodeWithText(
            "Your phone asks apps to reduce motion, so Ligaya is held still whichever of these you pick.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutAndPrivacySayWhatIsTrueOfThisBuild() {
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                AboutSettingsScreen(versionName = "0.1.0", smartRepliesConfigured = false, onBack = {})
            }
        }

        composeTestRule.onNodeWithText("0.1.0").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Off. Ligaya still listens and answers simply, and tells you when a reply is basic.",
        ).assertIsDisplayed()
    }

    @Test
    fun privacyOffersSignOutOnlyWhenThereIsAnAccountToSignOutOf() {
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                PrivacySettingsScreen(
                    signedInEmail = null,
                    smartRepliesConfigured = false,
                    onSignOut = {},
                    onOpenSystemSettings = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            "Nothing. Smart replies are switched off, so nothing is sent to Gemini.",
        ).assertIsDisplayed()

        // Nobody is signed in, so there is nothing to sign out of and no row offering it.
        val signOutRows = composeTestRule.onAllNodesWithText("Sign out").fetchSemanticsNodes()
        assertTrue("Signed out: no sign-out row expected", signOutRows.isEmpty())
    }
}
