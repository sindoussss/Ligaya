package com.ligaya.designsystem.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * The mascot settings a person chose in Settings > Character & Animation, applied to every [LigayaMascot] in
 * the app rather than to one screen's copy of her.
 *
 * [motion] is the only one that negotiates with the screen it is on: [LigayaMotionMode.System] means "leave
 * each screen to decide", and anything else overrides the screen — a person who asks for stiller motion means
 * it everywhere, including the screens that would otherwise animate her fully. The phone's own reduce-motion
 * setting still wins over both.
 */
@Immutable
data class LigayaMascotPreferences(
    val animationSpeed: Float = 1.0f,
    val depthStrength: Float = 0.6f,
    val motion: LigayaMotionMode = LigayaMotionMode.System,
)

/** Defaults to the same values the engine starts with, so a preview with no provider looks like the app. */
val LocalLigayaMascotPreferences = compositionLocalOf { LigayaMascotPreferences() }
