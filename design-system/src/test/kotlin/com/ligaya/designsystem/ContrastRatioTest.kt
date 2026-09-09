package com.ligaya.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 33's own acceptance criterion, made concrete: "token set passes WCAG AA contrast checks
 * for all defined color pairs." Every foreground/background pair LigayaColors actually pairs a
 * token with (an "on___" token against the surface it's meant to sit on) is checked here against
 * 4.5:1 — the stricter of the two AA minimums (section 27's own "Accessibility targets": 4.5:1
 * body text, 3:1 large text/icons) — since these are general-purpose tokens that could be used
 * for body-sized text, not just large text/icons.
 */
class ContrastRatioTest {

    private val wcagAaBodyTextMinimum = 4.5

    private val pairs: List<Triple<String, Color, Color>> = listOf(
        Triple("onSurface on idleBackground", LigayaColors.onSurface, LigayaColors.idleBackground),
        Triple("onIdlePrimary on idlePrimary", LigayaColors.onIdlePrimary, LigayaColors.idlePrimary),
        Triple("onEmergencyActive on colorEmergencyActive", LigayaColors.onEmergencyActive, LigayaColors.colorEmergencyActive),
        Triple("onSurface on emergencyBackground", LigayaColors.onSurface, LigayaColors.emergencyBackground),
        Triple("onResolved on resolved", LigayaColors.onResolved, LigayaColors.resolved),
        Triple("onSurface on resolvedBackground", LigayaColors.onSurface, LigayaColors.resolvedBackground),
        Triple("onStatusPending on colorStatusPending", LigayaColors.onStatusPending, LigayaColors.colorStatusPending),
        Triple("onStatusConfirmed on colorStatusConfirmed", LigayaColors.onStatusConfirmed, LigayaColors.colorStatusConfirmed),
        Triple("onStatusFailed on colorStatusFailed", LigayaColors.onStatusFailed, LigayaColors.colorStatusFailed),
    )

    @Test
    fun `every defined foreground-background pair meets WCAG AA for body text`() {
        for ((label, foreground, background) in pairs) {
            val ratio = contrastRatio(foreground, background)
            assertTrue(
                "$label must be >= $wcagAaBodyTextMinimum:1, was ${"%.2f".format(ratio)}:1",
                ratio >= wcagAaBodyTextMinimum,
            )
        }
    }

    @Test
    fun `contrastRatio is symmetric regardless of argument order`() {
        for ((_, foreground, background) in pairs) {
            assertTrue(
                contrastRatio(foreground, background) == contrastRatio(background, foreground),
            )
        }
    }

    @Test
    fun `contrastRatio of a color against itself is exactly 1`() {
        for (color in listOf(LigayaColors.onSurface, LigayaColors.idlePrimary, LigayaColors.colorEmergencyActive)) {
            assertTrue(contrastRatio(color, color) == 1.0)
        }
    }
}
