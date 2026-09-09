package com.ligaya.core.backend.household

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ligaya.core.data.entity.FamilyMemberStatus
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers this step's acceptance criteria against the real local Auth + Firestore emulators (no
 * real Firebase project — same "demo-*" pattern as every other emulator-backed test in this
 * codebase): a member can be invited, join, and be removed; a non-member cannot read household
 * data via a direct backend call.
 */
@RunWith(AndroidJUnit4::class)
class SafetyCircleRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private data class SignedIn(val uid: String, val firestore: FirebaseFirestore)

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
        return SignedIn(uid, firestore)
    }

    @Test
    fun fullInviteAcceptRemoveFlow() = runTest {
        val owner = signIn("safetyCircleOwner")
        val invitee = signIn("safetyCircleInvitee")

        val ownerRepo: SafetyCircleRepository = FirestoreSafetyCircleRepository(owner.firestore)
        val inviteeRepo: SafetyCircleRepository = FirestoreSafetyCircleRepository(invitee.firestore)

        val householdId = ownerRepo.createHousehold(owner.uid)
        assertEquals(owner.uid, ownerRepo.getHousehold(householdId)?.ownerId)

        ownerRepo.inviteMember(householdId, invitee.uid, "sibling")
        val pending = inviteeRepo.getMember(householdId, invitee.uid)
        assertEquals(FamilyMemberStatus.PENDING, pending?.status)

        // The invitee can now also read the household itself — Step 8's extension.
        assertEquals(owner.uid, inviteeRepo.getHousehold(householdId)?.ownerId)

        inviteeRepo.acceptInvite(householdId, invitee.uid)
        val active = ownerRepo.getMember(householdId, invitee.uid)
        assertEquals(FamilyMemberStatus.ACTIVE, active?.status)

        ownerRepo.removeMember(householdId, invitee.uid)
        assertNull(inviteeRepo.getMember(householdId, invitee.uid))
    }

    @Test
    fun aNonMemberCannotReadTheHouseholdOrAnyMemberRecord() = runTest {
        val owner = signIn("safetyCircleNonMemberOwner")
        val outsider = signIn("safetyCircleOutsider")
        val invitee = signIn("safetyCircleNonMemberInvitee")

        val ownerRepo: SafetyCircleRepository = FirestoreSafetyCircleRepository(owner.firestore)
        val outsiderRepo: SafetyCircleRepository = FirestoreSafetyCircleRepository(outsider.firestore)

        val householdId = ownerRepo.createHousehold(owner.uid)
        ownerRepo.inviteMember(householdId, invitee.uid, "sibling")

        // outsider is a real, validly-authenticated user — just not a member of this household.
        var householdReadRejected = false
        try {
            outsiderRepo.getHousehold(householdId)
        } catch (_: Exception) {
            householdReadRejected = true
        }
        assertTrue("expected the household read to be rejected", householdReadRejected)

        var memberReadRejected = false
        try {
            outsiderRepo.getMember(householdId, invitee.uid)
        } catch (_: Exception) {
            memberReadRejected = true
        }
        assertTrue("expected the member-record read to be rejected", memberReadRejected)
    }

    @Test
    fun aUserCannotSelfInviteIntoSomeoneElsesHousehold() = runTest {
        val owner = signIn("safetyCircleSelfInviteOwner")
        val attacker = signIn("safetyCircleSelfInviteAttacker")

        val ownerRepo: SafetyCircleRepository = FirestoreSafetyCircleRepository(owner.firestore)
        val attackerRepo: SafetyCircleRepository = FirestoreSafetyCircleRepository(attacker.firestore)

        val householdId = ownerRepo.createHousehold(owner.uid)

        var selfInviteRejected = false
        try {
            attackerRepo.inviteMember(householdId, attacker.uid, "friend")
        } catch (_: Exception) {
            selfInviteRejected = true
        }
        assertTrue("expected the self-invitation to be rejected", selfInviteRejected)
    }
}
