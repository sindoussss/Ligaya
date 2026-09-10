package com.ligaya.core.backend.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
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
}
