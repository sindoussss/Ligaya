package com.ligaya.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ligaya.core.backend.auth.AuthRepository
import com.ligaya.core.backend.auth.AuthResult
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaBackButton
import com.ligaya.designsystem.components.LigayaPrimaryButton
import com.ligaya.designsystem.components.LigayaSecondaryButton
import com.ligaya.designsystem.components.LigayaTextField
import kotlinx.coroutines.launch

/**
 * Screen 3: §3's "Account: create / login / auth / session", the one onboarding step the
 * architecture doc marks as required rather than skippable.
 *
 * This screen validates locally *before* calling [AuthRepository], and surfaces provider failures
 * per-field where it can tell which field is at fault. That split matters: a malformed email should
 * never cost a network round trip, but "this email is already registered" is knowledge only the
 * provider has, so it can only ever arrive as a response.
 *
 * The whole screen is one composable driving one [AuthRepository]; whether that repository is
 * Firebase or the on-device fallback is decided at the composition root and is deliberately
 * invisible here (see LocalAuthRepository's own doc comment).
 */
@Composable
fun CreateAccountScreen(
    authRepository: AuthRepository,
    onAuthenticated: (userId: String) -> Unit,
    onBack: () -> Unit,
    // ACCOUNT_ACTIONS_NEEDED.md item 6. Defaulted so every existing caller of this screen — this
    // module's own tests included — keeps compiling unchanged; only LigayaNavHost's real call site
    // needs to pass the live Credential Manager flow.
    onGoogleSignIn: suspend () -> AuthResult = { AuthResult.Failure(GOOGLE_UNAVAILABLE) },
    googleSignInAvailable: Boolean = false,
    // Welcome's "I already have an account" link opens straight into log-in mode rather than
    // making a returning user switch it themselves; every other caller still opens on sign-up.
    initialMode: AccountMode = AccountMode.SIGN_UP,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Real once googleSignInAvailable is true (ACCOUNT_ACTIONS_NEEDED.md item 6) — the button still
    // says plainly it is not set up otherwise, rather than launching a picker guaranteed to fail.
    fun signInWithGoogle() {
        if (!googleSignInAvailable) {
            formError = GOOGLE_UNAVAILABLE
            return
        }
        formError = null
        submitting = true
        scope.launch {
            when (val result = onGoogleSignIn()) {
                is AuthResult.Success -> {
                    submitting = false
                    onAuthenticated(result.userId)
                }
                is AuthResult.Failure -> {
                    submitting = false
                    // An empty message means the person closed the picker themselves — not
                    // something to show as an error.
                    if (result.message.isNotBlank()) formError = result.message
                }
            }
        }
    }

    fun submit() {
        emailError = validateEmail(email)
        // Only sign-up enforces a minimum length: applying it at log-in would lock out an account
        // whose password predates the rule, and would leak that rule to someone guessing.
        passwordError = validatePassword(password, enforceStrength = mode == AccountMode.SIGN_UP)
        formError = null
        if (emailError != null || passwordError != null) return

        submitting = true
        scope.launch {
            val result = when (mode) {
                AccountMode.SIGN_UP -> authRepository.signUp(email.trim(), password)
                AccountMode.LOG_IN -> authRepository.logIn(email.trim(), password)
            }
            submitting = false
            when (result) {
                is AuthResult.Success -> onAuthenticated(result.userId)
                is AuthResult.Failure -> formError = result.message
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LigayaSpacing.lg),
    ) {
        LigayaBackButton(onClick = onBack)

        // Small brand lockup, echoing the splash so the account step still feels like Ligaya's
        // rather than a generic form.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LigayaSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LigayaLogo(modifier = Modifier.size(LOCKUP_LOGO_SIZE))
            Text(
                text = "LIGAYA",
                style = LigayaTypography.wordmark.copy(fontSize = LOCKUP_WORDMARK_SIZE, letterSpacing = LOCKUP_TRACKING),
                color = LigayaTheme.colors.ink,
                modifier = Modifier.padding(top = LigayaSpacing.sm),
            )
        }

        Spacer(modifier = Modifier.height(LigayaSpacing.xl))

        Text(
            text = if (mode == AccountMode.SIGN_UP) "Create your account" else "Welcome back",
            style = LigayaTypography.headline,
            color = LigayaTheme.colors.ink,
        )
        Text(
            text = if (mode == AccountMode.SIGN_UP) {
                "Continue with your email or phone."
            } else {
                "Log in to pick up where you left off."
            },
            style = LigayaTypography.body,
            color = LigayaTheme.colors.inkSoft,
            modifier = Modifier.padding(top = LigayaSpacing.xs),
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.lg))

        // Both providers are shown because the design calls for them, but neither can work until
        // the project owner registers OAuth clients (ACCOUNT_ACTIONS_NEEDED.md item 6). Rather
        // than a dead button that appears broken, tapping says so plainly and points at the path
        // that does work — the alternative, hiding them, would make this screen stop matching the
        // design the moment credentials landed.
        LigayaSecondaryButton(
            text = "Continue with Google",
            onClick = ::signInWithGoogle,
        )
        Spacer(modifier = Modifier.height(LigayaSpacing.sm))
        LigayaSecondaryButton(
            text = "Continue with Apple",
            onClick = { formError = APPLE_UNAVAILABLE },
        )

        OrDivider(modifier = Modifier.padding(vertical = LigayaSpacing.lg))

        LigayaTextField(
            value = email,
            onValueChange = {
                email = it
                if (emailError != null) emailError = null
                formError = null
            },
            placeholder = "Email address",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            errorMessage = emailError,
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.sm))

        LigayaTextField(
            value = password,
            onValueChange = {
                password = it
                if (passwordError != null) passwordError = null
                formError = null
            },
            placeholder = "Password",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            errorMessage = passwordError,
            trailing = {
                Text(
                    text = if (passwordVisible) "Hide" else "Show",
                    style = LigayaTypography.label,
                    color = LigayaTheme.colors.roseDeep,
                    modifier = Modifier
                        .clip(LigayaShapes.pill)
                        .clickable { passwordVisible = !passwordVisible }
                        .padding(horizontal = LigayaSpacing.sm, vertical = LigayaSpacing.xs)
                        .semantics {
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        },
                )
            },
        )

        // Form-level failures (the provider's, not a single field's) get their own slot so they
        // never push a field's own message around.
        AnimatedVisibility(
            visible = formError != null,
            enter = fadeIn(tween(LigayaMotion.durationFast)),
            exit = fadeOut(tween(LigayaMotion.durationFast)),
        ) {
            Text(
                text = formError.orEmpty(),
                style = LigayaTypography.label,
                color = LigayaTheme.colors.colorStatusFailed,
                modifier = Modifier.padding(top = LigayaSpacing.sm),
            )
        }

        Spacer(modifier = Modifier.height(LigayaSpacing.lg))

        LigayaPrimaryButton(
            text = when {
                submitting -> "Please wait…"
                mode == AccountMode.SIGN_UP -> "Create account"
                else -> "Log in"
            },
            enabled = !submitting,
            onClick = ::submit,
        )

        Text(
            text = if (mode == AccountMode.SIGN_UP) {
                "Already have an account?  Log in"
            } else {
                "New here?  Create an account"
            },
            style = LigayaTypography.label,
            color = LigayaTheme.colors.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LigayaSpacing.md)
                .clip(LigayaShapes.pill)
                .clickable {
                    mode = if (mode == AccountMode.SIGN_UP) AccountMode.LOG_IN else AccountMode.SIGN_UP
                    // Errors describe the previous mode's rules, so they must not survive the switch.
                    emailError = null
                    passwordError = null
                    formError = null
                }
                .padding(vertical = LigayaSpacing.sm),
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.xl))
    }
}

