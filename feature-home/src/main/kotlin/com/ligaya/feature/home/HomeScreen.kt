package com.ligaya.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.ligayaButtonElevation
import com.ligaya.designsystem.ligayaElevation
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaEmotion
import com.ligaya.designsystem.components.LigayaFrame
import com.ligaya.designsystem.components.LigayaMascot
import com.ligaya.designsystem.components.LigayaMascotController
import com.ligaya.designsystem.components.LigayaTab
import com.ligaya.designsystem.components.LigayaTabBar
import com.ligaya.designsystem.components.VoiceStateIndicator
import com.ligaya.designsystem.components.rememberLigayaMascotController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Visual design, screen 2: Home. The brand header, a greeting, Ligaya herself, the "Ask me anything"
 * bar with Voice/Text shortcuts, and the Home / Chat / Circle / Profile tab bar.
 *
 * How it keeps section 4's requirements inside that design:
 *  - SOS is always on screen as the red pill in the header — one tap straight into
 *    [EmergencyController.triggerSos], never behind Gemini (section 9) and never below the fold.
 *  - Hands-free voice (section 5) needs no tap here: the wake-word loop runs underneath, and its live
 *    phase shows as a caption over Ligaya (and in her face) whenever it isn't idle. The mic and Voice
 *    chip are an extra way in, not a requirement.
 *  - When voice AI is unavailable the page says so in words and points at SOS (section 21).
 *  - AI assistance (the ask bar, Chat) sits at the bottom, apart from the SOS control at the top.
 *  - Safety Circle and every other destination are in the header menu, so each stays reachable.
 */
