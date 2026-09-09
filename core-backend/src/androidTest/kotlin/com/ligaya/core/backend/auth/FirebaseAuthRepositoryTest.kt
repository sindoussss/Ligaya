package com.ligaya.core.backend.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 3's account requirement against the real
 * local Firebase Auth emulator (no real Firebase project, login, or credentials involved — same
 * "demo-*" project pattern as backend/test/firestore.rules.test.mjs). Runs on a device/emulator
 * because it exercises the real Firebase Auth Android SDK, not just a mock.
 *
 * 10.0.2.2 is the Android emulator's standing alias for the host machine's loopback interface,
 * which is where `firebase emulators:start --only auth` listens.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseAuthRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newAuth(appName: String): FirebaseAuth {
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-ligaya-test")
            .setApplicationId("1:000000000000:android:0000000000000000000000")
            .setApiKey("fake-api-key-for-emulator-only")
            .build()
        val app = FirebaseApp.initializeApp(context, options, appName)
        return FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
    }

    private fun uniqueEmail() = "user-${System.currentTimeMillis()}-${(0..999999).random()}@test.ligaya.app"

    @Test
    fun signUpCreatesNewUser() = runTest {
        val repo = FirebaseAuthRepository(newAuth("signUpTest"))
        val result = repo.signUp(uniqueEmail(), "correcthorsebatterystaple")
        assertTrue("expected Success, got $result", result is AuthResult.Success)
        assertNotNull(repo.currentUserId())
    }

    @Test
    fun logInWithCorrectCredentialsSucceeds() = runTest {
        val repo = FirebaseAuthRepository(newAuth("logInSuccessTest"))
        val email = uniqueEmail()
        repo.signUp(email, "correcthorsebatterystaple")
        repo.logOut()
        assertNull(repo.currentUserId())

        val result = repo.logIn(email, "correcthorsebatterystaple")
        assertTrue("expected Success, got $result", result is AuthResult.Success)
        assertNotNull(repo.currentUserId())
    }

    @Test
    fun logInWithWrongPasswordIsRejected() = runTest {
        val repo = FirebaseAuthRepository(newAuth("logInRejectTest"))
        val email = uniqueEmail()
        repo.signUp(email, "correcthorsebatterystaple")
        repo.logOut()

        val result = repo.logIn(email, "the-wrong-password")
        assertTrue("expected Failure, got $result", result is AuthResult.Failure)
        assertNull(repo.currentUserId())
    }

    @Test
    fun logOutClearsCurrentUser() = runTest {
        val repo = FirebaseAuthRepository(newAuth("logOutTest"))
        repo.signUp(uniqueEmail(), "correcthorsebatterystaple")
        assertNotNull(repo.currentUserId())

        repo.logOut()
        assertNull(repo.currentUserId())
    }

    @Test
    fun sessionPersistsAcrossFreshFirebaseAuthInstance() = runTest {
        val appName = "sessionPersistTest"
        val repo1 = FirebaseAuthRepository(newAuth(appName))
        val email = uniqueEmail()
        repo1.signUp(email, "correcthorsebatterystaple")
        val uid = repo1.currentUserId()
        assertNotNull(uid)

        // Simulate the process restarting: tear down this FirebaseApp instance completely, then
        // create a brand-new one under the same name and see whether the signed-in session was
        // read back from persisted local storage rather than carried over in memory.
        FirebaseApp.getInstance(appName).delete()
        val repo2 = FirebaseAuthRepository(newAuth(appName))

        assertEquals(uid, repo2.currentUserId())
    }
}
