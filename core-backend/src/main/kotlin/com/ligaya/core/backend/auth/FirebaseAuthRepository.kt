package com.ligaya.core.backend.auth

import com.google.firebase.auth.FirebaseAuth
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

    override fun logOut() = auth.signOut()

    override fun currentUserId(): String? = auth.currentUser?.uid

    override fun currentUserEmail(): String? = auth.currentUser?.email
}