@Composable
fun HomeScreen(
    emergencyController: EmergencyController,
    otherDestinations: List<NavigableDestination>,
    onSosActivated: () -> Unit,
    onNavigateToSafetyCircle: () -> Unit,
    onNavigateToCompanion: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    voicePhase: StateFlow<VoicePipelinePhase>,
    voiceAiUnavailable: StateFlow<Boolean>,
    modifier: Modifier = Modifier,
    userName: String? = null,
    onAskText: (String) -> Unit = {},
    onStartVoice: () -> Unit = {},
    onNavigateToSafetyCircleTab: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    /** Whether the app is currently painted dark, so the header's button shows the theme in force. */
    isDarkTheme: Boolean = false,
    /** The hour the greeting is chosen for. A parameter only so tests can hold it still. */
    greetingAt: LocalTime = LocalTime.now(),
) {
    // Screens 2 and 11: the same Home, greeting by the phone's own clock — good day, or good evening.
    val greeting = remember(greetingAt) { homeGreetingFor(greetingAt) }
    val phase by voicePhase.collectAsState()
    val aiUnavailable by voiceAiUnavailable.collectAsState()
    val ligaya = rememberLigayaMascotController()

    LaunchedEffect(phase, aiUnavailable) { ligaya.reflectVoice(phase, aiUnavailable) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LigayaTheme.colors.cream, LigayaTheme.colors.creamDeep))),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HomeHeader(
                emergencyController = emergencyController,
                otherDestinations = otherDestinations,
                onSosActivated = onSosActivated,
                onNavigateToSafetyCircle = onNavigateToSafetyCircle,
                onNavigateToRoute = onNavigateToRoute,
                onToggleTheme = onToggleTheme,
                isDarkTheme = isDarkTheme,
            )

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = LigayaSpacing.lg).padding(top = 26.dp)) {
                Text(
                    text = greeting.salutation,
                    style = LigayaTypography.homeGreeting,
                    color = LigayaTheme.colors.cocoaInk,
                )
                Text(
                    text = "${userName?.trim()?.takeIf { it.isNotEmpty() } ?: "kaibigan"}!",
                    style = LigayaTypography.homeName,
                    color = LigayaTheme.colors.cocoaInk,
                )
                Text(
                    text = greeting.intro,
                    style = LigayaTypography.homeIntro,
                    color = LigayaTheme.colors.taupe,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 222.dp),
                )
                if (aiUnavailable) {
                    Text(
                        text = "Voice assistant unavailable right now — use the SOS button instead.",
                        style = LigayaTypography.chipLabel,
                        color = LigayaTheme.colors.cocoaInk,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LigayaTheme.colors.colorStatusPending.copy(alpha = 0.22f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LigayaMascot(
                    controller = ligaya,
                    frame = LigayaFrame.Bust,
                    modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 28.dp, top = 12.dp),
                )
                // Her artwork ends on a straight edge at the bottom of its canvas; this washes it into the page
                // above the ask bar, so she fades out rather than stopping on a line.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.3f)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, LigayaTheme.colors.cream))),
                )
                Doodles(Modifier.fillMaxSize())
                if (phase != VoicePipelinePhase.IDLE) {
                    VoiceCaption(phase, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
                }
            }

            AskBar(onAskText = onAskText, onStartVoice = onStartVoice)
            LigayaTabBar(
                selected = LigayaTab.Home,
                onSelect = { tab ->
                    when (tab) {
                        LigayaTab.Chat -> onNavigateToCompanion()
                        LigayaTab.Circle -> onNavigateToSafetyCircleTab()
                        LigayaTab.Profile -> onNavigateToProfile()
                        LigayaTab.Home -> Unit
                    }
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun HomeHeader(
    emergencyController: EmergencyController,
    otherDestinations: List<NavigableDestination>,
    onSosActivated: () -> Unit,
    onNavigateToSafetyCircle: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onToggleTheme: () -> Unit,
    isDarkTheme: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = LigayaSpacing.lg, end = LigayaSpacing.sm, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LigayaLogo(modifier = Modifier.size(34.dp))
        Text(
            text = "Ligaya",
            style = LigayaTypography.homeBrand,
            color = LigayaTheme.colors.cocoaInk,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        SosPill(emergencyController = emergencyController, onSosActivated = onSosActivated)
        // The icon shows the theme in force (a sun by day, a moon at night, as the reference draws it); the
        // description says what tapping it does, which is what a screen reader needs.
        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (isDarkTheme) LigayaIcons.darkMode else LigayaIcons.lightMode,
                contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                tint = LigayaTheme.colors.cocoa,
                modifier = Modifier.size(28.dp),
            )
        }
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(LigayaIcons.menu, contentDescription = "Menu", tint = LigayaTheme.colors.cocoa, modifier = Modifier.size(28.dp))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = LigayaTheme.colors.shell,
            ) {
                DropdownMenuItem(
                    text = { Text("Safety Circle", style = LigayaTypography.chipLabel) },
                    onClick = {
                        menuOpen = false
                        onNavigateToSafetyCircle()
                    },
                )
                otherDestinations.forEach { destination ->
                    DropdownMenuItem(
                        text = { Text(destination.title, style = LigayaTypography.chipLabel) },
                        onClick = {
                            menuOpen = false
                            onNavigateToRoute(destination.route)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The always-visible SOS control. The pill is drawn compact to fit the design's header, but its touch
 * target is the full 48dp floor, and one tap goes straight to [EmergencyController.triggerSos].
 */
@Composable
private fun SosPill(emergencyController: EmergencyController, onSosActivated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var activating by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .heightIn(min = LigayaSpacing.minTouchTarget)
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = !activating, role = Role.Button) {
                activating = true
                scope.launch {
                    try {
                        when (emergencyController.triggerSos()) {
                            is SosResult.Activated, is SosResult.AlreadyInProgress -> onSosActivated()
                        }
                    } finally {
                        activating = false
                    }
                }
            }
            .semantics { contentDescription = if (activating) "Activating emergency alert" else "Send SOS emergency alert" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .ligayaButtonElevation(shape = CircleShape)
                .clip(CircleShape)
                .background(LigayaTheme.colors.colorEmergencyActive)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (activating) {
                CircularProgressIndicator(color = LigayaTheme.colors.onEmergencyActive, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            } else {
                Icon(LigayaIcons.emergency, contentDescription = null, tint = LigayaTheme.colors.onEmergencyActive, modifier = Modifier.size(16.dp))
            }
            Text(text = "SOS", style = LigayaTypography.chipLabel, color = LigayaTheme.colors.onEmergencyActive)
        }
    }
}

/** The ask bar plus its Voice / Text shortcuts. */
@Composable
private fun AskBar(onAskText: (String) -> Unit, onStartVoice: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        val question = text.trim()
        if (question.isEmpty()) return
        text = ""
        keyboard?.hide()
        onAskText(question)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = LigayaSpacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(30.dp), ambientColor = LigayaTheme.colors.cocoa.copy(alpha = 0.25f), spotColor = LigayaTheme.colors.cocoa.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(30.dp))
                .background(LigayaTheme.colors.shell)
                .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(30.dp))
                .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = LigayaTypography.askField.copy(color = LigayaTheme.colors.cocoaInk),
                cursorBrush = SolidColor(LigayaTheme.colors.berry),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text("Ask me anything...", style = LigayaTypography.askField, color = LigayaTheme.colors.taupe)
                        }
                        field()
                    }
                },
            )
            val typing = text.isNotBlank()
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(LigayaTheme.colors.berry)
                    .clickable(role = Role.Button) { if (typing) submit() else onStartVoice() }
                    .semantics { contentDescription = if (typing) "Send to Ligaya" else "Talk to Ligaya" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (typing) LigayaIcons.send else LigayaIcons.mic, contentDescription = null, tint = LigayaTheme.colors.onBerry)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShortcutChip(LigayaIcons.voiceWave, "Voice", Modifier.weight(1f), onStartVoice)
            ShortcutChip(LigayaIcons.textChat, "Text", Modifier.weight(1f)) {
                focus.requestFocus()
                keyboard?.show()
            }
        }
    }
}

