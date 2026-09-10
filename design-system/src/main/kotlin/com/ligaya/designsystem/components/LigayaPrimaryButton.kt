package com.ligaya.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.rememberIsReduceMotionEnabled

/**
 * The app's primary call to action: a full-width rose pill. The same control appears on nearly
 * every screen of the onboarding sequence, so it lives here rather than being re-declared per
 * screen with slightly different padding each time.
 *
 * Filled with [LigayaColors.roseDeep] rather than the softer [LigayaColors.rose] the reference
 * mockups suggest — deliberately, and the one place this design knowingly departs from them. A
 * button carries text, and the soft rose cannot hold white text at WCAG AA (see LigayaColors'
 * own ornament-vs-text split); roseDeep is the same family two steps darker and clears 4.95:1.
 *
 * Press feedback is a slight scale-down plus a tonal shift, both short ([LigayaMotion.durationFast])
 * because this is immediate touch response, not a state transition — and both suppressed under
 * reduce-motion, where the tonal shift alone still confirms the press.
 */
@Composable
fun LigayaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberIsReduceMotionEnabled()

    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) PRESSED_SCALE else 1f,
        animationSpec = tween(LigayaMotion.durationFast, easing = LigayaMotion.easingStandard),
        label = "primaryButtonScale",
    )
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> LigayaColors.rose
            pressed -> LigayaColors.ink
            else -> LigayaColors.roseDeep
        },
        animationSpec = tween(LigayaMotion.durationFast, easing = LigayaMotion.easingStandard),
        label = "primaryButtonContainer",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LigayaShapes.primaryButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(LigayaShapes.pill)
            .background(container)
            .selectable(
                selected = false,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                // Null indication: the scale + tonal shift above already are the press feedback,
                // and Material's default ripple on a fully-rounded rose pill reads as a grey
                // smudge against this palette rather than as touch response.
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = LigayaTypography.label, color = LigayaColors.onRoseDeep)
    }
}

private const val PRESSED_SCALE = 0.97f
