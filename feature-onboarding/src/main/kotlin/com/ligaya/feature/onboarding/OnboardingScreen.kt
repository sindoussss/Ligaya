package com.ligaya.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.ligaya.core.backend.auth.AuthRepository
import com.ligaya.core.backend.auth.AuthResult
import com.ligaya.core.data.profile.EmergencyContact
import com.ligaya.core.data.profile.EmergencyProfile
import com.ligaya.core.data.profile.EmergencyProfileRepository
import com.ligaya.core.data.profile.MedicalInfo
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import kotlinx.coroutines.launch

private enum class OnboardingStep { ACCOUNT, PROFILE }

/**
 * Step 42's Onboarding screen: §3's "Account: create/login/auth/session" (Step 4's
 * [AuthRepository], already built) followed by §3's emergency profile (Step 5's
 * [EmergencyProfileRepository]/[EmergencyProfile], already built with its own "skip for now"
 * state — `EmergencyProfile()` with every field empty). This screen only presents that already-
 * built logic; it decides nothing about auth or persistence itself.
 *
 * The account step is not skippable — §3 lists it as its own required onboarding bullet, distinct
 * from "never block onboarding on [the profile]" which applies only to the profile step below it.
 * Once signed in, every profile field is genuinely optional: "Skip for now" and "Save & Continue"
 * both reach [onOnboardingComplete] through the exact same [EmergencyProfileRepository.saveProfile]
 * call — Skip simply saves whatever's currently filled (empty, if nothing was) rather than being a
 * separate bypass path, so a user who fills in a name and then taps Skip doesn't lose it.
 *
 * "Progress clearly indicated" (the UI/UX brief's own Onboarding row) is the "Step X of 2" line at
 * the top of every step.
 */
@Composable
fun OnboardingScreen(
    authRepository: AuthRepository,
    profileRepository: EmergencyProfileRepository,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(OnboardingStep.ACCOUNT) }
    var userId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LigayaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LigayaSpacing.md),
    ) {
        val stepNumber = if (step == OnboardingStep.ACCOUNT) 1 else 2
        Text(
            text = "Step $stepNumber of 2",
            style = LigayaTypography.label,
            color = LigayaTheme.colors.idlePrimary,
            modifier = Modifier.semantics { contentDescription = "Onboarding step $stepNumber of 2" },
        )

        when (step) {
            OnboardingStep.ACCOUNT -> AccountStep(
                authRepository = authRepository,
                onAccountReady = { readyUserId ->
                    userId = readyUserId
                    step = OnboardingStep.PROFILE
                },
            )
            OnboardingStep.PROFILE -> ProfileStep(
                onSave = { profile ->
                    val currentUserId = userId ?: return@ProfileStep
                    scope.launch {
                        profileRepository.saveProfile(currentUserId, profile)
                        onOnboardingComplete()
                    }
                },
            )
        }
    }
}

@Composable
private fun AccountStep(
    authRepository: AuthRepository,
    onAccountReady: (userId: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Text(text = "Create your account", style = LigayaTypography.headline, color = LigayaTheme.colors.onSurface)

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("onboardingEmailField"),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("onboardingPasswordField"),
    )

    errorMessage?.let {
        Text(text = it, style = LigayaTypography.label, color = LigayaTheme.colors.colorStatusFailed)
    }

    Button(
        enabled = email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag("onboardingAccountSubmit"),
        onClick = {
            errorMessage = null
            scope.launch {
                val result = if (isLoggingIn) authRepository.logIn(email, password) else authRepository.signUp(email, password)
                when (result) {
                    is AuthResult.Success -> onAccountReady(result.userId)
                    is AuthResult.Failure -> errorMessage = result.message
                }
            }
        },
    ) {
        Text(if (isLoggingIn) "Log In" else "Create Account")
    }

    TextButton(onClick = { isLoggingIn = !isLoggingIn; errorMessage = null }) {
        Text(if (isLoggingIn) "New here? Create an account" else "Already have an account? Log in")
    }
}

@Composable
private fun ProfileStep(onSave: (EmergencyProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var bloodType by remember { mutableStateOf("") }
    val contacts = remember { mutableStateListOf<Pair<String, String>>() }

    fun currentProfile(): EmergencyProfile = EmergencyProfile(
        name = name.ifBlank { null },
        medicalInfo = bloodType.takeIf { it.isNotBlank() }?.let { MedicalInfo(bloodType = it) },
        contacts = contacts
            .filter { (contactName, _) -> contactName.isNotBlank() }
            .map { (contactName, phone) -> EmergencyContact(name = contactName, phoneNumber = phone.ifBlank { null }) },
    )

    Text(text = "Emergency profile", style = LigayaTypography.headline, color = LigayaTheme.colors.onSurface)
    Text(
        text = "Every field here is optional — you can skip this or fill in only what you're comfortable sharing.",
        style = LigayaTypography.body,
        color = LigayaTheme.colors.onSurface,
    )

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("onboardingNameField"),
    )
    OutlinedTextField(
        value = bloodType,
        onValueChange = { bloodType = it },
        label = { Text("Blood type (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("onboardingBloodTypeField"),
    )

    Text(text = "Emergency contacts (optional)", style = LigayaTypography.label, color = LigayaTheme.colors.onSurface)
    contacts.forEachIndexed { index, (contactName, phone) ->
        Row(horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm)) {
            OutlinedTextField(
                value = contactName,
                onValueChange = { contacts[index] = it to phone },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { contacts[index] = contactName to it },
                label = { Text("Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
    OutlinedButton(
        onClick = { contacts.add("" to "") },
        modifier = Modifier.testTag("onboardingAddContact"),
    ) {
        Text("+ Add contact")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm),
    ) {
        TextButton(
            modifier = Modifier.testTag("onboardingSkip"),
            onClick = { onSave(EmergencyProfile()) },
        ) {
            Text("Skip for now")
        }
        Button(
            modifier = Modifier.weight(1f).testTag("onboardingSaveAndContinue"),
            colors = ButtonDefaults.buttonColors(
                containerColor = LigayaTheme.colors.colorStatusConfirmed,
                contentColor = LigayaTheme.colors.onStatusConfirmed,
            ),
            onClick = { onSave(currentProfile()) },
        ) {
            Text("Save & Continue")
        }
    }
}
