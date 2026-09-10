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

    // --- Brand palette (the four swatches on the brand sheet) -------------------------------
    //
    // The visual-design pass's own foundation: a warm, desaturated dusty-rose family, chosen so
    // normal mode reads calm and cared-for rather than clinical. Deliberately split into
    // decorative tones and text-bearing tones, because the soft end of this palette cannot carry
    // text at WCAG AA (4.5:1) no matter how good it looks:
    //   - [blush]/[blushDeep]/[rose] are surface/ornament only — never a text or icon color.
    //   - [roseDeep] is the one rose dark enough to carry white text (4.95:1, ContrastRatioTest).
    // Keeping that split explicit here is what stops a later screen from reaching for `rose` as a
    // button fill and quietly failing the contrast bar this codebase already holds itself to.

    /** Softest wash — splash/gradient backgrounds, selected-row tints. Ornament only. */
    val blush = Color(0xFFF7EBE8)

    /** A step deeper than [blush], for gradient ends and card tints. Ornament only. */
    val blushDeep = Color(0xFFF0DAD6)

    /** The signature dusty rose: the logo mark, decorative strokes. Ornament only. */
    val rose = Color(0xFFC48B8B)

    /** The one rose deep enough for white text on top — primary buttons, brand accents. */
    val roseDeep = Color(0xFF9C5F66)
    val onRoseDeep = Color(0xFFFFFFFF)

    /** Warm off-white page background — the app's default canvas, warmer than [idleBackground]. */
    val canvas = Color(0xFFFAF7F6)

    /** Cards and sheets sitting on [canvas]. */
    val surface = Color(0xFFFFFFFF)

    /** Warm near-black for primary text — softer than pure black, still ~14:1 on [canvas]. */
    val ink = Color(0xFF2E2A2B)

    /** Warm gray for secondary/supporting text. Tuned to clear 4.5:1 on [canvas], not by eye. */
    val inkSoft = Color(0xFF756A6C)

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
