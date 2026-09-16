package com.ligaya.feature.companion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaEmotion
import com.ligaya.designsystem.components.LigayaFrame
import com.ligaya.designsystem.components.LigayaMascot
import com.ligaya.designsystem.components.rememberLigayaMascotController
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** How long Ligaya thinks before the screen acknowledges the wait. */
internal const val THINKING_SLOW_AFTER_MILLIS = 8_000L

// The stage, measured in one unit (the screen width, less on a short screen) from the top of Ligaya's disc.
private const val DISC = 0.84f
private const val MASCOT_TOP = 0.016f
private const val MASCOT_WIDTH = 0.96f
private const val MASCOT_HEIGHT = 1.133f

// Her artwork stops above where the design cuts her off and fades out before that edge, so the cut sits just above
// the fade: her cardigan ends on a clean curve instead of dissolving.
private const val CUT_CENTER_Y = 0.287f
private const val CUT_RADIUS = 0.575f
private const val DOTS_Y = 1.24f

/**
 * Visual design, screen 5: Thinking. Shown while Ligaya works out her reply to what she just heard: her thinking face in
 * the disc, thought bubbles rising beside her, and loading dots.
 *
 * Honest about the wait (section 21): without smart replies the subtitle says her answer will be basic rather than
 * implying Gemini is working on it, and a long wait is acknowledged instead of looping the same line. The text is a
 * polite live region, so TalkBack announces the change.
 */
@Composable
fun VoiceThinkingScreen(
    aiAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val ligaya = rememberLigayaMascotController()
    LaunchedEffect(Unit) { ligaya.setEmotion(LigayaEmotion.Thinking) }
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(THINKING_SLOW_AFTER_MILLIS)
        slow = true
    }
    val subtitle = when {
        !aiAvailable -> "Smart replies are off, so my answer will be basic."
        slow -> "Still thinking. Thanks for waiting."
        else -> "Let me think about that for a moment."
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(LigayaColors.cream)) {
        val unit = minOf(maxWidth, maxHeight * 0.58f)
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 6.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LigayaLogo(modifier = Modifier.size(30.dp))
                Text("Ligaya", style = LigayaTypography.homeBrand, color = LigayaColors.cocoaInk, modifier = Modifier.padding(start = 10.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(top = 46.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Thinking...", style = LigayaTypography.voiceTitle, color = LigayaColors.cocoaInk, textAlign = TextAlign.Center)
                Text(
                    subtitle,
                    style = LigayaTypography.voiceSubtitle,
                    color = LigayaColors.taupe,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 23.dp),
                )
            }

            Spacer(Modifier.weight(0.29f))

            Box(modifier = Modifier.fillMaxWidth().height(unit * DOTS_Y + 10.dp)) {
                // Her soft disc, with no rim.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(unit * DISC)
                        .shadow(16.dp, CircleShape, ambientColor = LigayaColors.cocoa.copy(alpha = 0.1f), spotColor = LigayaColors.cocoa.copy(alpha = 0.1f))
                        .clip(CircleShape)
                        .background(LigayaColors.listeningDisc),
                )
                // Ligaya fills the disc and a little beyond it; only below is she cut off, on a wider curve than the disc.
                LigayaMascot(
                    controller = ligaya,
                    frame = LigayaFrame.Bust,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = unit * MASCOT_TOP)
                        .requiredWidth(unit * MASCOT_WIDTH)
                        .requiredHeight(unit * MASCOT_HEIGHT)
                        .clip(LigayaCut),
                )
                ThoughtBubbles(unit = unit, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(unit * DISC))
                ThinkingDots(modifier = Modifier.align(Alignment.TopCenter).offset(y = unit * DOTS_Y - 5.dp))
            }

            Spacer(Modifier.weight(0.71f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The curve Ligaya is cut off along, in her own view's coordinates (her view is [MASCOT_WIDTH] units wide). */
private val LigayaCut = GenericShape { size, _ ->
    val unit = size.width / MASCOT_WIDTH
    addOval(Rect(center = Offset(size.width / 2f, (CUT_CENTER_Y - MASCOT_TOP) * unit), radius = CUT_RADIUS * unit))
}

private class Bubble(val x: Float, val y: Float, val radius: Float)

/** Smallest (nearest her head) to largest, as in the design: centre x from the stage's left edge, centre y from the
 *  disc's top, and radius, in stage units. */
private val BUBBLES = listOf(
    Bubble(0.735f, 0.156f, 0.0073f),
    Bubble(0.775f, 0.115f, 0.016f),
    Bubble(0.830f, 0.066f, 0.025f),
)

/** Three thought bubbles. Each swells and brightens in turn, smallest first, like a thought rising; still with reduced motion. */
@Composable
private fun ThoughtBubbles(unit: Dp, modifier: Modifier = Modifier) {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val transition = rememberInfiniteTransition(label = "thoughtBubbles")
    val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "thoughtBubblesTime")
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val unitPx = unit.toPx()
        BUBBLES.forEachIndexed { i, bubble ->
            val pulse = if (reduceMotion) 1f else 0.5f + 0.5f * sin(2 * PI * (time - i * 0.15f)).toFloat()
            drawCircle(
                color = LigayaColors.thoughtBubble.copy(alpha = 0.6f + 0.4f * pulse),
                radius = bubble.radius * unitPx * (0.88f + 0.12f * pulse),
                center = Offset(size.width / 2f + (bubble.x - 0.5f) * unitPx, bubble.y * unitPx),
            )
        }
    }
}

/** The design's three loading dots, one light and two dark; the light spot travels smoothly along them. Still with reduced motion. */
@Composable
private fun ThinkingDots(modifier: Modifier = Modifier) {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val transition = rememberInfiniteTransition(label = "thinkingDots")
    val spot by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "thinkingDotsSpot")
    Row(modifier = modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            val light = if (reduceMotion) (if (i == 0) 1f else 0f) else (1f - ringDistance(spot, i.toFloat(), 3f)).coerceIn(0f, 1f)
            Box(Modifier.size(10.dp).clip(CircleShape).background(lerp(LigayaColors.thinkingDot, LigayaColors.thinkingDotLight, light)))
        }
    }
}

/** Distance between [a] and [b] around a loop of length [loop]. */
private fun ringDistance(a: Float, b: Float, loop: Float): Float {
    val d = abs(a - b) % loop
    return minOf(d, loop - d)
}
