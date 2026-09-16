package com.ligaya.feature.companion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaEmotion
import com.ligaya.designsystem.components.LigayaFrame
import com.ligaya.designsystem.components.LigayaMascot
import com.ligaya.designsystem.components.rememberLigayaMascotController
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Visual design, screen 7: Speaking — Ligaya reading her reply back.
 *
 * [aloud] is the honest half. The phone's voice engine reports whether it actually said anything (SpeechResult),
 * and on a device with no voice pack or a dead engine it says nothing at all. Section 23 forbids claiming an
 * action succeeded when it didn't, so when nothing is audible this screen drops the "Speaking..." wording and the
 * waveform entirely and shows her answer as text instead — the user still gets the reply, just not by ear.
 */
@Composable
fun VoiceSpeakingScreen(
    reply: String,
    aloud: Boolean,
    modifier: Modifier = Modifier,
) {
    val ligaya = rememberLigayaMascotController()
    LaunchedEffect(aloud) {
        ligaya.setEmotion(LigayaEmotion.Content)
        if (aloud) ligaya.startSpeaking() else ligaya.stopSpeaking()
    }

    val title = if (aloud) "Speaking..." else "I can't speak out loud right now"
    val subtitle = if (aloud) {
        "Here's what I found for you."
    } else {
        "This phone has no voice for me, so here's my answer."
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(LigayaTheme.colors.cream)) {
        val unit = minOf(maxWidth, maxHeight * 0.58f)
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 6.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LigayaLogo(modifier = Modifier.size(30.dp))
                Text("Ligaya", style = LigayaTypography.homeBrand, color = LigayaTheme.colors.cocoaInk, modifier = Modifier.padding(start = 10.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(top = 40.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = LigayaTypography.voiceTitle, color = LigayaTheme.colors.cocoaInk, textAlign = TextAlign.Center)
                Text(
                    subtitle,
                    style = LigayaTypography.voiceSubtitle,
                    color = LigayaTheme.colors.taupe,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Spacer(Modifier.weight(0.3f))

            // Her stage gives up room when the words have to be read instead of heard: the honest state carries a
            // two-line title and the reply card, and she should not crowd either.
            Box(modifier = Modifier.fillMaxWidth().weight(if (aloud) 1f else 0.72f)) {
                LigayaMascot(controller = ligaya, frame = LigayaFrame.Bust, modifier = Modifier.fillMaxSize())
                // Her artwork ends on a straight edge at the bottom of its canvas; wash it into the page.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.3f)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, LigayaTheme.colors.cream))),
                )
            }

            if (aloud) {
                // Decorative, unlike the Listening screen's bars: Android's TTS reports no amplitude, so there is
                // nothing real to plot. It reads as "a voice is playing", which is true while this state is shown.
                SpeechWaveform(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .size(width = unit * 0.36f, height = unit * 0.12f),
                )
            } else {
                Text(
                    text = reply,
                    style = LigayaTypography.bubble,
                    color = LigayaTheme.colors.cocoaInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(LigayaTheme.colors.shell)
                        .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }

            Spacer(Modifier.height(if (aloud) 28.dp else 36.dp))
        }
    }
}

/** The design's waveform: fifteen thin rounded bars, tallest in the middle, swaying while she talks. */
private val SPEECH_BARS = floatArrayOf(
    0.10f, 0.22f, 0.45f, 0.80f, 0.34f, 0.60f, 0.74f, 1f, 0.44f, 0.30f, 0.30f, 0.55f, 0.32f, 0.16f, 0.10f,
)

@Composable
private fun SpeechWaveform(modifier: Modifier = Modifier) {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val transition = rememberInfiniteTransition(label = "speech")
    val time by transition.animateFloat(
        0f,
        (2 * PI).toFloat(),
        infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "speechTime",
    )
    // Read here, in composable scope: the draw lambda below is a DrawScope and cannot read the theme.
    val barColor = LigayaTheme.colors.waveBar
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val gap = size.width / SPEECH_BARS.size
        val stroke = 2.4.dp.toPx()
        val midY = size.height / 2f
        SPEECH_BARS.forEachIndexed { i, shape ->
            val sway = if (reduceMotion) 1f else 0.72f + 0.28f * abs(sin(time + i * 0.7f))
            val h = shape * sway * size.height
            val x = gap * (i + 0.5f)
            drawLine(
                color = barColor,
                start = Offset(x, midY - h / 2f),
                end = Offset(x, midY + h / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
