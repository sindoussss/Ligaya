package com.ligaya.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaDeliveryState
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography

/**
 * Section 27: "delivery-state badge (PENDING/SENT/CONFIRMED/FAILED)" — the Safety Circle
 * per-member, per-channel delivery states (core-notifications' NotificationEventState, Steps
 * 21/28), never collapsed into one "notified" badge per §18's family screen requirement.
 *
 * SENT has no color named in [LigayaColors] (Step 33 named only the emergency/pending/confirmed/
 * failed tokens) — it reuses [LigayaColors.idlePrimary] rather than introducing an unreviewed new
 * hex value, since "sent, awaiting confirmation" is exactly the calm, non-alarming, in-progress
 * meaning that token already carries.
 */
private data class DeliveryStateStyle(val container: Color, val onContainer: Color)

private fun styleFor(state: LigayaDeliveryState): DeliveryStateStyle = when (state) {
    LigayaDeliveryState.PENDING -> DeliveryStateStyle(LigayaColors.colorStatusPending, LigayaColors.onStatusPending)
    LigayaDeliveryState.SENT -> DeliveryStateStyle(LigayaColors.idlePrimary, LigayaColors.onIdlePrimary)
    LigayaDeliveryState.CONFIRMED -> DeliveryStateStyle(LigayaColors.colorStatusConfirmed, LigayaColors.onStatusConfirmed)
    LigayaDeliveryState.FAILED -> DeliveryStateStyle(LigayaColors.colorStatusFailed, LigayaColors.onStatusFailed)
}

private fun labelFor(state: LigayaDeliveryState): String = when (state) {
    LigayaDeliveryState.PENDING -> "Pending"
    LigayaDeliveryState.SENT -> "Sent"
    LigayaDeliveryState.CONFIRMED -> "Confirmed"
    LigayaDeliveryState.FAILED -> "Failed"
}

@Composable
fun DeliveryStateBadge(
    state: LigayaDeliveryState,
    modifier: Modifier = Modifier,
) {
    val style = styleFor(state)
    val label = labelFor(state)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(style.container)
            .padding(horizontal = LigayaSpacing.md, vertical = LigayaSpacing.xs)
            .semantics { contentDescription = "Delivery status: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = requireNotNull(LigayaIcons.deliveryState[state]) { "no icon mapped for $state" },
            contentDescription = null,
            tint = style.onContainer,
            modifier = Modifier.padding(end = LigayaSpacing.xs),
        )
        Text(text = label, style = LigayaTypography.label, color = style.onContainer)
    }
}

@LigayaComponentPreviews
@Composable
private fun DeliveryStateBadgePreview() {
    MaterialTheme {
        Row {
            LigayaDeliveryState.entries.forEach { DeliveryStateBadge(state = it) }
        }
    }
}
