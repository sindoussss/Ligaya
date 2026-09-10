package com.ligaya.designsystem

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Section 27's motion tokens: "standardized durations/easings (fast for state feedback like
 * button press, slower/deliberate for state transitions like entering EMERGENCY_ACTIVE), plus a
 * documented reduced-motion fallback path (respect system 'remove animations' accessibility
 * setting)."
 *
 * [none] is that fallback path's token half: a component that actually animates (Step 34 onward)
 * is responsible for reading the system's reduce-motion setting and choosing [none] instead of
 * [durationFast]/[durationStateTransition] when it's on — a static token object has no way to
 * observe that setting itself, so this only defines what "no motion" resolves to, not the
 * observation logic.
 *
 * [rememberIsReduceMotionEnabled] (Step 45) is that observation logic, finally added: Android's
 * "Remove animations" accessibility toggle (Settings > Accessibility) is the same
 * ANIMATOR_DURATION_SCALE setting `ValueAnimator` itself checks internally — reading it directly
 * rather than inventing a separate signal. Read once per composition (`remember`, not a live
 * ContentObserver): the setting realistically only changes from a system settings screen the app
 * isn't visible during, matching how the platform's own animators already behave.
 */
object LigayaMotion {
    /** Button press and other immediate state feedback. */
    const val durationFast: Int = 100

    /** Deliberate state transitions, e.g. entering EMERGENCY_ACTIVE. */
    const val durationStateTransition: Int = 400

    /** The reduced-motion fallback value: an animation driven by this duration completes
     *  instantly rather than being skipped outright, so dependent state changes still resolve. */
    const val none: Int = 0

    /**
     * Brand/entrance moments — the splash lockup, a screen's first paint. Deliberately longer than
     * [durationStateTransition]: this is the one place in the app where motion is allowed to be
     * unhurried, because nothing is waiting on it. Anything mid-emergency uses the shorter tokens.
     */
    const val durationEntrance: Int = 900

    /**
     * How long the splash holds its finished lockup before handing off to the first real screen.
     * Kept short on purpose: the OS splash already occupied roughly a second of the launch before
     * this screen ever appeared, so the total brand moment is the sum of the two, not this alone.
     */
    const val durationSplashHold: Int = 500

    val easingStandard: Easing = FastOutSlowInEasing
    val easingLinear: Easing = LinearEasing

    /**
     * A long, soft decelerate — most of the distance is covered early, then it eases into place
     * over a comparatively long tail. This is what makes an entrance read as "smooth" rather than
     * merely "animated": no visible start jerk, no abrupt stop, and — unlike a spring — no
     * overshoot, which would fight the calm the visual design is built around.
     */
    val easingEntrance: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Symmetric ease for things that both appear and disappear, e.g. a fading overlay. */
    val easingGentle: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

@Composable
fun rememberIsReduceMotionEnabled(): Boolean {
    val contentResolver = LocalContext.current.contentResolver
    return remember(contentResolver) {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
