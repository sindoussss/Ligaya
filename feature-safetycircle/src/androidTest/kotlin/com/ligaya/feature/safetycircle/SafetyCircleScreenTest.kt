package com.ligaya.feature.safetycircle

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ligaya.core.backend.household.FirestoreSafetyCircleRepository
import com.ligaya.core.backend.household.SafetyCircleRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 43's own acceptance criterion: "UI test against a live/emulated backend household with
 * mixed member states." Same real local Firebase Auth + Firestore emulator pattern as
 * core-backend's own SafetyCircleRepositoryTest (Step 8) — no real Firebase project, no real
 * account, but a genuinely real backend round trip, not a fake in-memory repository.
 *
 * [composeTestRule.waitUntil] rather than a direct assertIsDisplayed right after setContent:
 * SafetyCircleScreen's own LaunchedEffect makes a real network call to the Firestore emulator,
 * which Compose UI Test's auto-idling doesn't know how to wait for (it only synchronizes with
 * Compose's own recomposition/frame loop, not arbitrary background network I/O) — polling with a
 * timeout is the correct pattern here, not a hopeful immediate assertion.
 */
@RunWith(AndroidJUnit4::class)
class SafetyCircleScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private data class SignedIn(val uid: String, val repository: SafetyCircleRepository)

    private suspend fun signIn(appName: String): SignedIn {
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-ligaya-test")
            .setApplicationId("1:000000000000:android:0000000000000000000000")
            .setApiKey("fake-api-key-for-emulator-only")
            .build()
        val app = FirebaseApp.initializeApp(context, options, appName)

        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
        val email = "user-${System.currentTimeMillis()}-${(0..999999).random()}@test.ligaya.app"
        val uid = auth.createUserWithEmailAndPassword(email, "correcthorsebatterystaple").await().user!!.uid

        val firestore = FirebaseFirestore.getInstance(app).apply { useEmulator("10.0.2.2", 8090) }
        return SignedIn(uid, FirestoreSafetyCircleRepository(firestore))
    }

    private fun waitForContentDescription(description: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun ownerSeesTheFullRosterWithMixedMemberStates() {
        val owner: SignedIn
        val pendingMember: SignedIn
        val householdId: String
        runBlocking {
            owner = signIn("safetyCircleScreenOwner")
            val activeMember = signIn("safetyCircleScreenActive")
            pendingMember = signIn("safetyCircleScreenPending")

            householdId = owner.repository.createHousehold(owner.uid)
            owner.repository.inviteMember(householdId, activeMember.uid, "Mother")
            owner.repository.inviteMember(householdId, pendingMember.uid, "Friend")
            activeMember.repository.acceptInvite(householdId, activeMember.uid)
        }

        composeTestRule.setContent {
            SafetyCircleScreen(
                householdId = householdId,
                currentUserId = owner.uid,
                repository = owner.repository,
            )
        }

        waitForContentDescription("Mother, Active")
        composeTestRule.onNodeWithContentDescription("Mother, Active").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Friend, Pending").assertIsDisplayed()

        composeTestRule.onNodeWithTag("safetyCircleRemove_${pendingMember.uid}").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription("Friend, Pending").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun invitedMemberSeesTheirOwnPendingStatusAndCanAcceptIt() {
        val owner: SignedIn
        val invitee: SignedIn
        val householdId: String
        runBlocking {
            owner = signIn("safetyCircleScreenSelfOwner")
            invitee = signIn("safetyCircleScreenSelfInvitee")

            householdId = owner.repository.createHousehold(owner.uid)
            owner.repository.inviteMember(householdId, invitee.uid, "Sibling")
        }

        composeTestRule.setContent {
            SafetyCircleScreen(
                householdId = householdId,
                currentUserId = invitee.uid,
                repository = invitee.repository,
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("safetyCircleAcceptInvite", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("You're the Sibling in this Safety Circle.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("safetyCircleAcceptInvite").performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("safetyCircleLeave", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("safetyCircleLeave").assertIsDisplayed()
    }
}