@Composable
private fun ShortcutChip(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(LigayaSpacing.minTouchTarget)
            .ligayaElevation(elevation = 4.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = LigayaTheme.colors.accentInk, modifier = Modifier.size(20.dp))
        Text(label, style = LigayaTypography.chipLabel, color = LigayaTheme.colors.cocoaInk, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The live voice phase, shown over Ligaya only while the voice loop is doing something. */
@Composable
private fun VoiceCaption(phase: VoicePipelinePhase, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(LigayaTheme.colors.shell.copy(alpha = 0.92f))
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceStateIndicator(state = phase.toLigayaVoiceState())
        Text(text = voiceStateLabel(phase), style = LigayaTypography.chipLabel, color = LigayaTheme.colors.cocoaInk)
    }
}

/** The little hand-drawn marks around Ligaya: a burst of strokes on her left, a heart on her right. */
@Composable
private fun Doodles(modifier: Modifier = Modifier) {
    // Read here, in composable scope: the draw lambda below is a DrawScope and cannot read the theme.
    val doodle = LigayaTheme.colors.doodle

    Canvas(modifier.clearAndSetSemantics { }) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        // Three short strokes fanning out, left of her face.
        val burst = Offset(w * 0.17f, h * 0.44f)
        listOf(Offset(-0.05f, -0.05f), Offset(-0.06f, 0.02f)).forEach { d ->
            drawLine(
                color = doodle,
                start = Offset(burst.x + d.x * w * 0.4f, burst.y + d.y * h * 0.4f),
                end = Offset(burst.x + d.x * w, burst.y + d.y * h),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // A small outlined heart with a flick above it, right of her face.
        val cx = w * 0.855f
        val cy = h * 0.34f
        val s = 11.dp.toPx()
        val heart = Path().apply {
            moveTo(cx, cy + s * 0.9f)
            cubicTo(cx - s * 1.3f, cy, cx - s * 0.6f, cy - s * 0.9f, cx, cy - s * 0.25f)
            cubicTo(cx + s * 0.6f, cy - s * 0.9f, cx + s * 1.3f, cy, cx, cy + s * 0.9f)
        }
        drawPath(heart, doodle, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(doodle, Offset(cx + s * 0.4f, cy - s * 1.4f), Offset(cx + s * 0.2f, cy - s * 2.6f), stroke, StrokeCap.Round)
    }
}

/**
 * Ligaya's face follows the always-on voice loop's real phase: a warm smile at rest, attentive while it
 * listens, thinking while Gemini interprets, her mouth moving while she speaks. When voice AI is
 * unavailable she looks reassuring rather than alarmed — the notice above says what to do.
 */
private fun LigayaMascotController.reflectVoice(phase: VoicePipelinePhase, aiUnavailable: Boolean) {
    if (aiUnavailable) {
        stopListening()
        stopSpeaking()
        setEmotion(LigayaEmotion.Reassuring)
        return
    }
    when (phase) {
        VoicePipelinePhase.IDLE -> {
            stopListening()
            stopSpeaking()
            setEmotion(LigayaEmotion.Content)
        }
        VoicePipelinePhase.LISTENING -> {
            // The wake-word loop listens nearly all the time on Home, so this is her resting look too.
            setEmotion(LigayaEmotion.Content)
            startListening()
        }
        VoicePipelinePhase.PROCESSING -> {
            stopListening()
            stopSpeaking()
            setEmotion(LigayaEmotion.Thinking)
        }
        VoicePipelinePhase.SPEAKING -> {
            setEmotion(LigayaEmotion.Neutral)
            startSpeaking()
        }
    }
}

/** A route/title pair — this module's own minimal stand-in for :app's LigayaDestination, so this
 *  module never depends on :app (see [HomeScreen]'s own doc comment). */
data class NavigableDestination(val route: String, val title: String)

/** The caption beside the voice indicator, phrased for Home's ambient context: the loop is always on in
 *  the background, waiting for her name. */
private fun voiceStateLabel(phase: VoicePipelinePhase): String = when (phase) {
    VoicePipelinePhase.IDLE -> "Voice assistant idle"
    VoicePipelinePhase.LISTENING -> "Listening for \"Ligaya\"…"
    VoicePipelinePhase.PROCESSING -> "Understanding what you said…"
    VoicePipelinePhase.SPEAKING -> "Ligaya is speaking…"
}
