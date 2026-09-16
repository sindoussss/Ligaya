package com.ligaya.core.backend.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
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
            .putString(accountKey(normalised), encodeRecord(PROVIDER_LOCAL, salt, derive(password, salt), userId))
            .putString(SESSION_KEY, userId)
            .putString(SESSION_EMAIL_KEY, normalised)
            .apply()
        return AuthResult.Success(userId)
    }

    override suspend fun logIn(email: String, password: String): AuthResult {
        val record = preferences.getString(accountKey(normalise(email)), null)
            // Deliberately the same message as a wrong password below: telling an unauthenticated
            // caller which of the two was wrong tells them whether an email is registered.
            ?: return AuthResult.Failure(INVALID_CREDENTIALS)
        val decoded = decodeRecord(record) ?: return AuthResult.Failure(INVALID_CREDENTIALS)
        if (decoded.provider == PROVIDER_GOOGLE) {
            // No password was ever set for this account (see signInWithGoogle) — the stored hash is
            // random and deliberately unmatchable, so this check exists to give an honest message
            // instead of a generic "wrong password" for something that was never set to begin with.
            return AuthResult.Failure("This account uses Google sign-in. Continue with Google instead.")
        }

        if (!MessageDigest.isEqual(derive(password, decoded.salt), decoded.hash)) {
            return AuthResult.Failure(INVALID_CREDENTIALS)
        }
        preferences.edit()
            .putString(SESSION_KEY, decoded.userId)
            .putString(SESSION_EMAIL_KEY, normalise(email))
            .apply()
        return AuthResult.Success(decoded.userId)
    }

    /**
     * ACCOUNT_ACTIONS_NEEDED.md item 6. [googleIdToken] already came from the device's own Google
     * account picker (Credential Manager, at the composition root) — the user genuinely chose a
     * real Google account to get here. What this method does *not* do: cryptographically verify
     * the token's signature against Google's rotating public keys, which needs either a backend
     * (Firebase does this — see [FirebaseAuthRepository]'s own implementation) or a JWT-verification
     * library neither of which this on-device fallback has. It does check the token is well-formed
     * and unexpired, and reads the account's real email from it — not a fabricated one.
     *
     * A returning Google account logs back into its own local account; a new one creates one. An
     * email that already has a *password* account is refused rather than silently taken over — this
     * repository has no way to confirm the person tapping "Continue with Google" also owns that
     * password, so linking them would mean trusting the Google picker to authorize access to an
     * account it was never used to create.
     */
    override suspend fun signInWithGoogle(googleIdToken: String): AuthResult {
        val claims = decodeGoogleIdTokenClaims(googleIdToken)
            ?: return AuthResult.Failure("That didn't look like a valid Google sign-in. Please try again.")
        val email = claims.email
            ?: return AuthResult.Failure("Your Google account has no email address to sign in with.")
        val normalised = normalise(email)

        val existing = preferences.getString(accountKey(normalised), null)?.let(::decodeRecord)
        if (existing != null) {
            if (existing.provider != PROVIDER_GOOGLE) {
                return AuthResult.Failure(
                    "This email already has a password-based Ligaya account. Log in with your password instead.",
                )
            }
            preferences.edit().putString(SESSION_KEY, existing.userId).putString(SESSION_EMAIL_KEY, normalised).apply()
            return AuthResult.Success(existing.userId)
        }

        // A brand-new account. No password was ever set, so what's stored here can never be derived
        // from or compared against one — logIn() below refuses password attempts on a Google account
        // before this value is ever read.
        val userId = UUID.randomUUID().toString()
        val placeholderSalt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val placeholderHash = ByteArray(KEY_LENGTH_BITS / 8).also { SecureRandom().nextBytes(it) }
        preferences.edit()
            .putString(accountKey(normalised), encodeRecord(PROVIDER_GOOGLE, placeholderSalt, placeholderHash, userId))
            .putString(SESSION_KEY, userId)
            .putString(SESSION_EMAIL_KEY, normalised)
            .apply()
        return AuthResult.Success(userId)
    }

    override fun logOut() {
        // Clears the session only. Removing the account records here would mean logging out
        // silently deleted the account, which is not what logging out means anywhere else.
        preferences.edit().remove(SESSION_KEY).remove(SESSION_EMAIL_KEY).apply()
    }

    override fun currentUserId(): String? = preferences.getString(SESSION_KEY, null)

    /** Stored with the session rather than derived: the account records are keyed BY email, so finding it
     *  otherwise would mean scanning every stored account. */
    override fun currentUserEmail(): String? =
        preferences.getString(SESSION_KEY, null)?.let { preferences.getString(SESSION_EMAIL_KEY, null) }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    /** Emails are case- and whitespace-insensitive as identifiers, as every auth provider treats them. */
    private fun normalise(email: String) = email.trim().lowercase()

    private fun accountKey(normalisedEmail: String) = "$ACCOUNT_KEY_PREFIX$normalisedEmail"

    /** [provider] omitted (three-field records) means [PROVIDER_LOCAL] — every account created before
     *  Google sign-in existed, read back exactly as it always was. */
    private fun encodeRecord(provider: String, salt: ByteArray, hash: ByteArray, userId: String): String =
        listOf(provider, encode(salt), encode(hash), userId).joinToString(RECORD_SEPARATOR)

    private fun decodeRecord(record: String): AccountRecord? {
        val parts = record.split(RECORD_SEPARATOR)
        return when (parts.size) {
            3 -> AccountRecord(PROVIDER_LOCAL, decode(parts[0]), decode(parts[1]), parts[2])
            4 -> AccountRecord(parts[0], decode(parts[1]), decode(parts[2]), parts[3])
            else -> null
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private data class AccountRecord(val provider: String, val salt: ByteArray, val hash: ByteArray, val userId: String)

    /** Reads the two claims this repository actually needs, without verifying the token's signature
     *  (see [signInWithGoogle]'s own doc comment on why not). Malformed input, an unparseable
     *  payload, or a token whose `exp` has already passed all return null alike — none of them is a
     *  real sign-in attempt worth a more specific message. */
    private fun decodeGoogleIdTokenClaims(idToken: String): GoogleIdTokenClaims? {
        val segments = idToken.split(".")
        if (segments.size != 3) return null
        return try {
            val payload = Base64.decode(segments[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val json = JSONObject(String(payload, Charsets.UTF_8))
            val expiresAtEpochSeconds = json.optLong("exp", -1L)
            if (expiresAtEpochSeconds in 0..(System.currentTimeMillis() / 1000)) return null
            GoogleIdTokenClaims(email = json.optString("email", "").takeIf { it.isNotBlank() })
        } catch (_: Exception) {
            null
        }
    }

    private data class GoogleIdTokenClaims(val email: String?)

    private companion object {
        const val PREFERENCES_NAME = "ligaya_local_auth"
        const val ACCOUNT_KEY_PREFIX = "account:"
        const val SESSION_KEY = "session_user_id"

        /** The signed-in account's email, kept beside the session so Settings can show it: the account records
         *  are keyed BY email, so there is otherwise no way to find it without scanning them all. */
        const val SESSION_EMAIL_KEY = "session_email"
        const val RECORD_SEPARATOR = ":"
        const val PROVIDER_LOCAL = "local"
        const val PROVIDER_GOOGLE = "google"
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH_BYTES = 16
        const val INVALID_CREDENTIALS = "That email or password doesn't match an account."
    }
}
