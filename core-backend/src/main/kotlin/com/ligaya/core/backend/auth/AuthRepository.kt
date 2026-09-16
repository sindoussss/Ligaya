package com.ligaya.core.backend.auth

/**
 * Implements LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 3's "Account: create / login / auth /
 * session" onboarding requirement. This is a thin domain contract over whichever auth provider
 * backs it (Firebase Auth here) — callers depend on this interface, not on Firebase types
 * directly, so the provider can change without touching call sites.
 */
interface AuthRepository {
    suspend fun signUp(email: String, password: String): AuthResult
    suspend fun logIn(email: String, password: String): AuthResult

    /**
     * ACCOUNT_ACTIONS_NEEDED.md item 6: "Continue with Google". [googleIdToken] is a real ID token
     * already obtained from the device's own Google account picker (Credential Manager, at the
     * composition root — this interface only ever sees the token, never anything Android-specific,
     * same separation as every other provider here) — this call never fabricates one. What it does
     * with that token differs by implementation: [com.ligaya.core.backend.auth.FirebaseAuthRepository]
     * exchanges it with Firebase; [com.ligaya.core.backend.auth.LocalAuthRepository] reads its own
     * claims locally, since it has no backend to exchange it with.
     */
    suspend fun signInWithGoogle(googleIdToken: String): AuthResult

    fun logOut()
    fun currentUserId(): String?

    /** The signed-in account's email address, or null when nobody is signed in. Settings shows it on the profile
     *  card; there is no other way to reach it, since [currentUserId] is an opaque id. */
    fun currentUserEmail(): String?
}

sealed interface AuthResult {
    data class Success(val userId: String) : AuthResult
    data class Failure(val message: String) : AuthResult
}
