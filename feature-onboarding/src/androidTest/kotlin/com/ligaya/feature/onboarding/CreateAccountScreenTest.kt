package com.ligaya.feature.onboarding

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.backend.auth.AuthRepository
import com.ligaya.core.backend.auth.AuthResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design screen 3. The screen's job is to not waste the provider's time on input it can
 * already tell is wrong, and to report the provider's answer honestly when it does call — so the
 * tests are mostly about what does *not* reach [AuthRepository].
 */
@RunWith(AndroidJUnit4::class)
class CreateAccountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingAuthRepository(
        private val result: AuthResult = AuthResult.Success("user-1"),
    ) : AuthRepository {
        var signUpCalls = 0
            private set
        var logInCalls = 0
            private set
        var lastEmail: String? = null
            private set

        override suspend fun signUp(email: String, password: String): AuthResult {
            signUpCalls++
            lastEmail = email
            return result
        }

        override suspend fun logIn(email: String, password: String): AuthResult {
            logInCalls++
            lastEmail = email
            return result
        }

        override fun logOut() = Unit
        override fun currentUserId(): String? = null
    }


    /**
     * Typing needs two things that are easy to get wrong here, and both cost real debugging time:
     *
     * 1. The node must be the actual editable one. LigayaTextField wraps BasicTextField in its own
     *    semantics node (which carries the permanent accessibility label), so matching by that
     *    label finds the wrapper — and typing into the wrapper does nothing, silently, without
     *    throwing. [hasSetTextAction] always lands on the real input.
     * 2. It must be focused first. `performTextInput` on an unfocused field is also a silent
     *    no-op, which presents identically to the input simply not working.
     *
     * Index is layout order: email, then password.
     */
    /**
     * Scrolls before clicking. The form is taller than the viewport, so its primary CTA sits below
     * the fold — and Compose's performClick on an off-screen node lands nowhere without throwing,
     * which reads exactly like the button being wired up wrong.
     */
    private fun tapButton(label: String) {
        composeTestRule.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun typeEmail(text: String) = typeInto(index = 0, text = text)

    private fun typePassword(text: String) = typeInto(index = 1, text = text)

    private fun typeInto(index: Int, text: String) {
        val field = composeTestRule.onAllNodes(hasSetTextAction())[index]
        field.requestFocus()
        field.performTextInput(text)
    }

    @Test
    fun aMalformedEmailIsRejectedWithoutEverCallingTheProvider() {
        val auth = RecordingAuthRepository()
        composeTestRule.setContent {
            CreateAccountScreen(authRepository = auth, onAuthenticated = {}, onBack = {})
        }

        typeEmail("not-an-email")
        typePassword("correcthorsebattery")
        tapButton("Create account")
        composeTestRule.waitForIdle()

        assertEquals("a client-side format failure must not cost a round trip", 0, auth.signUpCalls)
        composeTestRule.onNodeWithText("That doesn't look like an email address.").assertExists()
    }

    @Test
    fun aShortPasswordIsRejectedOnSignUpWithoutCallingTheProvider() {
        val auth = RecordingAuthRepository()
        composeTestRule.setContent {
            CreateAccountScreen(authRepository = auth, onAuthenticated = {}, onBack = {})
        }

        typeEmail("maria@example.com")
        typePassword("short")
        tapButton("Create account")
        composeTestRule.waitForIdle()

        assertEquals(0, auth.signUpCalls)
        composeTestRule.onNodeWithText("Use at least $MINIMUM_PASSWORD_LENGTH characters.").assertExists()
    }

    @Test
    fun validInputSignsUpAndReportsTheAuthenticatedUser() {
        val auth = RecordingAuthRepository(AuthResult.Success("user-42"))
        var authenticatedUserId: String? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                authRepository = auth,
                onAuthenticated = { authenticatedUserId = it },
                onBack = {},
            )
        }

        typeEmail("  Maria@example.com ")
        typePassword("correcthorsebattery")
        tapButton("Create account")
        composeTestRule.waitForIdle()

        assertEquals(1, auth.signUpCalls)
        assertEquals("the email must be trimmed before it reaches the provider", "Maria@example.com", auth.lastEmail)
        assertEquals("user-42", authenticatedUserId)
    }

    @Test
    fun aProviderFailureIsShownAndDoesNotReportAuthentication() {
        val auth = RecordingAuthRepository(AuthResult.Failure("That email already has an account."))
        var authenticatedUserId: String? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                authRepository = auth,
                onAuthenticated = { authenticatedUserId = it },
                onBack = {},
            )
        }

        typeEmail("maria@example.com")
        typePassword("correcthorsebattery")
        tapButton("Create account")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("That email already has an account.").assertExists()
        assertNull("a failed sign-up must never advance onboarding", authenticatedUserId)
    }

    @Test
    fun switchingToLogInCallsLogInRatherThanSignUp() {
        val auth = RecordingAuthRepository()
        composeTestRule.setContent {
            CreateAccountScreen(authRepository = auth, onAuthenticated = {}, onBack = {})
        }

        tapButton("Already have an account?  Log in")
        composeTestRule.waitForIdle()

        typeEmail("maria@example.com")
        typePassword("correcthorsebattery")
        tapButton("Log in")
        composeTestRule.waitForIdle()

        assertEquals(1, auth.logInCalls)
        assertEquals(0, auth.signUpCalls)
    }

    /** Log-in must not apply the sign-up length rule, or an older short password locks its owner out. */
    @Test
    fun logInAcceptsAShortPasswordAndLetsTheProviderDecide() {
        val auth = RecordingAuthRepository(AuthResult.Failure("That email or password doesn't match an account."))
        composeTestRule.setContent {
            CreateAccountScreen(authRepository = auth, onAuthenticated = {}, onBack = {})
        }

        tapButton("Already have an account?  Log in")
        composeTestRule.waitForIdle()
        typeEmail("maria@example.com")
        typePassword("short")
        tapButton("Log in")
        composeTestRule.waitForIdle()

        assertEquals("the provider, not the form, decides whether an existing password is valid", 1, auth.logInCalls)
    }

    @Test
    fun theShowToggleRevealsAndRehidesThePassword() {
        composeTestRule.setContent {
            CreateAccountScreen(authRepository = RecordingAuthRepository(), onAuthenticated = {}, onBack = {})
        }

        composeTestRule.onNodeWithContentDescription("Show password").performClick()
        composeTestRule.onNodeWithContentDescription("Hide password").assertExists()

        composeTestRule.onNodeWithContentDescription("Hide password").performClick()
        composeTestRule.onNodeWithContentDescription("Show password").assertExists()
    }

    @Test
    fun anUnconfiguredIdentityProviderSaysSoRatherThanFailingSilently() {
        val auth = RecordingAuthRepository()
        composeTestRule.setContent {
            CreateAccountScreen(authRepository = auth, onAuthenticated = {}, onBack = {})
        }

        tapButton("Continue with Google")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Google sign-in isn't set up yet. Use your email and password for now.")
            .assertExists()
        assertTrue("an unconfigured provider must not reach the email/password path", auth.signUpCalls == 0)
    }
}
