package com.ligaya.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import kotlinx.coroutines.delay

/**
 * Screen 1 of the visual design: the brand moment before the app's first real screen.
 *
 * Everything here is driven by ONE [Animatable] master timeline rather than several independently
 * launched animations. That matters for smoothness specifically: a single animation runs on a
 * single frame clock, so the logo, wordmark and tagline can never drift out of phase with each
 * other the way three separately-started coroutines can. Each element then takes its own slice of
 * that timeline (see [subProgress]) and applies its own easing.
 *
 * The master timeline itself is deliberately [LinearEasing] — each element eases its own slice, and
 * easing the master too would ease the same motion twice, which visibly bunches the stagger up at
 * the start and stretches it at the end.
 *
 * Reduced motion is a real branch, not a shorter duration: the lockup is snapped to its finished
 * state and simply held. Critically, [onFinished] still fires on that path — a splash that only
 * advanced when its animation completed would strand a reduce-motion user on this screen forever,
 * which is exactly the kind of accessibility trap the setting is meant to avoid.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val timeline = remember { Animatable(0f) }
    val lockupAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            timeline.snapTo(1f)
            delay(REDUCED_MOTION_HOLD_MILLIS)
        } else {
            timeline.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = ENTRANCE_MILLIS, easing = LinearEasing),
            )
            delay(LigayaMotion.durationSplashHold.toLong())
            lockupAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = EXIT_MILLIS, easing = LigayaMotion.easingGentle),
            )
        }
        onFinished()
    }

    val t = timeline.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    // Weighted stops, not an even three-way split: the blush is meant to settle
                    // into the lower third like light pooling, not wash the whole screen pink.
                    0f to LigayaColors.canvas,
                    0.55f to LigayaColors.blush,
                    1f to LigayaColors.blushDeep,
                ),
            )
            .alpha(lockupAlpha.value),
    ) {
        // The mark is centred on its own, with the wordmark offset below it, rather than the two
        // being centred together as a column. The system splash-screen window centres its icon,
        // so anything that shifted the mark off centre here would make it visibly jump at the
        // exact moment the OS window hands over to this composable.
        //
        // It also arrives already at full reveal and oversized, then settles down to rest. Both
        // are about that same handoff, and both were measured rather than guessed: comparing
        // screenshots of the two splashes, the OS draws this mark at roughly 180dp wide while the
        // resting Compose lockup is about 78dp, so a naive hand-off popped the mark down by ~2.3x
        // and re-grew leaves the user had already been looking at for a second. Starting at that
        // measured scale and easing to 1.0 turns the seam into the transition itself.
        val settle = LigayaMotion.easingEntrance.transform(subProgress(t, LOGO_START, LOGO_END))
        val logoScale = HANDOFF_START_SCALE + (1f - HANDOFF_START_SCALE) * settle
        LigayaLogo(
            modifier = Modifier
                .align(Alignment.Center)
                .size(LOGO_SIZE)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                },
            revealProgress = 1f,
        )

        val wordmark = LigayaMotion.easingEntrance.transform(subProgress(t, WORDMARK_START, WORDMARK_END))
        Text(
            text = "LIGAYA",
            style = LigayaTypography.wordmark.copy(
                // Tracking opens outward as it fades in — the letters settle into place rather
                // than simply appearing, which is what gives the lockup its unhurried feel.
                letterSpacing = lerpSp(WORDMARK_TRACKING_FROM, WORDMARK_TRACKING_TO, wordmark),
            ),
            color = LigayaColors.ink,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = WORDMARK_OFFSET_BELOW_CENTRE)
                .alpha(wordmark),
        )

        val tagline = LigayaMotion.easingEntrance.transform(subProgress(t, TAGLINE_START, TAGLINE_END))
        Text(
            text = "Your voice. Their help.",
            style = LigayaTypography.body,
            color = LigayaColors.inkSoft,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = TAGLINE_BOTTOM_PADDING)
                .alpha(tagline),
        )
    }
}

/** Where [master] sits within one element's own [start]..[end] slice of the shared timeline. */
private fun subProgress(master: Float, start: Float, end: Float): Float =
    ((master - start) / (end - start)).coerceIn(0f, 1f)

private fun lerpSp(from: Float, to: Float, fraction: Float) = (from + (to - from) * fraction).sp

private val LOGO_SIZE = 108.dp
private val TAGLINE_BOTTOM_PADDING = 72.dp

/** Clears the centred mark's lower half plus breathing room, so the two never collide. */
private val WORDMARK_OFFSET_BELOW_CENTRE = 92.dp

/**
 * Where the mark starts, relative to its resting size — measured from screenshots of the OS
 * splash beside this one (see the handoff comment above), not chosen by eye. Exact parity across
 * every device isn't achievable (the platform sizes its splash icon itself), but starting near it
 * makes the settle read as deliberate on any of them, where starting at 1.0 reads as a glitch.
 */
private const val HANDOFF_START_SCALE = 2.3f

/** Full entrance length. The per-element slices below are fractions of this. */
private const val ENTRANCE_MILLIS = 1200
private const val EXIT_MILLIS = 420

/** Reduced motion still holds long enough to register as a brand moment, then advances. */
private const val REDUCED_MOTION_HOLD_MILLIS = 900L

// Overlapping slices, not sequential ones: each element begins while the previous is still
// settling, which is what makes the sequence read as one continuous motion instead of three.
private const val LOGO_START = 0f
private const val LOGO_END = 0.75f
private const val WORDMARK_START = 0.35f
private const val WORDMARK_END = 0.95f
private const val TAGLINE_START = 0.60f
private const val TAGLINE_END = 1f

private const val WORDMARK_TRACKING_FROM = 4f
private const val WORDMARK_TRACKING_TO = 12f
