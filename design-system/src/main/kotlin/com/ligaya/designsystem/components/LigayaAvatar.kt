package com.ligaya.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.R

/**
 * Ligaya's round portrait, for places too small for the live mascot (chat header and message rows). A still
 * image rendered from the mascot's own artwork in her resting pose, so she looks the same here as she does
 * animated on Home. Decorative: her name is always shown as text next to it.
 */
@Composable
fun LigayaAvatar(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ligaya_avatar),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LigayaColors.creamDeep)
            .border(2.dp, LigayaColors.shell, CircleShape),
    )
}
