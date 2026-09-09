package com.ligaya.core.backend.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ligaya.core.data.profile.EmergencyContact
import com.ligaya.core.data.profile.EmergencyProfile
import com.ligaya.core.data.profile.MedicalInfo
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real local Auth + Firestore emulators (no real Firebase project — same
 * "demo-*" pattern as every other emulator-backed test in this codebase), signed in as a real
 * test user so backend/firestore.rules' owner-only rule from Step 2 is genuinely exercised, not
 * bypassed via withSecurityRulesDisabled.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreUserProfileRepositoryTest {

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
    fun profileSyncsToFirestoreAndBackForTheSignedInOwner() = runTest {
        val firestore = signedInFirestore("profileSyncTest")
        val uid = FirebaseAuth.getInstance(firestore.app).currentUser!!.uid
        val repo: UserProfileRepository = FirestoreUserProfileRepository(firestore)

        val profile = EmergencyProfile(
            name = "Fatima",
            medicalInfo = MedicalInfo(bloodType = "AB+"),
            contacts = listOf(EmergencyContact(name = "Youssef", relationship = "parent")),
        )
        repo.saveEmergencyProfile(uid, profile)

        assertEquals(profile, repo.getEmergencyProfile(uid))
    }

    @Test
    fun skippedProfileSyncsAsTheEmptyDefault() = runTest {
        val firestore = signedInFirestore("profileSkipTest")
        val uid = FirebaseAuth.getInstance(firestore.app).currentUser!!.uid
        val repo: UserProfileRepository = FirestoreUserProfileRepository(firestore)

        repo.saveEmergencyProfile(uid, EmergencyProfile())

        assertEquals(EmergencyProfile(), repo.getEmergencyProfile(uid))
    }

    @Test
    fun ownDocumentNeverWrittenMeansNoProfileNotAnError() = runTest {
        val firestore = signedInFirestore("profileMissingTest")
        val uid = FirebaseAuth.getInstance(firestore.app).currentUser!!.uid
        val repo: UserProfileRepository = FirestoreUserProfileRepository(firestore)

        assertNull(repo.getEmergencyProfile(uid))
    }
}
