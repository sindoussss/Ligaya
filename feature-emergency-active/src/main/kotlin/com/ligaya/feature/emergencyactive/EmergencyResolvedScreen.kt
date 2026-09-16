package com.ligaya.feature.emergencyactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.designsystem.ligayaButtonElevation
import com.ligaya.designsystem.ligayaElevation
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaEmotion
import com.ligaya.designsystem.components.LigayaFrame
import com.ligaya.designsystem.components.LigayaMascot
import com.ligaya.designsystem.components.LigayaTab
import com.ligaya.designsystem.components.LigayaTabBar
import com.ligaya.designsystem.components.rememberLigayaMascotController

/**
 * Visual design, screen 6: "That's great!" — shown once the engine has actually accepted "I'm safe", so the screen
 * itself is only ever reached on a confirmed transition, never optimistically.
 *
 * The design's card reads "Task completed / Your request has been processed." That generic claim is exactly what
 * sections 20 and 23 forbid: this app may only state what the system has confirmed, and "the user marked themselves
 * safe" is never "help arrived". So the card says what is true — the user marked themselves safe — and each
 * subsystem gets its own line only when the engine has confirmed it (911 hand-off completed, Safety Circle delivery
 * confirmed). A subsystem that failed is stated too rather than hidden behind the celebration; one still pending is
 * simply not mentioned, since an unfinished action must never be implied to have succeeded.
 */
@Composable
fun EmergencyResolvedScreen(
    emergencyController: EmergencyController,
    onSelectTab: (LigayaTab) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snapshot by emergencyController.observeSnapshot().collectAsState(initial = null)
    val ligaya = rememberLigayaMascotController()
    LaunchedEffect(Unit) { ligaya.setEmotion(LigayaEmotion.Happy) }

    val subsystems = snapshot?.subsystems
    val outcomes = buildList {
        // Section 20's wording, verbatim in meaning: "I'm safe" means the user marked themselves safe, nothing more.
        add(
            Outcome(
                title = "You marked yourself safe",
                detail = when (snapshot?.state) {
                    EmergencyState.USER_MARKED_SAFE, EmergencyState.EMERGENCY_RESOLVED -> "Emergency resolved by you."
                    else -> "Emergency resolved by you."
                },
                confirmed = true,
            ),
        )
        when (subsystems?.unified911) {
            Unified911FlowState.Succeeded -> add(Outcome("911 call placed", "Your phone completed the call to 911.", true))
            Unified911FlowState.CallFailed -> add(Outcome("The 911 call didn't go through", "Nobody was contacted for you.", false))
            else -> Unit
        }
        when (subsystems?.familyAlert) {
            FamilyAlertFlowState.Succeeded -> add(Outcome("Your Safety Circle was notified", "Delivery was confirmed.", true))
            FamilyAlertFlowState.DeliveryFailed -> add(Outcome("Your Safety Circle wasn't notified", "The alert didn't reach them.", false))
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 6.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LigayaLogo(modifier = Modifier.size(30.dp))
            Text("Ligaya", style = LigayaTypography.homeBrand, color = LigayaTheme.colors.cocoaInk, modifier = Modifier.padding(start = 10.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("That's great!", style = LigayaTypography.voiceTitle, color = LigayaTheme.colors.cocoaInk, textAlign = TextAlign.Center)
            Text(
                "I'm happy I could help!",
                style = LigayaTypography.voiceSubtitle,
                color = LigayaTheme.colors.taupe,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LigayaMascot(controller = ligaya, frame = LigayaFrame.Bust, modifier = Modifier.fillMaxSize())
            Sparkles(modifier = Modifier.fillMaxSize())
            // Her artwork ends on a straight edge; this washes it into the page above the cards, as the design does.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.34f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, LigayaTheme.colors.cream))),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            outcomes.forEach { OutcomeCard(it) }
        }

        LigayaTabBar(selected = LigayaTab.Home, onSelect = onSelectTab, modifier = Modifier.padding(top = 14.dp))
    }
}

/** One thing the system actually knows, stated plainly. [confirmed] false is a failure, shown rather than hidden. */
private class Outcome(val title: String, val detail: String, val confirmed: Boolean)

@Composable
private fun OutcomeCard(outcome: Outcome) {
    val disc = if (outcome.confirmed) LigayaTheme.colors.confirmDisc else LigayaTheme.colors.colorStatusFailed.copy(alpha = 0.14f)
    val glyph: ImageVector = if (outcome.confirmed) LigayaIcons.confirmed else LigayaIcons.failed
    val glyphTint = if (outcome.confirmed) Color.White else LigayaTheme.colors.colorStatusFailed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ligayaElevation(shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${outcome.title}. ${outcome.detail}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
            Icon(glyph, contentDescription = null, tint = glyphTint, modifier = Modifier.size(21.dp))
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(outcome.title, style = LigayaTypography.bubble.copy(fontWeight = FontWeight.SemiBold), color = LigayaTheme.colors.cocoaInk)
            Text(outcome.detail, style = LigayaTypography.chatStatus, color = LigayaTheme.colors.taupe, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Where each sparkle sits and how big it is, as fractions of the stage: x, y, radius (of the stage's width). */
private class Sparkle(val x: Float, val y: Float, val radius: Float)

private val SPARKLES = listOf(
    Sparkle(0.17f, 0.20f, 0.040f),
    Sparkle(0.147f, 0.35f, 0.022f),
    Sparkle(0.835f, 0.17f, 0.028f),
    Sparkle(0.848f, 0.29f, 0.020f),
    Sparkle(0.885f, 0.52f, 0.032f),
)

/** The design's soft four-point stars around her. Ornament only, and still — nothing here implies activity. */
@Composable
private fun Sparkles(modifier: Modifier = Modifier) {
    // Read here, in composable scope: the draw lambda below is a DrawScope and cannot read the theme.
    val sparkleColor = LigayaTheme.colors.sparkle

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val width = size.width
        SPARKLES.forEach { sparkle ->
            val centre = Offset(sparkle.x * width, sparkle.y * size.height)
            val r = sparkle.radius * width
            // A four-point star: each point pulled to a waist a third of the way in, the way the design draws them.
            val waist = r * 0.3f
            val path = Path().apply {
                moveTo(centre.x, centre.y - r)
                quadraticBezierTo(centre.x + waist * 0.4f, centre.y - waist * 0.4f, centre.x + r, centre.y)
                quadraticBezierTo(centre.x + waist * 0.4f, centre.y + waist * 0.4f, centre.x, centre.y + r)
                quadraticBezierTo(centre.x - waist * 0.4f, centre.y + waist * 0.4f, centre.x - r, centre.y)
                quadraticBezierTo(centre.x - waist * 0.4f, centre.y - waist * 0.4f, centre.x, centre.y - r)
                close()
            }
            drawPath(path, sparkleColor)
        }
    }
}
