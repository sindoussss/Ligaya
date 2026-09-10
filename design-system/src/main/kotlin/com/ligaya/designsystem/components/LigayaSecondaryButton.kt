package com.ligaya.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.rememberIsReduceMotionEnabled

/**
 * The quieter counterpart to [LigayaPrimaryButton]: a white pill with a hairline border, for
 * choices that sit beside the primary action rather than under it — the identity providers on the
 * account screen, secondary confirmations elsewhere.
 *
 * Same press feedback as the primary so the two never feel like they came from different apps, but
 * on a light fill the tonal shift has to be a tint rather than a darkening, or the button appears
 * to switch colour entirely on touch.
 */
@Composable
fun LigayaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberIsReduceMotionEnabled()

    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) PRESSED_SCALE else 1f,
        animationSpec = tween(LigayaMotion.durationFast, easing = LigayaMotion.easingStandard),
        label = "secondaryButtonScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LigayaShapes.primaryButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(LigayaShapes.pill)
            .background(if (pressed) LigayaColors.blush else LigayaColors.surface)
            .border(width = BORDER_WIDTH, color = LigayaColors.blushDeep, shape = LigayaShapes.pill)
            .selectable(
                selected = false,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(LigayaSpacing.sm))
        }
        Text(text = text, style = LigayaTypography.label, color = LigayaColors.ink)
    }
}

private val BORDER_WIDTH = 1.5.dp
private const val PRESSED_SCALE = 0.97f
