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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaAvatar
import com.ligaya.designsystem.components.LigayaTab
import com.ligaya.designsystem.components.LigayaTabBar

/** Which settings destination a row opens. Every row leads to a real screen — none of them is a placeholder. */
enum class SettingsSection { General, Appearance, Voice, Character, Privacy, About }

/**
 * Visual design, screen 9: Settings.
 *
 * The profile card shows the signed-in account. With nobody signed in it says so and offers the way in, rather
 * than showing an empty card or a name the app does not have — the same rule the rest of the app follows about
 * stating only what is true.
 */
@Composable
fun SettingsScreen(
    userName: String?,
    userEmail: String?,
    appearanceValue: String,
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenProfile: () -> Unit,
    onSelectTab: (LigayaTab) -> Unit = {},
    modifier: Modifier = Modifier,
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
            Text("Settings", style = LigayaTypography.settingsTitle, color = LigayaTheme.colors.cocoaInk)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            ProfileCard(userName = userName, userEmail = userEmail, onClick = onOpenProfile)

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(LigayaTheme.colors.shell)
                    .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(24.dp)),
            ) {
                SettingsRow(LigayaIcons.settingsGeneral, "General", null) { onOpenSection(SettingsSection.General) }
                RowDivider()
                SettingsRow(LigayaIcons.settingsAppearance, "Appearance", appearanceValue) {
                    onOpenSection(SettingsSection.Appearance)
                }
                RowDivider()
                SettingsRow(LigayaIcons.settingsVoice, "Voice & Speech", null) { onOpenSection(SettingsSection.Voice) }
                RowDivider()
                SettingsRow(LigayaIcons.settingsCharacter, "Character & Animation", null) {
                    onOpenSection(SettingsSection.Character)
                }
                RowDivider()
                SettingsRow(LigayaIcons.settingsPrivacy, "Privacy & Security", null) { onOpenSection(SettingsSection.Privacy) }
                RowDivider()
                SettingsRow(LigayaIcons.settingsAbout, "About Ligaya", null) { onOpenSection(SettingsSection.About) }
            }

            Spacer(Modifier.height(24.dp))
        }

        LigayaTabBar(selected = LigayaTab.Profile, onSelect = onSelectTab)
    }
}

@Composable
private fun ProfileCard(userName: String?, userEmail: String?, onClick: () -> Unit) {
    // Signed out: say so and offer the way in, rather than an empty card or an invented name.
    val title = userName ?: if (userEmail != null) "Your account" else "You are not signed in"
    val subtitle = userEmail ?: "Sign in to keep your emergency profile"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $subtitle" }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LigayaAvatar(size = 56.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = LigayaTypography.settingsRow, color = LigayaTheme.colors.cocoaInk)
            Text(
                text = subtitle,
                style = LigayaTypography.chatStatus,
                color = LigayaTheme.colors.taupe,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = LigayaIcons.chevronForward,
            contentDescription = null,
            tint = LigayaTheme.colors.taupe,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (value == null) label else "$label, $value" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = LigayaTheme.colors.cocoaInk, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = LigayaTypography.settingsRow,
            color = LigayaTheme.colors.cocoaInk,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        if (value != null) {
            Text(
                text = value,
                style = LigayaTypography.chatStatus,
                color = LigayaTheme.colors.taupe,
                modifier = Modifier.padding(end = 10.dp),
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

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(1.dp)
            .background(LigayaTheme.colors.shellEdge),
    )
}
