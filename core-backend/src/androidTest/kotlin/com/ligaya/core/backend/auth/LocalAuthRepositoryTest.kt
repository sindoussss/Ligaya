package com.ligaya.core.backend.auth

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The offline auth path is the one users will actually exercise until Firebase is configured, so
 * it gets the same treatment a real backend would: the failure cases are the point, not the happy
 * path. In particular [aWrongPasswordIsRejected] and [logInSurvivesANewRepositoryInstance] are what
 * separate a genuinely working implementation from one that just returns Success to move the UI on.
 */
@RunWith(AndroidJUnit4::class)
class LocalAuthRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newRepository() = LocalAuthRepository(context)

    @Before
    fun clearStoredAccounts() {
        context.getSharedPreferences("ligaya_local_auth", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun signUpCreatesAnAccountAndStartsASession() = runTest {
        val repository = newRepository()

        val result = repository.signUp("Maria@example.com", "correcthorsebatterystaple")

        assertTrue(result is AuthResult.Success)
        assertEquals((result as AuthResult.Success).userId, repository.currentUserId())
    }

    @Test
    fun signingUpTwiceWithTheSameEmailIsRejected() = runTest {
        val repository = newRepository()
        repository.signUp("maria@example.com", "correcthorsebatterystaple")

        val second = repository.signUp("maria@example.com", "anotherpassword")

        assertTrue("a duplicate email must not silently create a second account", second is AuthResult.Failure)
    }

    @Test
    fun emailIsTreatedCaseAndWhitespaceInsensitively() = runTest {
        val repository = newRepository()
        val signUp = repository.signUp("maria@example.com", "correcthorsebatterystaple") as AuthResult.Success

        val logIn = repository.logIn("  MARIA@Example.com  ", "correcthorsebatterystaple")

        assertTrue(logIn is AuthResult.Success)
        assertEquals(signUp.userId, (logIn as AuthResult.Success).userId)
    }

    @Test
    fun aWrongPasswordIsRejected() = runTest {
        val repository = newRepository()
        repository.signUp("maria@example.com", "correcthorsebatterystaple")

        val result = repository.logIn("maria@example.com", "notthepassword")

        assertTrue(result is AuthResult.Failure)
    }

    @Test
    fun loggingInToAnUnknownEmailFailsWithTheSameMessageAsAWrongPassword() = runTest {
        val repository = newRepository()
        repository.signUp("maria@example.com", "correcthorsebatterystaple")

        val unknownEmail = repository.logIn("nobody@example.com", "correcthorsebatterystaple") as AuthResult.Failure
        val wrongPassword = repository.logIn("maria@example.com", "notthepassword") as AuthResult.Failure

        // Distinguishable messages would let anyone enumerate which emails have accounts.
        assertEquals(unknownEmail.message, wrongPassword.message)
    }

    @Test
    fun logInSurvivesANewRepositoryInstance() = runTest {
        val created = newRepository().signUp("maria@example.com", "correcthorsebatterystaple") as AuthResult.Success

        // A separate instance, as a fresh app process would build.
        val result = newRepository().logIn("maria@example.com", "correcthorsebatterystaple")

        assertEquals(created.userId, (result as AuthResult.Success).userId)
    }

    @Test
    fun logOutEndsTheSessionButKeepsTheAccount() = runTest {
        val repository = newRepository()
        val created = repository.signUp("maria@example.com", "correcthorsebatterystaple") as AuthResult.Success

        repository.logOut()
        assertNull(repository.currentUserId())

        val backIn = repository.logIn("maria@example.com", "correcthorsebatterystaple")
        assertEquals(created.userId, (backIn as AuthResult.Success).userId)
    }

    @Test
    fun theStoredRecordNeverContainsTheRawPassword() = runTest {
        val password = "correcthorsebatterystaple"
        newRepository().signUp("maria@example.com", password)

        val stored = context.getSharedPreferences("ligaya_local_auth", Context.MODE_PRIVATE)
            .all
            .values
            .joinToString(" ") { it.toString() }

        assertTrue("nothing should have been written at all if this is empty", stored.isNotEmpty())
        assertTrue("the raw password must never be recoverable from storage", !stored.contains(password))
    }

    @Test
    fun twoAccountsWithTheSamePasswordProduceDifferentStoredHashes() = runTest {
        val repository = newRepository()
        repository.signUp("maria@example.com", "correcthorsebatterystaple")
        repository.signUp("juan@example.com", "correcthorsebatterystaple")

        val preferences = context.getSharedPreferences("ligaya_local_auth", Context.MODE_PRIVATE)
        val maria = preferences.getString("account:maria@example.com", null)
        val juan = preferences.getString("account:juan@example.com", null)

        // Per-account salting: identical passwords must not produce identical stored records, or a
        // single cracked hash would unlock every account that shares that password.
        assertNotEquals(maria, juan)
    }


    /** A structurally real Google ID token: header.payload.signature, base64url, the two claims
     *  this repository actually reads. The signature segment is never checked (see
     *  LocalAuthRepository.signInWithGoogle's own doc comment on why), so any bytes there prove
     *  the same thing a real one would for this class's purposes. */
    private fun fakeGoogleIdToken(email: String?, expiresInSeconds: Long = 3600): String {
        fun segment(json: String) = Base64.encodeToString(
            json.toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val header = segment("""{"alg":"RS256","typ":"JWT"}""")
        val payload = JSONObject().apply {
            if (email != null) put("email", email)
            put("exp", System.currentTimeMillis() / 1000 + expiresInSeconds)
        }
        return "$header.${segment(payload.toString())}.signature"
    }

    @Test
    fun signInWithGoogleCreatesAnAccountAndStartsASession() = runTest {
        val repository = newRepository()

        val result = repository.signInWithGoogle(fakeGoogleIdToken("maria@example.com"))

        assertTrue(result is AuthResult.Success)
        assertEquals((result as AuthResult.Success).userId, repository.currentUserId())
        assertEquals("maria@example.com", repository.currentUserEmail())
    }

    @Test
    fun signingInWithGoogleTwiceReturnsTheSameAccountRatherThanCreatingASecondOne() = runTest {
        val repository = newRepository()
        val first = repository.signInWithGoogle(fakeGoogleIdToken("maria@example.com")) as AuthResult.Success

        repository.logOut()
        val second = repository.signInWithGoogle(fakeGoogleIdToken("maria@example.com"))

        assertEquals(first.userId, (second as AuthResult.Success).userId)
    }

    @Test
    fun googleSignInIsCaseAndWhitespaceInsensitiveOnEmailLikeEveryOtherPath() = runTest {
        val repository = newRepository()
        val first = repository.signInWithGoogle(fakeGoogleIdToken("maria@example.com")) as AuthResult.Success

        val second = repository.signInWithGoogle(fakeGoogleIdToken("  MARIA@Example.com  "))

        assertEquals(first.userId, (second as AuthResult.Success).userId)
    }

    @Test
    fun googleSignInRefusesToTakeOverAnExistingPasswordAccount() = runTest {
        val repository = newRepository()
        repository.signUp("maria@example.com", "correcthorsebatterystaple")

        val result = repository.signInWithGoogle(fakeGoogleIdToken("maria@example.com"))

        assertTrue("a password account must never be silently linked to Google", result is AuthResult.Failure)
        // And the password account itself is untouched: it can still log in normally.
        val stillWorks = repository.logIn("maria@example.com", "correcthorsebatterystaple")
        assertTrue(stillWorks is AuthResult.Success)
    }

    @Test
    fun aPasswordAttemptAgainstAGoogleAccountIsRefusedWithAnHonestMessageNotAGenericWrongPassword() = runTest {
        val repository = newRepository()
        repository.signInWithGoogle(fakeGoogleIdToken("maria@example.com"))

        val result = repository.logIn("maria@example.com", "anything") as AuthResult.Failure

        assertTrue(result.message.contains("Google", ignoreCase = true))
    }

    @Test
    fun anExpiredGoogleTokenIsRejected() = runTest {
        val repository = newRepository()

        val result = repository.signInWithGoogle(fakeGoogleIdToken("maria@example.com", expiresInSeconds = -60))

        assertTrue(result is AuthResult.Failure)
        assertNull(repository.currentUserId())
    }

    @Test
    fun aTokenWithNoEmailClaimIsRejected() = runTest {
        val repository = newRepository()

        val result = repository.signInWithGoogle(fakeGoogleIdToken(email = null))

        assertTrue(result is AuthResult.Failure)
    }

    @Test
    fun aMalformedTokenIsRejectedRatherThanCrashing() = runTest {
        val repository = newRepository()

        val result = repository.signInWithGoogle("not-a-real-token")

        assertTrue(result is AuthResult.Failure)
    }

    @Test
    fun googleSignInSurvivesANewRepositoryInstance() = runTest {
        val created = newRepository().signInWithGoogle(fakeGoogleIdToken("maria@example.com")) as AuthResult.Success

        val result = newRepository().signInWithGoogle(fakeGoogleIdToken("maria@example.com"))

        assertEquals(created.userId, (result as AuthResult.Success).userId)
    }
}
