package com.ligaya.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Section 27's spacing/layout grid: "consistent 4/8pt spacing scale, generous touch targets
 * (minimum 48dp, but the SOS control and any control usable mid-emergency should be significantly
 * larger — likely 96dp+ tap area)."
 */
object LigayaSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp

    /** The 48dp floor for any touch target, app-wide. */
    val minTouchTarget: Dp = 48.dp

    /** SOS and any other control that must stay usable mid-emergency (e.g. "I'm safe"). */
    val emergencyTouchTarget: Dp = 96.dp
}
