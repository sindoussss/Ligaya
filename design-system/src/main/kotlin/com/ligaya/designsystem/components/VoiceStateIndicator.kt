package com.ligaya.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaVoiceState
import com.ligaya.designsystem.rememberIsReduceMotionEnabled

/**
 * Section 27's screen inventory: "A persistent, always-visible visual (e.g., an animated
 * waveform/orb) with distinct states: idle/ready, listening, processing (STT/Gemini round-trip),
 * speaking (TTS) — must communicate this without requiring the user to read text." This step's
 * own scope is explicitly "not yet wired to real data" — these are the four visual states only;
 * whatever later step actually drives this from VoiceCaptureCoordinator/EmergencyCompanionState
 * decides when each one applies.
 *
 * Uses [MaterialTheme]'s surface roles as its base (this indicator sits in normal UI chrome, not
 * a fixed safety color like [SosControl]/[StatusCard]'s failure tone), with [listening]'s pulse
 * as the one state that visibly moves — [LigayaMotion.durationStateTransition] paced, matching the
 * brief's "slower/deliberate" guidance for anything more than instant feedback.
 *
 * Step 45: the pulse itself is the "component that actually animates" [LigayaMotion]'s own doc
 * comment named as responsible for observing reduce-motion — [rememberIsReduceMotionEnabled] on,
 * and the infinite pulse transition is skipped entirely (held at a constant, non-pulsing scale)
 * rather than looping with a zero-length tween, which would still churn a frame every repeat for
 * no visible effect. The state is still communicated (the icon/color/contentDescription below
 * never depended on the animation), just not via motion.
 */
@Composable
fun VoiceStateIndicator(
    state: LigayaVoiceState,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val pulse: Float = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "voiceStatePulse")
        val animatedPulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = if (state == LigayaVoiceState.LISTENING) 1.15f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = LigayaMotion.durationStateTransition, easing = LigayaMotion.easingStandard),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "voiceStatePulseScale",
        )
        animatedPulse
    }

    val description = when (state) {
        LigayaVoiceState.IDLE -> "Voice assistant idle"
        LigayaVoiceState.LISTENING -> "Voice assistant listening"
        LigayaVoiceState.PROCESSING -> "Voice assistant processing"
        LigayaVoiceState.SPEAKING -> "Voice assistant speaking"
    }

    Box(
        modifier = modifier
            .size(LigayaSpacing.xxl)
            .scale(pulse)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = requireNotNull(LigayaIcons.voiceState[state]) { "no icon mapped for $state" },
            contentDescription = null,
            tint = if (state == LigayaVoiceState.IDLE) MaterialTheme.colorScheme.onSurfaceVariant else LigayaTheme.colors.idlePrimary,
        )
    }
}

@LigayaComponentPreviews
@Composable
private fun VoiceStateIndicatorPreview() {
    MaterialTheme {
        Row {
            LigayaVoiceState.entries.forEach { VoiceStateIndicator(state = it) }
        }
    }
}
