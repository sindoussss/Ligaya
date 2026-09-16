package com.ligaya.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.components.LigayaComponentPreviews
import kotlin.math.min

/**
 * The Ligaya mark: a five-petal flower, one petal pointing straight up, the petals set in a slight
 * pinwheel so each overlaps the next — the brand sheet's logo.
 *
 * Drawn as vector paths (not a raster asset) so it holds up at every size it appears at, and so
 * [revealProgress] can bloom the petals one after another. The platform copies of this mark
 * (app/res/drawable/ic_ligaya_mark.xml and ic_launcher_foreground.xml, drawn by the OS before any
 * Compose code runs) use the same petal geometry; change one, change them together.
 *
 * Decorative by default ([contentDescription] null): next to the "Ligaya" wordmark, which is real
 * text, a described logo would make a screen reader announce the name twice.
 */
@Composable
fun LigayaLogo(
    modifier: Modifier = Modifier,
    revealProgress: Float = 1f,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    // Read in composable scope, used inside the draw lambda below (which is not composable scope).
    val petalColor = LigayaTheme.colors.petal
    val petalDeepColor = LigayaTheme.colors.petalDeep

    Canvas(modifier = modifier.then(semanticsModifier)) {
        val radius = min(size.width, size.height) / 2f
        // The soft white halo goes down first, under every petal, so it only shows round the outline.
        for (index in 0 until PETAL_COUNT) {
            drawPetal(index, center, radius, revealProgress, halo = true, petalColor = petalColor, petalDeepColor = petalDeepColor)
        }
        for (index in 0 until PETAL_COUNT) {
            drawPetal(index, center, radius, revealProgress, halo = false, petalColor = petalColor, petalDeepColor = petalDeepColor)
        }
    }
}

private const val PETAL_COUNT = 5

/** Petal length and half-width at its widest, as fractions of the mark's radius. */
private const val PETAL_LENGTH = 0.98f
private const val PETAL_HALF_WIDTH = 0.25f

/** How far each petal's base reaches past the centre, so the petals overlap there with no gap. */
private const val PETAL_BASE_OVERLAP = 0.05f

/** Sideways offset of each petal's axis from the centre — what turns five spokes into a pinwheel. */
private const val PINWHEEL_OFFSET = 0.05f

/** The darker line where one petal crosses the next — what keeps five petals from reading as one star. */
private val PETAL_SEAM = Color(0xFF9A6A57)

private const val STAGGER_PER_PETAL = 0.11f
private const val PETAL_REVEAL_WINDOW = 0.56f
private const val PETAL_START_SCALE = 0.6f

// The petal colours are passed in rather than read here: a DrawScope lambda is not composable scope, so it cannot
// read the theme itself — the composable above reads them once and hands them down.
private fun DrawScope.drawPetal(
    index: Int,
    centre: Offset,
    radius: Float,
    revealProgress: Float,
    halo: Boolean,
    petalColor: Color,
    petalDeepColor: Color,
) {
    val raw = ((revealProgress - index * STAGGER_PER_PETAL) / PETAL_REVEAL_WINDOW).coerceIn(0f, 1f)
    if (raw <= 0f) return
    val eased = LigayaMotion.easingEntrance.transform(raw)

    val length = radius * PETAL_LENGTH
    val half = radius * PETAL_HALF_WIDTH
    val x = centre.x + radius * PINWHEEL_OFFSET
    val base = Offset(x, centre.y + radius * PETAL_BASE_OVERLAP)
    val tipY = centre.y - length

    // An oval petal: narrow where it meets the centre, full through the middle, rounded tip.
    val petal = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(x - half * 1.45f, centre.y - length * 0.42f, x - half * 0.8f, tipY + length * 0.02f, x, tipY)
        cubicTo(x + half * 0.8f, tipY + length * 0.02f, x + half * 1.45f, centre.y - length * 0.42f, base.x, base.y)
        close()
    }
    val petalScale = PETAL_START_SCALE + (1f - PETAL_START_SCALE) * eased

    rotate(degrees = index * 360f / PETAL_COUNT, pivot = centre) {
        scale(scale = petalScale, pivot = centre) {
            if (halo) {
                drawPath(path = petal, color = Color.White.copy(alpha = 0.9f * eased), style = Stroke(width = radius * 0.12f))
                return@scale
            }
            drawPath(
                path = petal,
                brush = Brush.linearGradient(listOf(petalDeepColor, petalColor), start = base, end = Offset(x, tipY)),
                alpha = eased,
            )
            // One side of each petal a touch deeper, like the fold in the brand mark.
            drawPath(
                path = petal,
                brush = Brush.horizontalGradient(
                    listOf(petalDeepColor.copy(alpha = 0.55f), Color.Transparent),
                    startX = x - half,
                    endX = x + half * 0.2f,
                ),
                alpha = eased,
            )
            // A faint darker edge keeps each petal distinct where it crosses its neighbour.
            drawPath(path = petal, color = PETAL_SEAM.copy(alpha = 0.55f * eased), style = Stroke(width = radius * 0.025f))
        }
    }
}

@LigayaComponentPreviews
@Composable
private fun LigayaLogoPreview() {
    Box(modifier = Modifier.background(LigayaTheme.colors.canvas).padding(LigayaSpacing.lg)) {
        LigayaLogo(modifier = Modifier.size(96.dp))
    }
}
