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

    /**
     * The large, light, left-aligned statement that opens a screen — onboarding pages, permission
     * primers, empty states. Distinct from [headline] (a section title inside a dense screen) in
     * both weight and leading: this role exists to be the only thing on its half of the screen, so
     * it can afford to be airy where [headline] has to sit tight above content.
     */
    val introHeading = TextStyle(
        fontSize = 34.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.2).sp,
    )

    /**
     * The brand lockup only — "LIGAYA" beside the mark, nothing else. Light and widely tracked, in
     * deliberate contrast to every other role here: the rest of this scale is tuned for legibility
     * under stress, where weight and tightness help, while the wordmark is the one piece of type in
     * the app that is pure brand and never has to be read in a hurry.
     *
     * [letterSpacing] is the resting value; the splash animates it open from tighter, so callers
     * that animate tracking override it rather than the value here being the only one that ships.
     */
    val wordmark = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 12.sp,
    )
}
