package com.ligaya.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography

/**
 * Section 27's screen inventory: "Large, unmistakable, single dominant control; distinct idle
 * vs. pressed vs. activating visual states; must remain usable one-handed under stress." Fixed
 * at [LigayaColors.colorEmergencyActive] regardless of light/dark theme — a safety-critical
 * control like this should read identically no matter the system theme, the same reasoning
 * [LigayaColors] already applies by defining it as one absolute token rather than a themed role.
 *
 * [SosControlState.Pressed]'s color is derived from the same base token (darkened, not a second
 * unrelated literal) so the three states read as one control's states, not three different colors.
 */
enum class SosControlState { Idle, Pressed, Activating }

@Composable
fun SosControl(
    state: SosControlState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when (state) {
        SosControlState.Idle -> LigayaColors.colorEmergencyActive
        SosControlState.Pressed -> lerp(LigayaColors.colorEmergencyActive, Color.Black, 0.15f)
        SosControlState.Activating -> LigayaColors.colorEmergencyActive
    }
    val description = when (state) {
        SosControlState.Idle, SosControlState.Pressed -> "Send SOS emergency alert"
        SosControlState.Activating -> "Activating emergency alert"
    }

    Box(
        modifier = modifier
            .size(LigayaSpacing.emergencyTouchTarget)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = state != SosControlState.Activating, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (state == SosControlState.Activating) {
            CircularProgressIndicator(
                color = LigayaColors.onEmergencyActive,
                modifier = Modifier.size(LigayaSpacing.emergencyTouchTarget - LigayaSpacing.lg),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Emergency,
                    contentDescription = null,
                    tint = LigayaColors.onEmergencyActive,
                    modifier = Modifier.size(LigayaSpacing.xxl),
                )
                Text(
                    text = "SOS",
                    style = LigayaTypography.label,
                    color = LigayaColors.onEmergencyActive,
                )
            }
        }
    }
}

@LigayaComponentPreviews
@Composable
private fun SosControlIdlePreview() {
    MaterialTheme { SosControl(state = SosControlState.Idle, onClick = {}) }
}

@LigayaComponentPreviews
@Composable
private fun SosControlPressedPreview() {
    MaterialTheme { SosControl(state = SosControlState.Pressed, onClick = {}) }
}

@LigayaComponentPreviews
@Composable
private fun SosControlActivatingPreview() {
    MaterialTheme { SosControl(state = SosControlState.Activating, onClick = {}) }
}
