package com.ligaya.feature.safetycircle

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ligaya.core.data.profile.EmergencyContact
import com.ligaya.designsystem.ligayaButtonElevation
import com.ligaya.designsystem.ligayaElevation
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaTab
import com.ligaya.designsystem.components.LigayaTabBar

/**
 * The Safety Circle tab (architecture sections 1, 4, 6 and 8), which replaced a "Tools" tab the architecture
 * never asks for.
 *
 * The distinction this screen exists to make honestly: the people saved on this phone are real and are shown,
 * while the household side of section 6 — inviting members, their alert permissions, family alerts with
 * delivery states — lives in the backend, and a build with no backend project configured cannot do any of it.
 * Section 21 requires the UI to say when a capability is unavailable, and section 23 forbids implying that
 * anyone would be alerted. So this screen says so in plain words instead of showing an empty roster that
 * looks like a working feature nobody has used yet.
 *
 * "Quick actions" leads to [QuickActionsScreen] rather than doing three things on one tap the way its own
 * subtitle here reads at a glance ("call 911, alert your circle, and share your location — all in one tap"):
 * one of those three is genuinely one-tap-real (911), one is real but independent (share location), and one
 * cannot do anything without the same backend project this whole screen is honest about not having (alert
 * your circle) — collapsing them into a single combined action would mean either skipping the one that
 * cannot run or silently doing less than the label promises. Each gets its own honest state instead.
 */
@Composable
fun SafetyCircleHomeScreen(
    signedIn: Boolean,
    contacts: List<EmergencyContact>,
    /** Whether a backend project is configured in this build, which is what household features need. */
    householdBackendConfigured: Boolean,
    onSignIn: () -> Unit,
    onEditContacts: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onOpenLigayaPlus: () -> Unit,
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
        Text(
            text = "Safety Circle",
            style = LigayaTypography.settingsTitle,
            color = LigayaTheme.colors.cocoaInk,
            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 10.dp),
        )

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            CircleCard {
                CircleFact(
                    label = "Your safety matters",
                    value = "Set up your circle for faster help in emergencies.",
                )
            }

            Spacer(Modifier.height(18.dp))

            if (!signedIn) {
                CircleCard {
                    CircleAction(
                        label = "Sign in to set up your Safety Circle",
                        detail = "A circle belongs to an account, so the people in it can be reached from more " +
                            "than this one phone.",
                        onClick = onSignIn,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            CircleCard {
                CircleAction(
                    icon = LigayaIcons.circle,
                    label = "Emergency contacts",
                    detail = if (contacts.isEmpty()) "None added yet" else "${contacts.size} added",
                    onClick = onEditContacts,
                )
                CircleDivider()
                CircleAction(
                    icon = LigayaIcons.quickActions,
                    label = "Quick actions",
                    detail = "Call 911, alert your circle, and share your location.",
                    onClick = onOpenQuickActions,
                )
            }

            Spacer(Modifier.height(18.dp))

            SectionHeading("FAMILY & FRIENDS")
            CircleCard {
                if (contacts.isEmpty()) {
                    CircleAction(
                        label = "No emergency contacts yet",
                        detail = "Add the people Ligaya should try to reach for you.",
                        onClick = onEditContacts,
                    )
                } else {
                    contacts.forEachIndexed { index, contact ->
                        if (index > 0) CircleDivider()
                        ContactRow(contact)
                    }
                }
                CircleDivider()
                CircleAction(
                    label = "Add contact",
                    detail = "In your emergency profile.",
                    onClick = onEditContacts,
                )
            }

            Spacer(Modifier.height(18.dp))

            SectionHeading("FAMILY ALERTS")
            CircleCard {
                CircleFact(
                    label = if (householdBackendConfigured) "Ready" else "Not set up on this build",
                    value = if (householdBackendConfigured) {
                        "When an emergency starts, your circle is sent an alert, and each one shows whether it " +
                            "was sent, delivered, or failed."
                    } else {
                        "Inviting family and alerting them runs through the Ligaya backend, and no backend " +
                            "project is configured in this build. Nobody would be alerted, so the app does not " +
                            "offer to invite anyone yet."
                    },
                )
                CircleDivider()
                CircleFact(
                    label = "What this does not affect",
                    value = "SOS, calling 911 and Ligaya staying with you through an emergency do not go " +
                        "through any of this, and keep working either way.",
                )
            }

            Spacer(Modifier.height(18.dp))

            SectionHeading("LIGAYA+")
            CircleCard {
                CircleAction(
                    label = "Family plan",
                    detail = "The circle, family alerts and shared status are the paid part. Your own SOS, " +
                        "911 and emergency profile are not.",
                    onClick = onOpenLigayaPlus,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        LigayaTabBar(selected = LigayaTab.Circle, onSelect = onSelectTab)
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = LigayaTypography.chatStatus,
        color = LigayaTheme.colors.taupe,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
internal fun CircleCard(content: @Composable () -> Unit) {
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

@Composable
internal fun CircleDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(LigayaTheme.colors.shellEdge),
    )
}

/** The avatar reads a name where the reference draws a photo: this app has no photo to show for a
 *  locally-saved contact, and a placeholder headshot would look like a real one. The initial is
 *  honestly what it is — a letter, not a picture — while still giving each row its own identity. */
@Composable
private fun ContactRow(contact: EmergencyContact) {
    // A missing number is the thing worth saying: a contact with a relationship and no number looked
    // perfectly complete, while Ligaya has no way to reach them at all.
    val reach = contact.phoneNumber ?: "no number saved, so Ligaya cannot reach them"
    val detail = listOfNotNull(contact.relationship, reach).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .semantics { contentDescription = "${contact.name}. $detail" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(LigayaTheme.colors.blush),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = LigayaTypography.settingsRow.copy(fontWeight = FontWeight.SemiBold),
                color = LigayaTheme.colors.accentInk,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(contact.name, style = LigayaTypography.settingsRow, color = LigayaTheme.colors.cocoaInk)
            Text(
                text = detail,
                style = LigayaTypography.chatStatus,
                color = LigayaTheme.colors.taupe,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun CircleFact(label: String, value: String) {
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
internal fun CircleAction(label: String, detail: String, onClick: () -> Unit, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label. $detail" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LigayaTheme.colors.cocoaInk,
                modifier = Modifier.size(22.dp).padding(end = 14.dp),
            )
        }
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
