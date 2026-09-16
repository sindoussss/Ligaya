package com.ligaya.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.ligayaButtonElevation

/** The app's four main sections, in tab-bar order. */
enum class LigayaTab(val label: String) {
    Home("Home"),
    Chat("Chat"),
    /** Section 6 of the architecture: the household the user is protected by. Labelled "Circle" because
     *  "Safety Circle" does not fit a quarter of a phone's width; the screen itself says the full name. */
    Circle("Circle"),
    Profile("Profile"),
}

/**
 * The Home / Chat / Circle / Profile tab bar shared by the main screens, with SOS raised in the
 * middle of it. Tapping the tab you're already on does nothing; every tab is a full 56dp-tall touch
 * target.
 *
 * [onSos] is the same one-tap path Home's own SOS pill takes — trigger the emergency, then show the
 * Emergency screen — so reaching it from the tab bar is a shortcut to the one SOS flow rather than a
 * second, parallel one. It sits in the bar on every main screen because an emergency control that is
 * only on Home is one tab away exactly when a person has the least attention to spare.
 */
@Composable
fun LigayaTabBar(
    selected: LigayaTab,
    onSelect: (LigayaTab) -> Unit,
    onSos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                // Transparent gap the raised SOS button rises into, above the bar's own surface.
                .padding(top = 20.dp)
                .background(LigayaTheme.colors.shell)
                .padding(vertical = 6.dp),
        ) {
            TabItem(LigayaTab.Home, selected, onSelect)
            TabItem(LigayaTab.Chat, selected, onSelect)
            Spacer(Modifier.weight(1f))
            TabItem(LigayaTab.Circle, selected, onSelect)
            TabItem(LigayaTab.Profile, selected, onSelect)
        }
        SosTabButton(onSos = onSos, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun RowScope.TabItem(tab: LigayaTab, selected: LigayaTab, onSelect: (LigayaTab) -> Unit) {
    val isSelected = tab == selected
    val tint = if (isSelected) LigayaTheme.colors.cocoa else LigayaTheme.colors.taupe
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clickable(role = Role.Tab) { if (!isSelected) onSelect(tab) }
            .semantics { this.selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon(isSelected), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Text(tab.label, style = LigayaTypography.tabLabel, color = tint, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SosTabButton(onSos: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(62.dp)
            .ligayaButtonElevation(elevation = 6.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(LigayaTheme.colors.berry)
            .clickable(role = Role.Button, onClick = onSos)
            // Labelled by what it visibly says. Home also carries an SOS pill in its header, and two
            // controls sharing one accessible name would leave a screen reader unable to tell them apart.
            .semantics(mergeDescendants = true) { contentDescription = "SOS" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SOS",
            style = LigayaTypography.label.copy(fontWeight = FontWeight.Bold),
            color = LigayaTheme.colors.onBerry,
        )
    }
}

private fun LigayaTab.icon(selected: Boolean): ImageVector = when (this) {
    LigayaTab.Home -> if (selected) LigayaIcons.homeSelected else LigayaIcons.home
    LigayaTab.Chat -> if (selected) LigayaIcons.chatSelected else LigayaIcons.chat
    LigayaTab.Circle -> if (selected) LigayaIcons.circleSelected else LigayaIcons.circle
    LigayaTab.Profile -> if (selected) LigayaIcons.profileSelected else LigayaIcons.profile
}
