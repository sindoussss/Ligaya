package com.ligaya.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.backend.auth.AuthRepository
import com.ligaya.core.backend.auth.AuthResult
import com.ligaya.core.data.profile.EmergencyProfile
import com.ligaya.core.data.profile.EmergencyProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 42's own acceptance criterion: "user can complete onboarding skipping every optional
 * field." A fake [AuthRepository] always succeeds (the account step itself is not optional — see
 * OnboardingScreen's own doc comment — this test's focus is the profile step after it), and a
 * fake [EmergencyProfileRepository] records exactly what gets saved, proving "Skip for now" saves
 * the same empty [EmergencyProfile] state Step 5 itself defined as "explicitly skipped" and still
 * reaches [onOnboardingComplete][com.ligaya.feature.onboarding.OnboardingScreen] — not stuck, not
 * blocked, not requiring any field.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeAuthRepository : AuthRepository {
        override suspend fun signUp(email: String, password: String): AuthResult = AuthResult.Success("test-user-id")
        override suspend fun logIn(email: String, password: String): AuthResult = AuthResult.Success("test-user-id")
        override suspend fun signInWithGoogle(googleIdToken: String): AuthResult = AuthResult.Success("test-user-id")
        override fun logOut() = Unit
        override fun currentUserId(): String? = null
        override fun currentUserEmail(): String? = null
    }

    private class FakeEmergencyProfileRepository : EmergencyProfileRepository {
        var savedUserId: String? = null
            private set
        var savedProfile: EmergencyProfile? = null
            private set

        override suspend fun getProfile(userId: String): EmergencyProfile? = null

        override suspend fun saveProfile(userId: String, profile: EmergencyProfile) {
            savedUserId = userId
            savedProfile = profile
        }
    }

    @Test
    fun userCanCompleteOnboardingSkippingEveryOptionalProfileField() {
        val profileRepository = FakeEmergencyProfileRepository()
        var completed = false

        composeTestRule.setContent {
            OnboardingScreen(
                authRepository = FakeAuthRepository(),
                profileRepository = profileRepository,
                onOnboardingComplete = { completed = true },
            )
        }

        composeTestRule.onNodeWithTag("onboardingEmailField").performTextInput("user@example.com")
        composeTestRule.onNodeWithTag("onboardingPasswordField").performTextInput("hunter22")
        composeTestRule.onNodeWithTag("onboardingAccountSubmit").performClick()

        composeTestRule.onNodeWithTag("onboardingSkip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboardingSkip").performClick()

        assertTrue("expected onOnboardingComplete to fire after Skip", completed)
        assertEquals("test-user-id", profileRepository.savedUserId)
        assertEquals(EmergencyProfile(), profileRepository.savedProfile)
    }

    @Test
    fun userCanCompleteOnboardingAfterFillingOptionalFields() {
        val profileRepository = FakeEmergencyProfileRepository()
        var completed = false

        composeTestRule.setContent {
            OnboardingScreen(
                authRepository = FakeAuthRepository(),
                profileRepository = profileRepository,
                onOnboardingComplete = { completed = true },
            )
        }

        composeTestRule.onNodeWithTag("onboardingEmailField").performTextInput("user@example.com")
        composeTestRule.onNodeWithTag("onboardingPasswordField").performTextInput("hunter22")
        composeTestRule.onNodeWithTag("onboardingAccountSubmit").performClick()

        composeTestRule.onNodeWithTag("onboardingNameField").performTextInput("Juana")
        composeTestRule.onNodeWithTag("onboardingSaveAndContinue").performClick()

        assertTrue("expected onOnboardingComplete to fire after Save & Continue", completed)
        assertEquals("Juana", profileRepository.savedProfile?.name)
    }
}
