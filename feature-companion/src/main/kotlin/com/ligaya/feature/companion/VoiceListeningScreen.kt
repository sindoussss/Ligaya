package com.ligaya.feature.companion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaIcons
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

/** What the Listening screen is showing. */
enum class ListeningState {
    /** The microphone is open and Ligaya is listening. */
    Listening,

    /** Not listening; tap the mic to start. */
    Paused,

    /** A listening session ended without hearing anything usable. */
    NotHeard,

    /** Microphone permission is off, so she can't listen at all. */
    MicBlocked,
}

/**
 * Visual design, screen 4: Listening. Ligaya in her disc, sound bars either side, and the mic button.
 *
 * Honest by construction: the bars are drawn from [inputLevel], the recognizer's real microphone level, so they only
 * move when there is sound; the title always says what's actually happening, including when she didn't hear
 * anything or can't use the microphone (section 21), and it's a polite live region so TalkBack announces changes.
 */
@Composable
fun VoiceListeningScreen(
    state: ListeningState,
    inputLevel: Float,
    onMicTap: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ligaya = rememberLigayaMascotController()
    LaunchedEffect(state) {
        when (state) {
            ListeningState.Listening -> {
                ligaya.setEmotion(LigayaEmotion.Content)
                ligaya.startListening()
            }
            ListeningState.Paused -> {
                ligaya.stopListening()
                ligaya.setEmotion(LigayaEmotion.Content)
            }
            ListeningState.NotHeard, ListeningState.MicBlocked -> {
                ligaya.stopListening()
                ligaya.setEmotion(LigayaEmotion.Reassuring)
            }
        }
    }
    val listening = state == ListeningState.Listening
    val level by animateFloatAsState(if (listening) inputLevel else 0f, tween(140), label = "micLevel")

    val (title, subtitle) = when (state) {
        ListeningState.Listening -> "Listening..." to "I'm here. Just say what you need."
        ListeningState.Paused -> "Tap to talk" to "I'm here whenever you're ready."
        ListeningState.NotHeard -> "I didn't catch that" to "Tap the mic and try again, or type instead."
        ListeningState.MicBlocked -> "Microphone is off" to "Allow microphone access in Settings to talk to Ligaya."
    }
    val micDescription = when (state) {
        ListeningState.Listening -> "Stop listening"
        ListeningState.MicBlocked -> "Open settings to allow the microphone"
        else -> "Start listening"
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(LigayaTheme.colors.cream)) {
        val disc = minOf(maxWidth * 0.96f, maxHeight * 0.56f)
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LigayaLogo(modifier = Modifier.size(30.dp))
                Text("Ligaya", style = LigayaTypography.homeBrand, color = LigayaTheme.colors.cocoaInk, modifier = Modifier.padding(start = 10.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(LigayaIcons.close, contentDescription = "Close", tint = LigayaTheme.colors.cocoa, modifier = Modifier.size(26.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(top = 42.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = LigayaTypography.voiceTitle, color = LigayaTheme.colors.cocoaInk, textAlign = TextAlign.Center)
                Text(
                    subtitle,
                    style = LigayaTypography.voiceSubtitle,
                    color = LigayaTheme.colors.taupe,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }

            // Spare height splits about evenly above the disc and below the mic, as in the design.
            Spacer(Modifier.weight(0.55f))

            // The mic sits mostly below the disc, its ring just overlapping the disc's bottom edge.
            Box(modifier = Modifier.fillMaxWidth().height(disc + 80.dp)) {
                // Ligaya's disc.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(disc)
                        .shadow(14.dp, CircleShape, ambientColor = LigayaTheme.colors.cocoa.copy(alpha = 0.12f), spotColor = LigayaTheme.colors.cocoa.copy(alpha = 0.12f))
                        .clip(CircleShape)
                        .background(LigayaTheme.colors.listeningDisc)
                        .border(1.dp, LigayaTheme.colors.shellEdge, CircleShape),
                ) {
                    // Head, shoulders and the top of her cardigan, with the disc's edge cutting across her body. Her
                    // view is taller than the disc so the artwork's own bottom fade falls outside it: the cardigan stays
                    // solid right up to the disc's edge.
                    LigayaMascot(
                        controller = ligaya,
                        frame = LigayaFrame.Bust,
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().requiredHeight(disc * 1.18f).offset(y = disc * 0.09f),
                    )
                }
                // Sound bars beside her face.
                Row(
                    // Centred on her eye line.
                    modifier = Modifier.fillMaxWidth().offset(y = disc * 0.42f - 35.dp).padding(horizontal = 6.dp),
                ) {
                    SoundBars(level = level, active = listening, count = 7, barHeight = 70.dp, mirrored = true)
                    Spacer(Modifier.weight(1f))
                    SoundBars(level = level, active = listening, count = 7, barHeight = 70.dp, mirrored = false)
                }
                // The mic, overlapping the bottom of the disc, with smaller bars either side.
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoundBars(level = level, active = listening, count = 8, barHeight = 42.dp, mirrored = true)
                    MicButton(description = micDescription, onClick = onMicTap, modifier = Modifier.padding(horizontal = 14.dp))
                    SoundBars(level = level, active = listening, count = 8, barHeight = 42.dp, mirrored = false)
                }
            }

            Spacer(Modifier.weight(0.45f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MicButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(104.dp)
            .shadow(10.dp, CircleShape, ambientColor = LigayaTheme.colors.cocoa.copy(alpha = 0.18f), spotColor = LigayaTheme.colors.cocoa.copy(alpha = 0.18f))
            .clip(CircleShape)
            .background(LigayaTheme.colors.shell)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(82.dp).clip(CircleShape).background(LigayaTheme.colors.berry),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LigayaIcons.mic, contentDescription = null, tint = LigayaTheme.colors.onBerry, modifier = Modifier.size(36.dp))
        }
    }
}

/** Relative bar heights, nearest Ligaya first: the uneven waveform shape the design draws. */
private val BAR_SHAPE = floatArrayOf(0.5f, 1f, 0.62f, 0.9f, 0.42f, 0.72f, 0.34f, 0.5f)

/**
 * A row of thin rounded bars in the design's waveform shape ([mirrored] puts the Ligaya end on the right). In silence
 * they rest, perfectly still, at a low height; they only grow and sway with real sound from [level], so any movement
 * means the microphone is actually hearing something. Dimmer when not listening; no sway with reduced motion.
 */
@Composable
private fun SoundBars(level: Float, active: Boolean, count: Int, barHeight: Dp, mirrored: Boolean) {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val transition = rememberInfiniteTransition(label = "bars")
    val time by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "barsTime")
    val sway = if (active && !reduceMotion) time else 0f
    // Read here, in composable scope: the draw lambda below is a DrawScope and cannot read the theme.
    val barColor = LigayaTheme.colors.waveBar
    Canvas(Modifier.width(barHeight * 0.16f * count).height(barHeight).clearAndSetSemantics { }) {
        val gap = size.width / count
        val stroke = 1.8.dp.toPx()
        val midY = size.height / 2f
        val rest = if (active) 0.4f else 0.26f
        val color = if (active) barColor else barColor.copy(alpha = 0.6f)
        for (i in 0 until count) {
            // Position 0 is nearest Ligaya.
            val fromCentre = if (mirrored) count - 1 - i else i
            val shape = BAR_SHAPE[fromCentre % BAR_SHAPE.size]
            val wobble = 0.7f + 0.3f * abs(sin(sway + fromCentre * 1.3f))
            val h = (rest + (1f - rest) * level * wobble) * shape * size.height
            val x = gap * (i + 0.5f)
            drawLine(
                color = color,
                start = Offset(x, midY - h / 2f),
                end = Offset(x, midY + h / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
