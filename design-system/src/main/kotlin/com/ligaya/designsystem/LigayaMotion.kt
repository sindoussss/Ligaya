package com.ligaya.designsystem

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing

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
 */
object LigayaMotion {
    /** Button press and other immediate state feedback. */
    const val durationFast: Int = 100

    /** Deliberate state transitions, e.g. entering EMERGENCY_ACTIVE. */
    const val durationStateTransition: Int = 400

    /** The reduced-motion fallback value: an animation driven by this duration completes
     *  instantly rather than being skipped outright, so dependent state changes still resolve. */
    const val none: Int = 0

    val easingStandard: Easing = FastOutSlowInEasing
    val easingLinear: Easing = LinearEasing
}
