package com.ligaya.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaSpacing

/**
 * The back affordance every step of the onboarding sequence carries.
 *
 * Sized to [LigayaSpacing.minTouchTarget] rather than to the glyph: an icon drawn at 24dp is well
 * under the 48dp floor this codebase holds itself to, and back is a control people reach for
 * without looking.
 */
@Composable
fun LigayaBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(LigayaSpacing.minTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = LigayaIcons.back,
            contentDescription = null,
            tint = LigayaColors.ink,
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

private val ICON_SIZE = 24.dp
