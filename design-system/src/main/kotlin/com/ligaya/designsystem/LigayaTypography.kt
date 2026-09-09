package com.ligaya.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Section 27's typography scale: "a clear hierarchy (display for emergency status, headline for
 * section titles, body for companion transcript, label for status chips) with a legible,
 * high-contrast typeface at large sizes."
 *
 * "Emergency screens should default to larger type sizes than normal-mode screens" is achieved by
 * role choice, not a second parallel scale: a screen's emergency-status text uses [display] (the
 * largest role here) while an idle/normal-mode screen has no reason to reach for [display] at
 * all — there is exactly one definition per role, not an idle/emergency pair for each, matching
 * how [LigayaColors] keeps one shared neutral rather than duplicating identical values.
 */
object LigayaTypography {
    val display = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold)
    val headline = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
}
