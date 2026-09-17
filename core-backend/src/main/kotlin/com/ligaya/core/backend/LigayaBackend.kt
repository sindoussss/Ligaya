package com.ligaya.core.backend

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.ligaya.core.backend.auth.AuthRepository
import com.ligaya.core.backend.auth.FirebaseAuthRepository
import com.ligaya.core.backend.household.FirestoreSafetyCircleRepository
import com.ligaya.core.backend.household.SafetyCircleRepository

/**
 * Whether this build actually has a Firebase project behind it, and the repositories that only
 * exist when it does.
 *
 * The check is the presence of real Firebase config, not a flag someone remembered to set: the
 * `google-services` Gradle plugin (applied only when `app/google-services.json` exists — see
 * app/build.gradle.kts) generates the string resources [FirebaseApp.initializeApp] reads, and
 * that call returns null when they are absent instead of throwing. So an unconfigured build gets
 * [Unavailable] and says so on screen, and a configured one gets [Available] with no code change
 * anywhere. See ACCOUNT_ACTIONS_NEEDED.md item 1.
 */
sealed interface LigayaBackend {

    /** No Firebase project configured. Sign-in and the Safety Circle run on this phone only. */
    data object Unavailable : LigayaBackend

    data class Available(
        val authRepository: AuthRepository,
        val safetyCircleRepository: SafetyCircleRepository,
    ) : LigayaBackend

    companion object {
        fun resolve(context: Context): LigayaBackend {
            val app = runCatching { FirebaseApp.initializeApp(context.applicationContext) }.getOrNull()
                ?: return Unavailable
            return runCatching {
                Available(
                    authRepository = FirebaseAuthRepository(FirebaseAuth.getInstance(app)),
                    safetyCircleRepository = FirestoreSafetyCircleRepository(
                        firestore = FirebaseFirestore.getInstance(app),
                        functions = FirebaseFunctions.getInstance(app),
                    ),
                )
            }.getOrElse { Unavailable }
        }
    }
}
