package com.ligaya.feature.safetycircle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.data.profile.EmergencyContact
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Safety Circle tab, which replaced a Tools tab the architecture never asked for.
 *
 * What these tests are really protecting is section 23: with no backend project configured, nobody would be
 * alerted, so the screen has to say so — and must never grow an invite control that silently does nothing.
 */
@RunWith(AndroidJUnit4::class)
class SafetyCircleHomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val contacts = listOf(
        EmergencyContact(name = "Maria Casili", relationship = "Mother", phoneNumber = "09171234567"),
        EmergencyContact(name = "Ana Reyes", relationship = "Sister"),
    )

    @Test
    fun withoutABackendItSaysNobodyWouldBeAlertedAndOffersNoInvite() {
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SafetyCircleHomeScreen(
                    signedIn = true,
                    contacts = contacts,
                    householdBackendConfigured = false,
                    onSignIn = {},
                    onEditContacts = {},
                    onOpenQuickActions = {},
                    onOpenLigayaPlus = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Not available yet").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Inviting family and alerting them needs an account, and accounts are not switched on yet. " +
                "Nobody would be reached, so Ligaya does not offer to invite anyone for now.",
        ).performScrollTo().assertIsDisplayed()

        // A control that cannot do anything must not be on the screen at all.
        val inviteControls = composeTestRule.onAllNodesWithText("Invite", substring = true).fetchSemanticsNodes()
        assertTrue("no invite control may exist without a backend", inviteControls.isEmpty())
    }

    @Test
    fun theContactsSavedOnThisPhoneAreShownWithWhatIsActuallyKnownAboutThem() {
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SafetyCircleHomeScreen(
                    signedIn = true,
                    contacts = contacts,
                    householdBackendConfigured = false,
                    onSignIn = {},
                    onEditContacts = {},
                    onOpenQuickActions = {},
                    onOpenLigayaPlus = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Maria Casili. Mother · 09171234567")
            .performScrollTo().assertIsDisplayed()
        // No number saved means Ligaya genuinely cannot reach her, and the row says that rather than
        // showing a name that looks ready to be contacted.
        composeTestRule.onNodeWithContentDescription(
            "Ana Reyes. Sister · no number saved, so Ligaya cannot reach them yet",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun itSaysWhatKeepsWorkingWithoutAnyOfThis() {
        // Section 13's independence rule, said in the one place a person might otherwise read
        // "family alerts unavailable" as "this app cannot help me".
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SafetyCircleHomeScreen(
                    signedIn = false,
                    contacts = emptyList(),
                    householdBackendConfigured = false,
                    onSignIn = {},
                    onEditContacts = {},
                    onOpenQuickActions = {},
                    onOpenLigayaPlus = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            "SOS, calling 911, and Ligaya staying with you through an emergency do not depend on any " +
                "of this.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun signedOutItOffersTheWayInAndTheEmptyContactsLeadToTheProfile() {
        var signIn = false
        var editContacts = false
        var quickActions = false
        var ligayaPlus = false
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SafetyCircleHomeScreen(
                    signedIn = false,
                    contacts = emptyList(),
                    householdBackendConfigured = false,
                    onSignIn = { signIn = true },
                    onEditContacts = { editContacts = true },
                    onOpenQuickActions = { quickActions = true },
                    onOpenLigayaPlus = { ligayaPlus = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Sign in to set up your Safety Circle").performScrollTo().performClick()
        composeTestRule.onNodeWithText("No emergency contacts yet").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Quick actions").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Family plan").performScrollTo().performClick()

        assertTrue(signIn)
        assertTrue(editContacts)
        assertTrue(quickActions)
        assertTrue(ligayaPlus)
    }

    @Test
    fun theEmergencyContactsSummaryRowShowsTheRealCountAndLeadsToTheSameEditorAsFamilyAndFriends() {
        var editContacts = 0
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SafetyCircleHomeScreen(
                    signedIn = true,
                    contacts = contacts,
                    householdBackendConfigured = false,
                    onSignIn = {},
                    onEditContacts = { editContacts++ },
                    onOpenQuickActions = {},
                    onOpenLigayaPlus = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Emergency contacts. 2 added").performScrollTo().performClick()

        assertTrue("the summary row's own count must reflect the real contact list", editContacts == 1)
    }

    @Test
    fun quickActionsRowHandsOff() {
        var opened = false
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SafetyCircleHomeScreen(
                    signedIn = true,
                    contacts = contacts,
                    householdBackendConfigured = false,
                    onSignIn = {},
                    onEditContacts = {},
                    onOpenQuickActions = { opened = true },
                    onOpenLigayaPlus = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Quick actions").performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun familyAndFriendsShowsEachContactsInitialAsItsAvatar() {
        composeTestRule.setContent {
            LigayaTheme(mode = LigayaThemeMode.Light) {
                SafetyCircleHomeScreen(
                    signedIn = true,
                    contacts = contacts,
                    householdBackendConfigured = false,
                    onSignIn = {},
                    onEditContacts = {},
                    onOpenQuickActions = {},
                    onOpenLigayaPlus = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Family & Friends").performScrollTo().assertIsDisplayed()
        // No real photo exists for a locally-saved contact, so the avatar is honestly a letter, not a
        // placeholder headshot.
        composeTestRule.onNodeWithText("M").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("A").performScrollTo().assertIsDisplayed()
    }
}
