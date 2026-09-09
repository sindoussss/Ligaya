package com.ligaya.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the FCM token actually lands on the user's own backend record, against the real
 * local Auth + Firestore emulators — no real Firebase project, login, or credentials involved
 * (same "demo-*" pattern as every other emulator-backed test in this codebase). Signed in as a
 * real test user so the `users/{userId}` owner-only rule (Step 2) is genuinely exercised, not
 * bypassed — matching core-backend's FirestoreUserProfileRepositoryTest.
 */
@RunWith(AndroidJUnit4::class)
class FirestorePushTokenRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private suspend fun signedInFirestore(appName: String): FirebaseFirestore {
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-ligaya-test")
            .setApplicationId("1:000000000000:android:0000000000000000000000")
            .setApiKey("fake-api-key-for-emulator-only")
            .build()
        val app = FirebaseApp.initializeApp(context, options, appName)

        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
        val email = "user-${System.currentTimeMillis()}-${(0..999999).random()}@test.ligaya.app"
        auth.createUserWithEmailAndPassword(email, "correcthorsebatterystaple").await()

        return FirebaseFirestore.getInstance(app).apply { useEmulator("10.0.2.2", 8090) }
    }

    @Test
    fun saveTokenWritesFcmTokenOntoTheUsersOwnDocument() = runTest {
        val firestore = signedInFirestore("saveTokenTest")
        val uid = FirebaseAuth.getInstance(firestore.app).currentUser!!.uid
        val repository = FirestorePushTokenRepository(firestore)

        repository.saveToken(uid, "token-abc-123")

        val snapshot = firestore.collection("users").document(uid).get().await()
        assertEquals("token-abc-123", snapshot.getString("fcm_token"))
    }

    @Test
    fun saveTokenMergesRatherThanOverwritingOtherFields() = runTest {
        val firestore = signedInFirestore("saveTokenMergeTest")
        val uid = FirebaseAuth.getInstance(firestore.app).currentUser!!.uid
        val repository = FirestorePushTokenRepository(firestore)
        firestore.collection("users").document(uid).set(mapOf("display_name" to "Ligaya User")).await()

        repository.saveToken(uid, "token-xyz-789")

        val snapshot = firestore.collection("users").document(uid).get().await()
        assertEquals("token-xyz-789", snapshot.getString("fcm_token"))
        assertEquals("Ligaya User", snapshot.getString("display_name"))
    }
}
