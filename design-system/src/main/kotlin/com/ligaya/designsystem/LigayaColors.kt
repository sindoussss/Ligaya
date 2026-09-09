package com.ligaya.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Section 27 of LIGAYA_ARCHITECTURE_FINAL_VOICE.md's "Design system foundations": semantic color
 * tokens, not hardcoded hex values in screens, so state colors stay consistent everywhere a state
 * is shown (§18 family screen, §14 companion, home).
 *
 * Three palettes, each self-contained (its own accent + light background), per the brief:
 *   - idle/normal mode: calm, desaturated
 *   - emergency active: distinct, unambiguous (red/amber family) — this token set only defines
 *     the *color*; never relying on hue alone to distinguish states is a composition concern for
 *     whatever later step actually builds screens (pairing color with icon/shape/text), not
 *     something a color token by itself can guarantee.
 *   - resolved/safe: green/calm blue
 *
 * Plus the four semantic tokens the brief names explicitly for delivery/emergency status:
 * [colorEmergencyActive], [colorStatusPending], [colorStatusConfirmed], [colorStatusFailed].
 *
 * [colorOnSurface] is the one shared neutral text/icon color used against every light background
 * below (idle/emergency/resolved) — deliberately not three separately-named identical values,
 * since the brief's requirement is that *state-indicating* colors are consistent, not that every
 * palette duplicates its own copy of the same neutral dark-gray.
 *
 * Every pairing below is proven to meet WCAG AA (this step's own acceptance criterion) by
 * ContrastRatioTest, computed against these exact values — change a value here and that test is
 * what catches a regression, not a second copy of the numbers in a comment.
 */
object LigayaColors {

    // --- Shared neutral ---
    val onSurface = Color(0xFF1B1F24)

    // --- Idle / normal mode: calm, desaturated ---
    val idleBackground = Color(0xFFF5F6F8)
    val idlePrimary = Color(0xFF3A6EA5)
    val onIdlePrimary = Color(0xFFFFFFFF)

    // --- Emergency active: distinct, unambiguous (red/amber family) ---
    val colorEmergencyActive = Color(0xFFC62828)
    val onEmergencyActive = Color(0xFFFFFFFF)
    val emergencyBackground = Color(0xFFFFF5F5)

    // --- Resolved / safe: green / calm blue ---
    val resolved = Color(0xFF2E7D32)
    val onResolved = Color(0xFFFFFFFF)
    val resolvedBackground = Color(0xFFF1F8F2)

    // --- Delivery/status tokens (named explicitly in the brief) ---
    val colorStatusPending = Color(0xFFF9A825)
    // Amber only clears WCAG AA against a dark foreground, not white — see ContrastRatioTest.
    val onStatusPending = onSurface
    val colorStatusConfirmed = resolved
    val onStatusConfirmed = onResolved
    val colorStatusFailed = colorEmergencyActive
    val onStatusFailed = onEmergencyActive
}
