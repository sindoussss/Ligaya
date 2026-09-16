package com.ligaya.core.backend.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    override suspend fun signUp(email: String, password: String): AuthResult =
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            AuthResult.Success(result.user?.uid.orEmpty())
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Sign up failed")
        }

    override suspend fun logIn(email: String, password: String): AuthResult =
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            AuthResult.Success(result.user?.uid.orEmpty())
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Log in failed")
        }

    /** The standard Firebase exchange: a Google ID token becomes a Firebase credential, which either
     *  signs in an existing linked user or creates one — Firebase's own job, not this class's. */
    override suspend fun signInWithGoogle(googleIdToken: String): AuthResult =
        try {
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val result = auth.signInWithCredential(credential).await()
            AuthResult.Success(result.user?.uid.orEmpty())
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Google sign-in failed")
        }

    override fun logOut() = auth.signOut()

    override fun currentUserId(): String? = auth.currentUser?.uid

    override fun currentUserEmail(): String? = auth.currentUser?.email
}
