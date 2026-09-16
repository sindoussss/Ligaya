package com.ligaya.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import com.ligaya.core.data.profile.EmergencyProfile
import com.ligaya.core.data.profile.EmergencyProfileRepository
import com.ligaya.core.data.profile.MedicalInfo
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaBackButton
import com.ligaya.designsystem.components.LigayaLabeledField
import com.ligaya.designsystem.components.LigayaPrimaryButton
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import kotlinx.coroutines.launch

/**
 * Screen 5: §3's emergency profile — "name, optional medical info, contacts".
 *
 * Every field here is optional, and "Next" is never disabled. That is §3's explicit instruction
 * ("do not require sensitive information... and never block onboarding on it") rather than a
 * shortcut: a half-filled profile is strictly better than a user who abandoned onboarding at a
 * required medical-history field, and `EmergencyProfile()` with everything empty is already a
 * valid stored state by design.
 *
 * Loads any existing profile first, so re-entering the step shows what was already saved instead
 * of silently blanking it — the same call that makes this screen reusable for editing later.
 */
@Composable
fun EmergencyProfileScreen(
    profileRepository: EmergencyProfileRepository,
    userId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var medicalNotes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        profileRepository.getProfile(userId)?.let { existing ->
            name = existing.name.orEmpty()
            phoneNumber = existing.phoneNumber.orEmpty()
            medicalNotes = existing.medicalInfo?.notes.orEmpty()
        }
    }

    val reduceMotion = rememberIsReduceMotionEnabled()
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            entrance.snapTo(1f)
        } else {
            entrance.animateTo(1f, tween(LigayaMotion.durationEntrance, easing = LigayaMotion.easingEntrance))
        }
    }
    val t = entrance.value

    fun save() {
        saving = true
        scope.launch {
            // Blank stays null rather than becoming an empty string: "" and "not provided" are the
            // same thing to a user and must not be two different states downstream.
            profileRepository.saveProfile(
                userId = userId,
                profile = EmergencyProfile(
                    name = name.trim().ifBlank { null },
                    phoneNumber = phoneNumber.trim().ifBlank { null },
                    medicalInfo = medicalNotes.trim().ifBlank { null }?.let { MedicalInfo(notes = it) },
                ),
            )
            saving = false
            onSaved()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.canvas)
            .safeDrawingPadding()
            // Without this the keyboard covers the field being typed into and the Next button
            // below it — on a form this tall, that is most of the screen.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LigayaSpacing.lg),
    ) {
        LigayaBackButton(onClick = onBack)

        Spacer(modifier = Modifier.height(LigayaSpacing.md))

        Text(
            text = "Emergency Profile",
            style = LigayaTypography.headline,
            color = LigayaTheme.colors.ink,
            modifier = Modifier.alpha(slice(t, 0f, 0.5f)),
        )
        Text(
            text = "Help us get you the right kind of help.",
            style = LigayaTypography.body,
            color = LigayaTheme.colors.inkSoft,
            modifier = Modifier
                .padding(top = LigayaSpacing.xs)
                .alpha(slice(t, 0.1f, 0.6f)),
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.lg))

        LigayaLabeledField(
            label = "Full Name",
            value = name,
            onValueChange = { name = it },
            placeholder = "Your name",
            modifier = Modifier.alpha(slice(t, 0.2f, 0.7f)),
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.md))

        LigayaLabeledField(
            label = "Phone Number",
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            placeholder = "+63 912 345 6789",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.alpha(slice(t, 0.3f, 0.8f)),
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.md))

        LigayaLabeledField(
            label = "Medical Info (optional)",
            value = medicalNotes,
            onValueChange = { medicalNotes = it },
            placeholder = "e.g. allergies, conditions",
            singleLine = false,
            modifier = Modifier.alpha(slice(t, 0.4f, 0.9f)),
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.xl))

        LigayaPrimaryButton(
            text = if (saving) "Saving…" else "Next",
            enabled = !saving,
            onClick = ::save,
            modifier = Modifier.alpha(slice(t, 0.5f, 1f)),
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.xl))
    }
}

/** Each element eases its own slice of one shared entrance, as on every other screen here. */
private fun slice(master: Float, start: Float, end: Float): Float =
    LigayaMotion.easingEntrance.transform(((master - start) / (end - start)).coerceIn(0f, 1f))
