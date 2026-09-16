package com.ligaya.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaTypography

/** The app's four main sections, in tab-bar order. */
enum class LigayaTab(val label: String) {
    Home("Home"),
    Chat("Chat"),
    Tools("Tools"),
    Profile("Profile"),
}

/**
 * The Home / Chat / Tools / Profile tab bar shared by the main screens. Tapping the tab you're already on does
 * nothing; every tab is a full 56dp-tall touch target.
 */
@Composable
fun LigayaTabBar(
    selected: LigayaTab,
    onSelect: (LigayaTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LigayaColors.shell)
            .padding(vertical = 6.dp),
    ) {
        LigayaTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val tint = if (isSelected) LigayaColors.cocoa else LigayaColors.taupe
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
    }
}

private fun LigayaTab.icon(selected: Boolean): ImageVector = when (this) {
    LigayaTab.Home -> if (selected) LigayaIcons.homeSelected else LigayaIcons.home
    LigayaTab.Chat -> if (selected) LigayaIcons.chatSelected else LigayaIcons.chat
    LigayaTab.Tools -> if (selected) LigayaIcons.toolsSelected else LigayaIcons.tools
    LigayaTab.Profile -> if (selected) LigayaIcons.profileSelected else LigayaIcons.profile
}
