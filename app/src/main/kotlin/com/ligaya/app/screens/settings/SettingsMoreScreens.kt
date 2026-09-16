package com.ligaya.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.ligayaButtonElevation
import com.ligaya.designsystem.ligayaElevation
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaEmotion
import com.ligaya.designsystem.components.LigayaFrame
import com.ligaya.designsystem.components.LigayaMascot
import com.ligaya.designsystem.components.LigayaMascotPreferences
import com.ligaya.designsystem.components.LigayaMotionMode
import com.ligaya.designsystem.components.rememberLigayaMascotController

/** A row with a switch. The whole row toggles it, so it is a single target rather than a small thumb. */
@Composable
fun SettingsToggleRow(label: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .semantics { contentDescription = "$label. $detail" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = LigayaTypography.settingsRow, color = LigayaTheme.colors.cocoaInk)
            Text(
                text = detail,
                style = LigayaTypography.chatStatus,
                color = LigayaTheme.colors.taupe,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LigayaTheme.colors.onBerry,
                checkedTrackColor = LigayaTheme.colors.berry,
                uncheckedThumbColor = LigayaTheme.colors.shell,
                uncheckedTrackColor = LigayaTheme.colors.shellEdge,
            ),
        )
    }
}

/** A small caption under a card, for something true about the setting above that is not itself a setting. */
@Composable
fun SettingsNote(text: String) {
    Text(
        text = text,
        style = LigayaTypography.chatStatus,
        color = LigayaTheme.colors.taupe,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}

@Composable
private fun GroupHeading(text: String) {
    Text(
        text = text,
        style = LigayaTypography.chatStatus,
        color = LigayaTheme.colors.taupe,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** Settings > General. */
@Composable
fun GeneralSettingsScreen(
    onOpenProfile: () -> Unit,
    onShowWelcomeAgain: () -> Unit,
    welcomeWillShowAgain: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = "General", onBack = onBack, modifier = modifier) {
        SettingsCard {
            SettingsActionRow(
                label = "Emergency profile",
                detail = "Your name, medical notes and the people Ligaya contacts for you.",
                onClick = onOpenProfile,
            )
        }

        Spacer(Modifier.height(18.dp))

        SettingsCard {
            SettingsFact(
                label = "Emergency number",
                value = "911, the Philippines national hotline. It is fixed, so nothing can dial somewhere " +
                    "else by mistake.",
            )
            SettingsDivider()
            SettingsFact(
                label = "Language",
                value = "Ligaya listens in the language your phone is set to. She speaks Filipino when your " +
                    "phone has that voice installed.",
            )
        }

        Spacer(Modifier.height(18.dp))

        SettingsCard {
            SettingsActionRow(
                label = "Show the welcome screen again",
                detail = if (welcomeWillShowAgain) {
                    "It is set to show the next time you open Ligaya."
                } else {
                    "You will see it again the next time you open Ligaya."
                },
                onClick = onShowWelcomeAgain,
            )
        }
    }
}

/**
 * Settings > Voice & Speech. The switch is the only setting here; everything else is a statement about what
 * this phone can actually do, including the cases where the switch is on but nothing is listening.
 */
@Composable
fun VoiceSettingsScreen(
    wakePhraseEnabled: Boolean,
    onWakePhraseChange: (Boolean) -> Unit,
    micPermitted: Boolean,
    smartRepliesConfigured: Boolean,
    onOpenSystemSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = "Voice & Speech", onBack = onBack, modifier = modifier) {
        SettingsCard {
            SettingsToggleRow(
                label = "Listen for the wake phrase",
                detail = "While Ligaya is open, she listens so you can speak without touching the phone.",
                checked = wakePhraseEnabled,
                onCheckedChange = onWakePhraseChange,
            )
        }
        if (wakePhraseEnabled && !micPermitted) {
            SettingsNote(
                "She is not listening right now, because Ligaya does not have microphone access on this phone.",
            )
        }
        if (wakePhraseEnabled && !smartRepliesConfigured) {
            SettingsNote(
                "She hears you, but smart replies are switched off, so she cannot work out what a spoken " +
                    "phrase means. She will tell you out loud instead of going quiet.",
            )
        }

        Spacer(Modifier.height(18.dp))

        SettingsCard {
            SettingsFact(
                label = "Her voice",
                value = "Filipino, when your phone has that voice installed. Otherwise your phone speaks for " +
                    "her, and if it has no voice at all the app shows her reply as text and says there was no " +
                    "voice for it.",
            )
            SettingsDivider()
            SettingsFact(
                label = "SOS does not need any of this",
                value = "Pressing SOS, the emergency screen and calling 911 work with the microphone off and " +
                    "with no internet.",
            )
        }

        Spacer(Modifier.height(18.dp))

        SettingsCard {
            SettingsActionRow(
                label = "Microphone permission",
                detail = if (micPermitted) "Granted. Change it in Android settings." else "Not granted.",
                onClick = onOpenSystemSettings,
            )
        }
    }
}

/**
 * Visual design, screen 10: Character & Animation. Each choice is applied to the preview the moment it is
 * made, and to every other screen she appears on.
 */
@Composable
fun CharacterSettingsScreen(
    preferences: LigayaMascotPreferences,
    reduceMotionOnPhone: Boolean,
    onAnimationSpeedChange: (Float) -> Unit,
    onDepthStrengthChange: (Float) -> Unit,
    onMotionChange: (LigayaMotionMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = "Character & Animation", onBack = onBack, modifier = modifier) {
        val controller = rememberLigayaMascotController()
        LaunchedEffect(Unit) { controller.setEmotion(LigayaEmotion.Content) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .ligayaElevation(shape = RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(LigayaTheme.colors.blush),
        ) {
            // Portrait: head and shoulders, the framing the rest of the app shows her in. Head crops to her
            // eyes at this size, which is not a preview of anything.
            LigayaMascot(controller = controller, frame = LigayaFrame.Portrait)
        }
        SettingsNote("She moves here exactly as she will everywhere else.")

        Spacer(Modifier.height(20.dp))

        GroupHeading("MOVEMENT")
        SettingsCard {
            SettingsChoice(
                label = "Lively",
                detail = "She breathes and blinks a little faster.",
                selected = preferences.animationSpeed > 1.15f,
            ) { onAnimationSpeedChange(1.4f) }
            SettingsDivider()
            SettingsChoice(
                label = "Normal",
                detail = "The pace she was drawn for.",
                selected = preferences.animationSpeed in 0.85f..1.15f,
            ) { onAnimationSpeedChange(1.0f) }
            SettingsDivider()
            SettingsChoice(
                label = "Slow",
                detail = "Everything she does takes a little longer.",
                selected = preferences.animationSpeed < 0.85f,
            ) { onAnimationSpeedChange(0.7f) }
        }

        Spacer(Modifier.height(20.dp))

        GroupHeading("DEPTH")
        SettingsCard {
            SettingsChoice(
                label = "Full",
                detail = "Her layers shift the most as she moves.",
                selected = preferences.depthStrength > 0.8f,
            ) { onDepthStrengthChange(1.0f) }
            SettingsDivider()
            SettingsChoice(
                label = "Gentle",
                detail = "A small amount of parallax between her layers.",
                selected = preferences.depthStrength in 0.2f..0.8f,
            ) { onDepthStrengthChange(0.6f) }
            SettingsDivider()
            SettingsChoice(
                label = "Flat",
                detail = "No parallax at all.",
                selected = preferences.depthStrength < 0.2f,
            ) { onDepthStrengthChange(0f) }
        }

        Spacer(Modifier.height(20.dp))

        GroupHeading("MOTION")
        SettingsCard {
            SettingsChoice(
                label = "Follow the screen",
                detail = "She is lively on Home and calmer during an emergency.",
                selected = preferences.motion == LigayaMotionMode.System,
            ) { onMotionChange(LigayaMotionMode.System) }
            SettingsDivider()
            SettingsChoice(
                label = "Calm everywhere",
                detail = "Small movements only, on every screen.",
                selected = preferences.motion == LigayaMotionMode.Calm,
            ) { onMotionChange(LigayaMotionMode.Calm) }
            SettingsDivider()
            SettingsChoice(
                label = "Hold still",
                detail = "She stays still except when she speaks.",
                selected = preferences.motion == LigayaMotionMode.Still,
            ) { onMotionChange(LigayaMotionMode.Still) }
        }
        if (reduceMotionOnPhone) {
            SettingsNote(
                "Your phone asks apps to reduce motion, so Ligaya is held still whichever of these you pick.",
            )
        }
    }
}
