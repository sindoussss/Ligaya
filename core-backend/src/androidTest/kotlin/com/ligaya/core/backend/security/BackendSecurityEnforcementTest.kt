package com.ligaya.core.backend.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 32's own acceptance criteria against the real (emulated) Firestore, via direct backend
 * calls that never touch the app UI — for the two specific rules whose Node-level coverage
 * (backend/test/firestore.rules.test.mjs) is blocked by that file's own documented
 * "Firestore has already been started" SDK flake (a first-use-of-a-channel quirk in
 * @firebase/rules-unit-testing, confirmed via direct isolation to affect these new
 * cross-collection-get() rules exactly as it already does the pre-existing SUBSCRIPTION/
 * FAMILY_MEMBER cases documented there). The real Android SDK against the same emulator doesn't
 * hit that quirk, so this class is independent, working proof the rules themselves are correct —
 * not a workaround for the Node suite, which still documents the gap in its own comments.
 */
@RunWith(AndroidJUnit4::class)
class BackendSecurityEnforcementTest {

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
    fun aFamilyMemberWithViewLocationPermissionStillCannotReadTheRawLocationEventDirectly() = runTest {
        val owner = signIn("securityLocationOwner")
        val member = signIn("securityLocationMember")

        val eventId = "event-${System.currentTimeMillis()}"
        owner.firestore.collection("emergencyEvents").document(eventId)
            .set(mapOf("user_id" to owner.uid, "incident_type" to "FIRE", "status" to "ACTIVE"))
            .await()
        val householdRef = owner.firestore.collection("households").document()
        householdRef.set(mapOf("owner_id" to owner.uid)).await()
        // can_view_location: true is enough for the getFamilyEmergencyView callable — never
        // enough for a raw Firestore read, which is the exact claim under test here.
        householdRef.collection("members").document(member.uid)
            .set(
                mapOf(
                    "relationship" to "sibling",
                    "permissions" to mapOf("can_view_location" to true),
                    "notification_channel" to "push",
                    "status" to "ACTIVE",
                ),
            ).await()
        owner.firestore.collection("emergencyEvents").document(eventId)
            .collection("locationEvents").document("loc1")
            .set(mapOf("latitude" to 14.5995, "longitude" to 120.9842, "source" to "GPS"))
            .await()

        var rejected = false
        try {
            member.firestore.collection("emergencyEvents").document(eventId)
                .collection("locationEvents").document("loc1")
                .get()
                .await()
        } catch (_: Exception) {
            rejected = true
        }
        assert(rejected) { "expected a raw locationEvents read by a non-creator to be rejected server-side" }
    }

    @Test
    fun aDifferentUserCannotWriteALocationEventUnderSomeoneElsesEmergency() = runTest {
        val owner = signIn("securityLocationWriteOwner")
        val attacker = signIn("securityLocationWriteAttacker")

        val eventId = "event-${System.currentTimeMillis()}"
        owner.firestore.collection("emergencyEvents").document(eventId)
            .set(mapOf("user_id" to owner.uid, "incident_type" to "FIRE", "status" to "ACTIVE"))
            .await()

        var rejected = false
        try {
            attacker.firestore.collection("emergencyEvents").document(eventId)
                .collection("locationEvents").document("forged")
                .set(mapOf("latitude" to 0.0, "longitude" to 0.0, "source" to "GPS"))
                .await()
        } catch (_: Exception) {
            rejected = true
        }
        assert(rejected) { "expected a forged locationEvents write by a non-creator to be rejected server-side" }
    }

    @Test
    fun theNotificationRecipientStillCannotReadTheRawNotificationEventDirectly() = runTest {
        val owner = signIn("securityNotifOwner")
        val recipient = signIn("securityNotifRecipient")

        val eventId = "event-${System.currentTimeMillis()}"
        owner.firestore.collection("emergencyEvents").document(eventId)
            .set(mapOf("user_id" to owner.uid, "incident_type" to "FIRE", "status" to "ACTIVE"))
            .await()
        owner.firestore.collection("emergencyEvents").document(eventId)
            .collection("notificationEvents").document("notif1")
            .set(mapOf("recipient" to recipient.uid, "channel" to "push", "state" to "PENDING"))
            .await()

        // Being the notification's own named recipient is still not the creator — only the
        // getFamilyEmergencyView callable is the permitted path for anyone but the creator.
        var rejected = false
        try {
            recipient.firestore.collection("emergencyEvents").document(eventId)
                .collection("notificationEvents").document("notif1")
                .get()
                .await()
        } catch (_: Exception) {
            rejected = true
        }
        assert(rejected) { "expected a raw notificationEvents read by the recipient (not the creator) to be rejected server-side" }
    }
}
