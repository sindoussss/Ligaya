package com.ligaya.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.ligayaButtonElevation
import com.ligaya.designsystem.ligayaElevation
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaThemeMode
import com.ligaya.designsystem.LigayaTypography

/** Every settings destination shares this frame: a back arrow, a title, and a scrolling body. */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = LigayaIcons.chevronBack,
                    contentDescription = "Back",
                    tint = LigayaTheme.colors.cocoaInk,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(title, style = LigayaTypography.settingsTitle, color = LigayaTheme.colors.cocoaInk)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            content()
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** A card of related rows, the same shape the Settings list uses. */
@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ligayaElevation(shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(24.dp)),
    ) {
        content()
    }
}

/** A plain statement: a label, and what the app actually knows about it. */
@Composable
fun SettingsFact(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = LigayaTypography.settingsRow, color = LigayaTheme.colors.cocoaInk)
        Text(
            text = value,
            style = LigayaTypography.chatStatus,
            color = LigayaTheme.colors.taupe,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(LigayaTheme.colors.shellEdge),
    )
}

/** A tappable choice with a tick when it is the one in force. */
@Composable
fun SettingsChoice(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "$label. $detail"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = LigayaTypography.settingsRow, color = LigayaTheme.colors.cocoaInk)
            Text(
                text = detail,
                style = LigayaTypography.chatStatus,
                color = LigayaTheme.colors.taupe,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(LigayaTheme.colors.berry),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = LigayaIcons.confirmed,
                    contentDescription = null,
                    tint = LigayaTheme.colors.onBerry,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Settings > Appearance. The choice applies the moment it is made and survives the app being closed. */
@Composable
fun AppearanceSettingsScreen(
    mode: LigayaThemeMode,
    onSelectMode: (LigayaThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = "Appearance", onBack = onBack, modifier = modifier) {
        SettingsCard {
            SettingsChoice(
                label = "Light",
                detail = "Always the light design.",
                selected = mode == LigayaThemeMode.Light,
            ) { onSelectMode(LigayaThemeMode.Light) }
            SettingsDivider()
            SettingsChoice(
                label = "Dark",
                detail = "Always the dark design.",
                selected = mode == LigayaThemeMode.Dark,
            ) { onSelectMode(LigayaThemeMode.Dark) }
            SettingsDivider()
            SettingsChoice(
                label = "System",
                detail = "Follows your phone, so it changes with it.",
                selected = mode == LigayaThemeMode.System,
            ) { onSelectMode(LigayaThemeMode.System) }
        }
    }
}

/**
 * Settings > Privacy & Security. Facts about this device, not promises: what is stored here, what leaves the
 * phone, and what does not.
 */
@Composable
fun PrivacySettingsScreen(
    signedInEmail: String?,
    smartRepliesConfigured: Boolean,
    onSignOut: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = "Privacy & Security", onBack = onBack, modifier = modifier) {
        SettingsCard {
            SettingsFact(
                label = "Kept on this phone",
                value = "Your emergency profile, contacts, location history and emergency records live in the " +
                    "app database on this device.",
            )
            SettingsDivider()
            SettingsFact(
                label = "Backups are off",
                value = "Android backup is disabled for this app, so none of that is copied to the cloud or " +
                    "restored onto another phone.",
            )
            SettingsDivider()
            SettingsFact(
                label = "What leaves the phone",
                value = if (smartRepliesConfigured) {
                    "What you say or type to Ligaya is sent to Google Gemini to be answered. Nothing else is " +
                        "sent anywhere."
                } else {
                    "Nothing. Smart replies are switched off, so nothing is sent to Gemini."
                },
            )
            SettingsDivider()
            SettingsFact(
                label = "Passwords",
                value = "Never stored. Sign-in keeps only a scrambled form that cannot be turned back into your " +
                    "password.",
            )
        }

        Spacer(Modifier.height(18.dp))

        SettingsCard {
            SettingsActionRow(
                label = "App permissions",
                detail = "Microphone, location and notifications, in Android settings.",
                onClick = onOpenSystemSettings,
            )
            if (signedInEmail != null) {
                SettingsDivider()
                SettingsActionRow(
                    label = "Sign out",
                    detail = "Signs $signedInEmail out on this phone. Your saved profile stays on the device.",
                    onClick = onSignOut,
                )
            }
        }
    }
}

/** Settings > About Ligaya. */
@Composable
fun AboutSettingsScreen(
    versionName: String,
    smartRepliesConfigured: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = "About Ligaya", onBack = onBack, modifier = modifier) {
        SettingsCard {
            SettingsFact(label = "Version", value = versionName)
            SettingsDivider()
            SettingsFact(
                label = "Smart replies",
                value = if (smartRepliesConfigured) {
                    "On. Ligaya can answer freely, and says so when an answer is limited."
                } else {
                    "Off. Ligaya still listens and answers simply, and tells you when a reply is basic."
                },
            )
            SettingsDivider()
            SettingsFact(
                label = "Emergencies never depend on AI",
                value = "SOS, the emergency screen and calling 911 work whether or not the assistant is " +
                    "reachable, and whether or not you have internet.",
            )
            SettingsDivider()
            SettingsFact(
                label = "What Ligaya will not claim",
                value = "She never says help was contacted, family was notified or location was shared unless " +
                    "the phone confirmed it happened.",
            )
            SettingsDivider()
            SettingsFact(
                label = "Emergency number",
                value = "The Philippines national emergency hotline, 911.",
            )
        }
    }
}

@Composable
fun SettingsActionRow(label: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label. $detail" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = LigayaTypography.settingsRow, color = LigayaTheme.colors.cocoaInk)
            Text(
                text = detail,
                style = LigayaTypography.chatStatus,
                color = LigayaTheme.colors.taupe,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = LigayaIcons.chevronForward,
            contentDescription = null,
            tint = LigayaTheme.colors.taupe,
            modifier = Modifier.size(14.dp),
        )
    }
}
