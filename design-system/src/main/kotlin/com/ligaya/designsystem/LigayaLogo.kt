package com.ligaya.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.components.LigayaComponentPreviews

/**
 * The Ligaya mark: four leaves fanning from a single base point, in the brand's dusty-rose family.
 *
 * Real vector art, drawn as Bézier paths rather than a raster asset — LigayaIcons' own doc comment
 * had noted "no custom vector art was produced for this step," which the visual-design pass is
 * what finally changes. Paths (not a PNG) specifically because this mark has to hold up at every
 * size it appears at — a 28dp header lockup, a 96dp splash, an adaptive launcher icon — and
 * because [revealProgress] animates the leaves individually, which a flat image can't do.
 *
 * Leaves are drawn outermost-first so the two tall inner leaves overlap on top, and each carries a
 * slightly different [Leaf.tone] blend between [LigayaColors.rose] and [LigayaColors.roseDeep] —
 * that tonal variation is what keeps the mark from reading as a flat silhouette at small sizes.
 *
 * Decorative by default ([contentDescription] null): wherever this appears next to the "LIGAYA"
 * wordmark, the wordmark is real text and already announces the brand — a described logo beside it
 * would make a screen reader say the name twice.
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

    Canvas(modifier = modifier.then(semanticsModifier)) {
        // Base sits below centre so the fan is optically centred rather than mathematically
        // centred — a fan of leaves is top-heavy, so a true centre reads as sitting too low.
        val base = Offset(size.width / 2f, size.height * 0.88f)
        LEAVES.forEachIndexed { index, leaf ->
            drawLeaf(leaf, index, base, revealProgress)
        }
    }
}

private data class Leaf(
    /** Degrees from vertical; negative leans left. */
    val angleDegrees: Float,
    /** Tip distance from the base, as a fraction of the canvas height. */
    val length: Float,
    /** Half-width at the leaf's widest point, as a fraction of the canvas height. */
    val halfWidth: Float,
    /** 0 = [LigayaColors.rose], 1 = [LigayaColors.roseDeep]. */
    val tone: Float,
)

/** Outermost first: the two tall inner leaves are drawn last so they overlap on top. */
private val LEAVES = listOf(
    Leaf(angleDegrees = -52f, length = 0.46f, halfWidth = 0.105f, tone = 0.10f),
    Leaf(angleDegrees = 52f, length = 0.44f, halfWidth = 0.100f, tone = 0.28f),
    Leaf(angleDegrees = -18f, length = 0.72f, halfWidth = 0.125f, tone = 0.55f),
    Leaf(angleDegrees = 18f, length = 0.68f, halfWidth = 0.120f, tone = 0.88f),
)

/** Each leaf starts this fraction of [revealProgress] after the previous one. */
private const val STAGGER_PER_LEAF = 0.13f

/** How much of [revealProgress] a single leaf's own reveal occupies. */
private const val LEAF_REVEAL_WINDOW = 0.58f

/** Leaves grow from this fraction of full size, about their own base point. */
private const val LEAF_START_SCALE = 0.55f

private fun DrawScope.drawLeaf(leaf: Leaf, index: Int, base: Offset, revealProgress: Float) {
    val start = index * STAGGER_PER_LEAF
    val raw = ((revealProgress - start) / LEAF_REVEAL_WINDOW).coerceIn(0f, 1f)
    if (raw <= 0f) return
    // Same easing the splash uses for its own entrance, so a leaf settling and the wordmark
    // settling share one motion character rather than two subtly different ones.
    val eased = LigayaMotion.easingEntrance.transform(raw)

    val height = size.height
    val length = leaf.length * height
    val halfWidth = leaf.halfWidth * height

    val path = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(base.x - halfWidth, base.y - length * 0.45f, base.x, base.y - length)
        quadraticTo(base.x + halfWidth, base.y - length * 0.45f, base.x, base.y)
        close()
    }

    val color = lerp(LigayaColors.rose, LigayaColors.roseDeep, leaf.tone)
    val leafScale = LEAF_START_SCALE + (1f - LEAF_START_SCALE) * eased

    rotate(degrees = leaf.angleDegrees, pivot = base) {
        scale(scale = leafScale, pivot = base) {
            drawPath(path = path, color = color.copy(alpha = eased))
        }
    }
}

@LigayaComponentPreviews
@Composable
private fun LigayaLogoPreview() {
    Box(modifier = Modifier.background(LigayaColors.canvas).padding(LigayaSpacing.lg)) {
        LigayaLogo(modifier = Modifier.size(96.dp))
    }
}
