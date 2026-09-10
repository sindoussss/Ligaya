package com.ligaya.core.backend.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A real, working [AuthRepository] that needs no Firebase project.
 *
 * [FirebaseAuthRepository] is the intended production implementation, but it cannot be constructed
 * at all without a `google-services.json` — which this repo does not have and cannot have until the
 * project owner creates a Firebase project (ACCOUNT_ACTIONS_NEEDED.md item 1). Wiring it anyway
 * would crash the app at launch. This is the same degradation pattern the codebase already uses at
 * its other account-gated seams: a real Gemini provider when a key exists, NullIntentProvider when
 * it doesn't; the Places flow constructed only when its key is non-blank.
 *
 * Unlike those, though, this is not a no-op stand-in — accounts genuinely persist, duplicate emails
 * are genuinely rejected, and a wrong password genuinely fails. That matters because sign-up and
 * log-in are the one part of onboarding whose whole point is that it remembers you; a fake that
 * accepted anything would make the screen impossible to evaluate.
 *
 * **On storing credentials on-device:** passwords are never stored, in any form that can be read
 * back. Each account gets a fresh random salt and the password is stretched with PBKDF2-HMAC-SHA256
 * ([ITERATIONS] rounds) before only the derived hash is written. Verification re-derives and
 * compares in constant time ([MessageDigest.isEqual]) so a wrong password can't be narrowed down by
 * timing. This is deliberately stronger than a local development stand-in strictly needs, because
 * "temporary" auth code has a habit of outliving its excuse, and this is a personal-safety app.
 *
 * Once Firebase is configured this class stops being constructed — the composition root picks the
 * implementation, and nothing else in the app knows which one it got.
 */
class LocalAuthRepository(context: Context) : AuthRepository {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun signUp(email: String, password: String): AuthResult {
        val normalised = normalise(email)
        if (preferences.contains(accountKey(normalised))) {
            return AuthResult.Failure("That email already has an account. Log in instead.")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val userId = UUID.randomUUID().toString()
        preferences.edit()
            .putString(accountKey(normalised), encodeRecord(salt, derive(password, salt), userId))
            .putString(SESSION_KEY, userId)
            .apply()
        return AuthResult.Success(userId)
    }

    override suspend fun logIn(email: String, password: String): AuthResult {
        val record = preferences.getString(accountKey(normalise(email)), null)
            // Deliberately the same message as a wrong password below: telling an unauthenticated
            // caller which of the two was wrong tells them whether an email is registered.
            ?: return AuthResult.Failure(INVALID_CREDENTIALS)
        val (salt, storedHash, userId) = decodeRecord(record) ?: return AuthResult.Failure(INVALID_CREDENTIALS)

        if (!MessageDigest.isEqual(derive(password, salt), storedHash)) {
            return AuthResult.Failure(INVALID_CREDENTIALS)
        }
        preferences.edit().putString(SESSION_KEY, userId).apply()
        return AuthResult.Success(userId)
    }

    override fun logOut() {
        // Clears the session only. Removing the account records here would mean logging out
        // silently deleted the account, which is not what logging out means anywhere else.
        preferences.edit().remove(SESSION_KEY).apply()
    }

    override fun currentUserId(): String? = preferences.getString(SESSION_KEY, null)

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    /** Emails are case- and whitespace-insensitive as identifiers, as every auth provider treats them. */
    private fun normalise(email: String) = email.trim().lowercase()

    private fun accountKey(normalisedEmail: String) = "$ACCOUNT_KEY_PREFIX$normalisedEmail"

    private fun encodeRecord(salt: ByteArray, hash: ByteArray, userId: String): String =
        listOf(encode(salt), encode(hash), userId).joinToString(RECORD_SEPARATOR)

    private fun decodeRecord(record: String): Triple<ByteArray, ByteArray, String>? {
        val parts = record.split(RECORD_SEPARATOR)
        if (parts.size != 3) return null
        return Triple(decode(parts[0]), decode(parts[1]), parts[2])
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES_NAME = "ligaya_local_auth"
        const val ACCOUNT_KEY_PREFIX = "account:"
        const val SESSION_KEY = "session_user_id"
        const val RECORD_SEPARATOR = ":"
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH_BYTES = 16
        const val INVALID_CREDENTIALS = "That email or password doesn't match an account."
    }
}