@Composable
private fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(HAIRLINE)
                .background(LigayaTheme.colors.blushDeep),
        )
        Text(
            text = "or",
            style = LigayaTypography.label,
            color = LigayaTheme.colors.inkSoft,
            modifier = Modifier.padding(horizontal = LigayaSpacing.md),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(HAIRLINE)
                .background(LigayaTheme.colors.blushDeep),
        )
    }
}

/** Public so callers outside this module (LigayaNavHost) can pass [CreateAccountScreen.initialMode] —
 *  Welcome's "I already have an account" link opens straight into [LOG_IN]. */
enum class AccountMode { SIGN_UP, LOG_IN }

/**
 * Deliberately permissive: this is a formatting sanity check to save a pointless round trip, not an
 * attempt to decide what a valid address is. Over-strict client-side email regexes reject real
 * addresses, and the provider is the only thing that can actually confirm one.
 */
internal fun validateEmail(email: String): String? {
    val trimmed = email.trim()
    return when {
        trimmed.isEmpty() -> "Enter your email address."
        !trimmed.contains("@") || trimmed.startsWith("@") || trimmed.endsWith("@") ->
            "That doesn't look like an email address."
        trimmed.substringAfterLast("@").let { !it.contains(".") || it.startsWith(".") || it.endsWith(".") } ->
            "That doesn't look like an email address."
        trimmed.contains(" ") -> "Email addresses can't contain spaces."
        else -> null
    }
}

internal fun validatePassword(password: String, enforceStrength: Boolean): String? = when {
    password.isEmpty() -> "Enter a password."
    enforceStrength && password.length < MINIMUM_PASSWORD_LENGTH ->
        "Use at least $MINIMUM_PASSWORD_LENGTH characters."
    else -> null
}

internal const val MINIMUM_PASSWORD_LENGTH = 8

private const val GOOGLE_UNAVAILABLE =
    "Google sign-in isn't set up yet. Use your email and password for now."
private const val APPLE_UNAVAILABLE =
    "Apple sign-in isn't set up yet. Use your email and password for now."

private val LOCKUP_LOGO_SIZE = 44.dp
private val LOCKUP_WORDMARK_SIZE = 14.sp
private val LOCKUP_TRACKING = 6.sp
private val HAIRLINE = 1.dp
