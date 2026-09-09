package com.ligaya.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.x relative-luminance/contrast-ratio formulas (the same math the Web Content
 * Accessibility Guidelines define), used by ContrastRatioTest to prove this step's acceptance
 * criterion — "token set passes WCAG AA contrast checks for all defined color pairs" — against
 * real numbers rather than by-eye judgement. Kept in main, not test-only, since any later step
 * that adds new tokens needs the exact same check, not a re-typed copy of it.
 */
private fun linearize(component: Float): Double {
    val c = component.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun relativeLuminance(color: Color): Double {
    val r = linearize(color.red)
    val g = linearize(color.green)
    val b = linearize(color.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG contrast ratio between two colors, always >= 1.0 regardless of argument order. */
fun contrastRatio(a: Color, b: Color): Double {
    val l1 = relativeLuminance(a)
    val l2 = relativeLuminance(b)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}
