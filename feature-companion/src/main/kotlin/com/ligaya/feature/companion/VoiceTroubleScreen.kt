package com.ligaya.feature.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaEmotion
import com.ligaya.designsystem.components.LigayaFrame
import com.ligaya.designsystem.components.LigayaMascot
import com.ligaya.designsystem.components.rememberLigayaMascotController

/**
 * Why a turn couldn't be answered. Each value is something the app has actually established — section 21 gives
 * "Internet unavailable" and a Gemini outage separate rows, and section 23 forbids stating a cause that was never
 * confirmed, so there is deliberately no "unknown error" that guesses at the network.
 */
enum class TroubleReason {
    /** The device reports no validated internet connection (NetworkStatus). */
    Offline,

    /** There is a connection, but her assistant couldn't be reached or isn't configured. */
    AssistantUnavailable,

    /** A reply came back but made a claim nothing had confirmed, so none of it was safe to say. */
    ReplyBlocked,
}

/**
 * Visual design, screen 8: "Oops..." — a turn that couldn't be answered, with the reason named rather than guessed.
 *
 * Whatever failed here, the emergency path is untouched (section 9: SOS never depends on Gemini), which is why the
 * card says so instead of implying the app is broken.
 */
@Composable
fun VoiceTroubleScreen(
    reason: TroubleReason,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ligaya = rememberLigayaMascotController()
    LaunchedEffect(Unit) { ligaya.setEmotion(LigayaEmotion.Concerned) }

    val (cardTitle, cardDetail) = when (reason) {
        TroubleReason.Offline -> "No internet connection" to "Check your connection and try again."
        TroubleReason.AssistantUnavailable -> "I couldn't reach my assistant" to "It's not answering right now. SOS and emergency calling still work."
        TroubleReason.ReplyBlocked -> "I couldn't answer that safely" to "My reply claimed something I can't confirm, so I didn't say it. Try asking another way."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaColors.cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
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
                .padding(top = 34.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Oops...", style = LigayaTypography.voiceTitle, color = LigayaColors.cocoaInk, textAlign = TextAlign.Center)
            Text(
                "I'm having trouble with that right now.\nLet's try something else.",
                style = LigayaTypography.voiceSubtitle,
                color = LigayaColors.taupe,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LigayaMascot(controller = ligaya, frame = LigayaFrame.Bust, modifier = Modifier.fillMaxSize())
            // Her artwork ends on a straight edge at the bottom of its canvas; wash it into the page above the card.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.34f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, LigayaColors.cream))),
            )
        }

        TroubleCard(title = cardTitle, detail = cardDetail, onTryAgain = onTryAgain)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TroubleCard(title: String, detail: String, onTryAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(LigayaColors.shell)
            .border(1.dp, LigayaColors.shellEdge, RoundedCornerShape(26.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(LigayaColors.troubleDisc),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LigayaIcons.trouble, contentDescription = null, tint = LigayaColors.onBerry, modifier = Modifier.size(21.dp))
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(title, style = LigayaTypography.chatTitle, color = LigayaColors.cocoaInk)
                Text(detail, style = LigayaTypography.chatStatus, color = LigayaColors.taupe, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(LigayaColors.berry)
                .clickable(role = Role.Button, onClick = onTryAgain),
            contentAlignment = Alignment.Center,
        ) {
            Text("Try Again", style = LigayaTypography.pillLabel, color = LigayaColors.onBerry)
        }
    }
}
