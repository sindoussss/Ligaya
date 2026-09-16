package com.ligaya.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same WCAG AA bar [ContrastRatioTest] holds the light palette to, applied to the dark one.
 *
 * Dark mode is where contrast quietly goes wrong: a token that was deep ink on cream becomes deep ink on
 * near-black and vanishes. That is exactly what happened on the first pass here — the selected tab and the menu
 * icon stayed dark brown on the dark bar and were effectively invisible on the device — so the pairs are pinned
 * rather than eyeballed.
 */
class DarkPaletteContrastTest {

    private val bodyTextMinimum = 4.5

    /** Icons and large type may sit at 3:1 under AA; body text may not. */
    private val iconMinimum = 3.0

    private val dark = LigayaDarkPalette

    private val textPairs: List<Triple<String, Color, Color>> = listOf(
        Triple("cocoaInk on cream", dark.cocoaInk, dark.cream),
        Triple("cocoaInk on creamDeep", dark.cocoaInk, dark.creamDeep),
        Triple("cocoaInk on shell", dark.cocoaInk, dark.shell),
        Triple("taupe on cream", dark.taupe, dark.cream),
        Triple("taupe on shell", dark.taupe, dark.shell),
        Triple("ink on canvas", dark.ink, dark.canvas),
        Triple("inkSoft on canvas", dark.inkSoft, dark.canvas),
        Triple("onSurface on idleBackground", dark.onSurface, dark.idleBackground),
        Triple("onBerry on berry", dark.onBerry, dark.berry),
        Triple("onCocoa on cocoa", dark.onCocoa, dark.cocoa),
        Triple("cocoaInk on bubbleLigaya", dark.cocoaInk, dark.bubbleLigaya),
        Triple("cocoaInk on bubbleUser", dark.cocoaInk, dark.bubbleUser),
        Triple("cocoaInk on listeningDisc", dark.cocoaInk, dark.listeningDisc),
        Triple("onEmergencyActive on colorEmergencyActive", dark.onEmergencyActive, dark.colorEmergencyActive),
    )

    private val iconPairs: List<Triple<String, Color, Color>> = listOf(
        // The tab bar paints the selected tab with cocoa and the rest with taupe, both on shell.
        Triple("cocoa (selected tab) on shell", dark.cocoa, dark.shell),
        Triple("taupe (unselected tab) on shell", dark.taupe, dark.shell),
        // Home's header icons sit straight on the page.
        Triple("cocoa (header icons) on cream", dark.cocoa, dark.cream),
    )

    @Test
    fun `dark text pairs meet WCAG AA for body text`() {
        for ((label, foreground, background) in textPairs) {
            val ratio = contrastRatio(foreground, background)
            assertTrue(
                "dark: $label must be >= $bodyTextMinimum:1, was ${"%.2f".format(ratio)}:1",
                ratio >= bodyTextMinimum,
            )
        }
    }

    @Test
    fun `dark icon pairs meet WCAG AA for icons and large text`() {
        for ((label, foreground, background) in iconPairs) {
            val ratio = contrastRatio(foreground, background)
            assertTrue(
                "dark: $label must be >= $iconMinimum:1, was ${"%.2f".format(ratio)}:1",
                ratio >= iconMinimum,
            )
        }
    }

    @Test
    fun `the dark page is actually dark and the light page actually light`() {
        // Guards against a regeneration silently swapping the two palettes.
        assertTrue("dark page should be darker than its own text", contrastRatio(dark.cream, Color.Black) < contrastRatio(dark.cocoaInk, Color.Black))
        assertTrue("light page should be lighter than its own text", contrastRatio(LigayaLightPalette.cream, Color.Black) > contrastRatio(LigayaLightPalette.cocoaInk, Color.Black))
    }
}
