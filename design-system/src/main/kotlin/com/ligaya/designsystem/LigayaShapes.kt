package com.ligaya.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner scale. Before the visual-design pass every radius in this codebase was written
 * inline at its use site (`RoundedCornerShape(percent = 50)` in two components, `CircleShape` in
 * two more, a bare `RoundedCornerShape(LigayaSpacing.sm)` on Home's cards) — four files each
 * deciding independently what "rounded" meant. These are the shared answers.
 *
 * Generous by design: the reference aesthetic is soft-edged throughout, and rounding is one of the
 * few things carrying that softness on a screen made mostly of rectangles.
 */
object LigayaShapes {
    /** Fully-rounded ends — primary buttons, chips, the page indicator. */
    val pill = RoundedCornerShape(percent = 50)

    /** Cards, list rows, and anything else sitting on the canvas as a distinct surface. */
    val card = RoundedCornerShape(20.dp)

    /** Text fields and other inline inputs — squarer than [card] so they read as editable. */
    val field = RoundedCornerShape(14.dp)

    /** Bottom sheets and full-width panels anchored to a screen edge. */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

    /** The height every full-width primary button uses, so CTAs line up screen to screen. */
    val primaryButtonHeight: Dp = 56.dp
}
