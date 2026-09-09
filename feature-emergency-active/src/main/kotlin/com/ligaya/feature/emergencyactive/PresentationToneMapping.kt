package com.ligaya.feature.emergencyactive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ligaya.core.uistate.PresentationTone
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.components.StatusTone

/**
 * Step 35's own doc comment on [PresentationTone] anticipated this exact resolution: "resolving
 * a PresentationTone against the real token set is whichever later UI step actually renders a
 * screen, not this module's job." This is that later step.
 */
@Composable
fun PresentationTone.containerColor(): Color = when (this) {
    PresentationTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    PresentationTone.PENDING -> LigayaColors.colorStatusPending
    PresentationTone.IN_PROGRESS -> LigayaColors.idlePrimary
    PresentationTone.SUCCESS -> LigayaColors.colorStatusConfirmed
    PresentationTone.FAILURE -> LigayaColors.colorStatusFailed
    PresentationTone.EMERGENCY -> LigayaColors.colorEmergencyActive
}

@Composable
fun PresentationTone.contentColor(): Color = when (this) {
    PresentationTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    PresentationTone.PENDING -> LigayaColors.onStatusPending
    PresentationTone.IN_PROGRESS -> LigayaColors.onIdlePrimary
    PresentationTone.SUCCESS -> LigayaColors.onStatusConfirmed
    PresentationTone.FAILURE -> LigayaColors.onStatusFailed
    PresentationTone.EMERGENCY -> LigayaColors.onEmergencyActive
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
