package com.ligaya.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme

/** What the user chose in Settings > Appearance. [System] follows the phone's own light/dark setting. */
enum class LigayaThemeMode { Light, Dark, System }

/**
 * Provides the palette every screen paints with, so one composable hierarchy can be light or dark.
 *
 * Deliberately a `staticCompositionLocalOf`: the theme changes rarely (a setting, or the system flipping at
 * sunset) and reading it should cost nothing on every recomposition in between.
 */
val LocalLigayaPalette = staticCompositionLocalOf { LigayaLightPalette }

object LigayaTheme {
    /** The colours for the theme in force here. Screens use this rather than [LigayaColors]. */
    val colors: LigayaPalette
        @Composable @ReadOnlyComposable get() = LocalLigayaPalette.current
}

/**
 * Wraps the app in a palette and a matching Material scheme.
 *
 * Material's own scheme is set too, not just ours: dialogs, ripples, text selection handles and anything else
 * drawn by Material rather than by our screens would otherwise stay light on a dark page.
 */
@Composable
fun LigayaTheme(
    mode: LigayaThemeMode = LigayaThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        LigayaThemeMode.Light -> false
        LigayaThemeMode.Dark -> true
        LigayaThemeMode.System -> isSystemInDarkTheme()
    }
    val palette = if (dark) LigayaDarkPalette else LigayaLightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.berry,
            onPrimary = palette.onBerry,
            background = palette.cream,
            onBackground = palette.cocoaInk,
            surface = palette.shell,
            onSurface = palette.cocoaInk,
            // Mapped, not left to Material: unmapped, these fall back to Material's own blue-greys, which
            // is where the voice chip's blue microphone came from on the dark Home.
            surfaceVariant = palette.shell,
            onSurfaceVariant = palette.cocoaInk,
            outline = palette.shellEdge,
            error = palette.colorStatusFailed,
        )
    } else {
        lightColorScheme(
            primary = palette.berry,
            onPrimary = palette.onBerry,
            background = palette.cream,
            onBackground = palette.cocoaInk,
            surface = palette.shell,
            onSurface = palette.cocoaInk,
            // Mapped, not left to Material: unmapped, these fall back to Material's own blue-greys, which
            // is where the voice chip's blue microphone came from on the dark Home.
            surfaceVariant = palette.shell,
            onSurfaceVariant = palette.cocoaInk,
            outline = palette.shellEdge,
            error = palette.colorStatusFailed,
        )
    }
    CompositionLocalProvider(LocalLigayaPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
