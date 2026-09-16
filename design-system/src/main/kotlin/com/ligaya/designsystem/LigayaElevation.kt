package com.ligaya.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft, warm-toned lift for cards, buttons and other surfaces that sit above the page.
 *
 * Purely a detailing pass: every card in this app was flat fill + a 1dp [LigayaPalette.shellEdge] hairline,
 * which reads correctly but flatter than the reference art (whose cards carry a soft shadow, not just an
 * edge). This adds that lift without touching layout, copy, or any existing colour — the hairline border
 * stays wherever it already was; this is additional, not instead of.
 *
 * Tinted with the palette's own ink rather than plain black, at low alpha, so the shadow reads as this
 * app's warm shade rather than a generic Material drop shadow — and low enough that it holds up in both
 * themes without a separate dark-mode value (a stronger dark canvas already reads as "elevated" more
 * readily than the light cream one, so the same alpha does not need compensating per theme).
 */
@Composable
fun Modifier.ligayaElevation(
    elevation: Dp = 8.dp,
    shape: Shape = RoundedCornerShape(24.dp),
): Modifier {
    val ink = LigayaTheme.colors.cocoaInk
    return shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = ink.copy(alpha = 0.10f),
        spotColor = ink.copy(alpha = 0.18f),
    )
}

/** The lighter lift for a row-height control (a button, a chip) rather than a full card. */
@Composable
fun Modifier.ligayaButtonElevation(elevation: Dp = 4.dp, shape: Shape = LigayaShapes.pill): Modifier =
    ligayaElevation(elevation = elevation, shape = shape)
