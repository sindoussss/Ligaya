package com.ligaya.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.ligayaElevation

/**
 * Section 27's screen inventory names several of these by role: "911 status card
 * (idle/dialing/failed/retry), location status card (acquiring/GPS/last-known/unavailable)."
 * [StatusTone] is the shared shape every one of those maps onto, so this one component (not a
 * bespoke card per subsystem) renders all of them.
 *
 * "Never rely on hue alone to distinguish states, pair with icon/shape/text" (section 27): every
 * non-Neutral tone shows an icon alongside its color, and [contentDescription] states the tone in
 * words for TalkBack — color is never the only signal.
 *
 * Repainted from a solid tone-coloured block (this card's own shape until the app's visual design
 * pass reached it — see git history: it predates every other screen's cream/shell language) to
 * the same soft card every other status row in the app uses: a [LigayaTheme.colors.shell] surface
 * that never changes colour, with the tone carried by a small accent disc instead — the pattern
 * already established by Home's contact rows and screen 6's own outcome cards. The card's own
 * text stays [LigayaTheme.colors.cocoaInk]/[LigayaTheme.colors.taupe] regardless of tone (already
 * proven AA by DarkPaletteContrastTest), so only the disc's icon carries the saturated colour.
 */
enum class StatusTone { Neutral, Pending, Success, Failure }

private data class ToneStyle(val discBackground: Color, val iconTint: Color, val icon: ImageVector?)

@Composable
private fun toneStyle(tone: StatusTone): ToneStyle = when (tone) {
    StatusTone.Neutral -> ToneStyle(LigayaTheme.colors.shellEdge, LigayaTheme.colors.taupe, null)
    StatusTone.Pending -> ToneStyle(LigayaTheme.colors.colorStatusPending.copy(alpha = 0.18f), LigayaTheme.colors.colorStatusPending, Icons.Filled.Schedule)
    StatusTone.Success -> ToneStyle(LigayaTheme.colors.colorStatusConfirmed.copy(alpha = 0.18f), LigayaTheme.colors.colorStatusConfirmed, Icons.Filled.CheckCircle)
    StatusTone.Failure -> ToneStyle(LigayaTheme.colors.colorStatusFailed.copy(alpha = 0.18f), LigayaTheme.colors.colorStatusFailed, Icons.Filled.ErrorOutline)
}

@Composable
fun StatusCard(
    title: String,
    message: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val style = toneStyle(tone)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .ligayaElevation(shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(22.dp))
            .padding(LigayaSpacing.md)
            .semantics { contentDescription = "$title: $message" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(style.discBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (style.icon != null) {
                Icon(imageVector = style.icon, contentDescription = null, tint = style.iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.padding(start = LigayaSpacing.sm)) {
            Text(text = title, style = LigayaTypography.headline, color = LigayaTheme.colors.cocoaInk)
            Text(text = message, style = LigayaTypography.body, color = LigayaTheme.colors.taupe)
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val style = toneStyle(tone)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(percent = 50))
            .padding(horizontal = LigayaSpacing.md, vertical = LigayaSpacing.xs)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        style.icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = style.iconTint,
                modifier = Modifier.padding(end = LigayaSpacing.xs).size(16.dp),
            )
        }
        Text(text = text, style = LigayaTypography.label, color = LigayaTheme.colors.cocoaInk)
    }
}

@LigayaComponentPreviews
@Composable
private fun StatusCardPreview() {
    MaterialTheme {
        Column {
            StatusCard("911 Call", "Dialing…", StatusTone.Pending)
            StatusCard("Location", "GPS fix acquired", StatusTone.Success)
            StatusCard("911 Call", "Call failed — tap to retry", StatusTone.Failure)
            StatusCard("Companion", "Idle", StatusTone.Neutral)
        }
    }
}

@LigayaComponentPreviews
@Composable
private fun StatusChipPreview() {
    MaterialTheme {
        Row {
            StatusChip("Pending", StatusTone.Pending)
            StatusChip("Confirmed", StatusTone.Success)
            StatusChip("Failed", StatusTone.Failure)
        }
    }
}
