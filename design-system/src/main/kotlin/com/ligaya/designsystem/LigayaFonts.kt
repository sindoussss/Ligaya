package com.ligaya.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * The brand sans from the visual design: a geometric face with a single-storey "a" and round "g",
 * bundled as one variable font file (res/font/ligaya_sans.ttf, SIL Open Font License — see
 * design-system/licenses/). One file, several weights read from its weight axis.
 */
@OptIn(ExperimentalTextApi::class)
object LigayaFonts {
    val sans: FontFamily = FontFamily(
        Font(R.font.ligaya_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.ligaya_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.ligaya_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    )
}
