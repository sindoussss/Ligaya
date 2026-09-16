package com.ligaya.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.rememberIsReduceMotionEnabled

/**
 * Visual design, screen 1: the welcome screen — the flower mark, "Ligaya", the tagline and a
 * "Get Started" pill over soft blush waves.
 *
 * Shown until the user taps Get Started once; after that the app opens straight to Home (see
 * MainActivity), so on an emergency app the SOS control is never behind an extra tap.
 *
 * The entrance runs on one timeline, each element easing its own slice of it so they can't drift
 * out of phase; with reduced motion everything is simply shown at rest.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val timeline = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) timeline.animateTo(1f, tween(durationMillis = ENTRANCE_MILLIS, easing = LinearEasing))
    }
    val t = timeline.value

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(LigayaTheme.colors.cream)) {
        val screenHeight = maxHeight
        BlushWaves(Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = screenHeight * LOGO_CENTRE_FRACTION - LOGO_SIZE / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val logo = slice(t, 0f, 0.8f)
            LigayaLogo(
                modifier = Modifier.size(LOGO_SIZE).rise(slice(t, 0f, 0.6f)),
                revealProgress = logo,
            )
            Text(
                text = "Ligaya",
                style = LigayaTypography.welcomeWordmark,
                color = LigayaTheme.colors.cocoaInk,
                // The font's ascent padding leaves a gap above the capitals; pull it up to sit under the mark.
                modifier = Modifier.offset(y = -WORDMARK_LIFT).rise(slice(t, 0.25f, 0.8f)),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "More than an assistant.\nA kaibigan, always.",
                style = LigayaTypography.welcomeTagline,
                color = LigayaTheme.colors.taupe,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = -WORDMARK_LIFT).rise(slice(t, 0.4f, 0.95f)),
            )
        }

        GetStartedPill(
            onClick = onGetStarted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = screenHeight * BUTTON_BOTTOM_FRACTION)
                .fillMaxWidth(BUTTON_WIDTH_FRACTION)
                .height(BUTTON_HEIGHT)
                .rise(slice(t, 0.55f, 1f)),
        )
    }
}

@Composable
private fun GetStartedPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = LigayaTheme.colors.cocoa, spotColor = LigayaTheme.colors.cocoa)
            .clip(CircleShape)
            .background(LigayaTheme.colors.cocoa)
            .border(1.5.dp, LigayaTheme.colors.onCocoa.copy(alpha = 0.35f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Get Started", style = LigayaTypography.pillLabel, color = LigayaTheme.colors.onCocoa)
            // Read here, in composable scope: the draw lambda below is a DrawScope and cannot read the theme.
            val arrow = LigayaTheme.colors.onCocoa
            Canvas(Modifier.size(width = 18.dp, height = 14.dp)) {
                val stroke = 1.6.dp.toPx()
                val midY = size.height / 2f
                drawLine(arrow, Offset(0f, midY), Offset(size.width, midY), stroke, StrokeCap.Round)
                drawLine(arrow, Offset(size.width, midY), Offset(size.width * 0.58f, 0f), stroke, StrokeCap.Round)
                drawLine(arrow, Offset(size.width, midY), Offset(size.width * 0.58f, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}

/** The two soft blush waves that fill the bottom third behind the button. */
@Composable
private fun BlushWaves(modifier: Modifier = Modifier) {
    // Read here, in composable scope: the draw lambda below is a DrawScope and cannot read the theme.
    val waveLight = LigayaTheme.colors.waveLight
    val waveDeep = LigayaTheme.colors.waveDeep

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val back = Path().apply {
            moveTo(0f, h * 0.79f)
            cubicTo(w * 0.32f, h * 0.73f, w * 0.60f, h * 0.665f, w, h * 0.675f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(back, Brush.verticalGradient(listOf(waveLight, waveDeep), startY = h * 0.66f, endY = h))
        val front = Path().apply {
            moveTo(0f, h * 0.745f)
            cubicTo(w * 0.22f, h * 0.80f, w * 0.52f, h * 0.835f, w, h * 0.88f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(front, Brush.verticalGradient(listOf(waveDeep.copy(alpha = 0.45f), waveDeep), startY = h * 0.75f, endY = h))
    }
}

/** Fades an element in while it rises the last few dp into place. */
private fun Modifier.rise(progress: Float): Modifier = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 10.dp.toPx()
}

/** [master] mapped into one element's own [start]..[end] slice of the timeline, eased. */
private fun slice(master: Float, start: Float, end: Float): Float =
    LigayaMotion.easingEntrance.transform(((master - start) / (end - start)).coerceIn(0f, 1f))

private val LOGO_SIZE = 92.dp
private val BUTTON_HEIGHT = 62.dp
private val WORDMARK_LIFT = 16.dp
/** Where the logo's centre sits, as a fraction of screen height (measured from the mockup). */
private const val LOGO_CENTRE_FRACTION = 0.335f
private const val BUTTON_BOTTOM_FRACTION = 0.038f
private const val BUTTON_WIDTH_FRACTION = 0.75f
private const val ENTRANCE_MILLIS = 1100
