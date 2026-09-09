package com.ligaya.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography

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
 * [StatusTone.Neutral] uses [MaterialTheme]'s own surface roles (so it adapts to light/dark
 * automatically); the other three use [LigayaColors]' fixed semantic tokens, same reasoning as
 * [SosControl] — a "failed" card should look the same regardless of system theme.
 */
enum class StatusTone { Neutral, Pending, Success, Failure }

private data class ToneStyle(val container: Color, val onContainer: Color, val icon: ImageVector?)

@Composable
private fun toneStyle(tone: StatusTone): ToneStyle = when (tone) {
    StatusTone.Neutral -> ToneStyle(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, null)
    StatusTone.Pending -> ToneStyle(LigayaColors.colorStatusPending, LigayaColors.onStatusPending, Icons.Filled.Schedule)
    StatusTone.Success -> ToneStyle(LigayaColors.colorStatusConfirmed, LigayaColors.onStatusConfirmed, Icons.Filled.CheckCircle)
    StatusTone.Failure -> ToneStyle(LigayaColors.colorStatusFailed, LigayaColors.onStatusFailed, Icons.Filled.ErrorOutline)
}

@Composable
fun StatusCard(
    title: String,
    message: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val style = toneStyle(tone)
    Card(
        modifier = modifier.semantics { contentDescription = "$title: $message" },
        colors = CardDefaults.cardColors(containerColor = style.container, contentColor = style.onContainer),
    ) {
        Row(
            modifier = Modifier.padding(LigayaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            style.icon?.let {
                Icon(imageVector = it, contentDescription = null, tint = style.onContainer)
                Spacer(Modifier.width(LigayaSpacing.sm))
            }
            Column {
                Text(text = title, style = LigayaTypography.headline, color = style.onContainer)
                Text(text = message, style = LigayaTypography.body, color = style.onContainer)
            }
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
            .background(style.container)
            .padding(horizontal = LigayaSpacing.md, vertical = LigayaSpacing.xs)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        style.icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = style.onContainer,
                modifier = Modifier.padding(end = LigayaSpacing.xs),
            )
        }
        Text(text = text, style = LigayaTypography.label, color = style.onContainer)
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
