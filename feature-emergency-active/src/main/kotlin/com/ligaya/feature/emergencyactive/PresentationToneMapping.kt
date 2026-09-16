package com.ligaya.feature.emergencyactive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ligaya.core.uistate.PresentationTone
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.components.StatusTone

/**
 * Step 35's own doc comment on [PresentationTone] anticipated this exact resolution: "resolving
 * a PresentationTone against the real token set is whichever later UI step actually renders a
 * screen, not this module's job." This is that later step.
 */
@Composable
fun PresentationTone.containerColor(): Color = when (this) {
    PresentationTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    PresentationTone.PENDING -> LigayaTheme.colors.colorStatusPending
    PresentationTone.IN_PROGRESS -> LigayaTheme.colors.idlePrimary
    PresentationTone.SUCCESS -> LigayaTheme.colors.colorStatusConfirmed
    PresentationTone.FAILURE -> LigayaTheme.colors.colorStatusFailed
    PresentationTone.EMERGENCY -> LigayaTheme.colors.colorEmergencyActive
}

@Composable
fun PresentationTone.contentColor(): Color = when (this) {
    PresentationTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    PresentationTone.PENDING -> LigayaTheme.colors.onStatusPending
    PresentationTone.IN_PROGRESS -> LigayaTheme.colors.onIdlePrimary
    PresentationTone.SUCCESS -> LigayaTheme.colors.onStatusConfirmed
    PresentationTone.FAILURE -> LigayaTheme.colors.onStatusFailed
    PresentationTone.EMERGENCY -> LigayaTheme.colors.onEmergencyActive
}

/** design-system's StatusCard/StatusChip (Step 34) only have four tones — IN_PROGRESS folds into
 *  Pending (still "not resolved yet, actively working") and EMERGENCY folds into Failure (the
 *  most visually urgent of the four available) rather than growing StatusTone itself, since
 *  those two are the closest existing meaning, not a new distinct one worth a fifth/sixth case. */
fun PresentationTone.toStatusTone(): StatusTone = when (this) {
    PresentationTone.NEUTRAL -> StatusTone.Neutral
    PresentationTone.PENDING, PresentationTone.IN_PROGRESS -> StatusTone.Pending
    PresentationTone.SUCCESS -> StatusTone.Success
    PresentationTone.FAILURE, PresentationTone.EMERGENCY -> StatusTone.Failure
}
