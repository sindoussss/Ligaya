package com.ligaya.feature.companion

import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ligaya.core.ai.CompanionTurn
import com.ligaya.core.ai.GeminiCompanionResponseProvider
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaAvatar
import com.ligaya.designsystem.components.LigayaTab
import com.ligaya.designsystem.components.LigayaTabBar
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Visual design, screen 3: Chat with Ligaya — the Emergency Companion conversation (section 19) in the
 * mockup's messaging layout. [EmergencyCompanionCoordinator.transcript] renders as message bubbles, the header
 * status follows the live [EmergencyCompanionCoordinator.phase], and the text box is the always-available way to
 * talk without voice ([EmergencyCompanionCoordinator.runOneTurnWithText] never touches the microphone).
 *
 * What it keeps honest, per the blueprint:
 *  - "Online" only when smart replies can actually work. With no Gemini key ([aiAvailable] false), or once
 *    Gemini has fallen back to its fixed safe line, the status says "Limited replies" and a notice explains it
 *    (section 21: never imply full AI still works).
 *  - A turn that ends without a reply (blocked by the response validator, or no speech heard) says so in the
 *    conversation, rather than the chat silently stopping (section 23).
 *  - The answered tick on your message means Ligaya replied to it — nothing more.
 *  - During an emergency ([inEmergency]) the greeting is calm and on-task, and SOS is always in the menu,
 *    triggered directly by the caller, never through Gemini (section 9).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmergencyCompanionScreen(
    coordinator: EmergencyCompanionCoordinator,
    modifier: Modifier = Modifier,
    userName: String? = null,
    aiAvailable: Boolean = true,
    inEmergency: Boolean = false,
    onBack: () -> Unit = {},
    onSelectTab: (LigayaTab) -> Unit = {},
    onStartVoice: () -> Unit = {},
    onSos: (() -> Unit)? = null,
) {
    val transcript by coordinator.transcript.collectAsState()
    val times by coordinator.turnTimes.collectAsState()
    val phase by coordinator.phase.collectAsState()
    val lastResult by coordinator.lastResult.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val focusManager = LocalFocusManager.current

    val lastReply = transcript.lastOrNull { it.speaker == CompanionTurn.Speaker.LIGAYA }
    val limited = !aiAvailable || lastReply?.text == GeminiCompanionResponseProvider.SAFE_FALLBACK_RESPONSE
    val note = when {
        phase != VoicePipelinePhase.IDLE -> null
        lastResult is CompanionTurnResult.ResponseBlocked -> "Ligaya couldn't answer that safely. Try asking another way."
        lastResult is CompanionTurnResult.NoSpeechCaptured -> "Ligaya didn't catch that. Try again, or type your message."
        else -> null
    }

    // Keep the newest message in view as the conversation grows or Ligaya starts typing.
    LaunchedEffect(transcript.size, phase, note) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.cream)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        ChatHeader(phase = phase, limited = limited, onBack = onBack, onStartVoice = onStartVoice, onSos = onSos)
        if (limited) LimitedRepliesNotice()

        LazyColumn(
            state = listState,
            // Tapping the conversation puts the keyboard away, as in any messaging app.
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "greeting") { LigayaMessage(text = greeting(userName, inEmergency), time = null) }
            itemsIndexed(transcript) { index, turn ->
                val time = times.getOrNull(index)?.let { timeFormat.format(Date(it)) }
                when (turn.speaker) {
                    CompanionTurn.Speaker.LIGAYA -> LigayaMessage(text = turn.text, time = time)
                    CompanionTurn.Speaker.USER -> UserMessage(
                        text = turn.text,
                        time = time,
                        answered = transcript.drop(index + 1).firstOrNull()?.speaker == CompanionTurn.Speaker.LIGAYA,
                    )
                }
            }
            if (phase == VoicePipelinePhase.PROCESSING) item(key = "typing") { TypingIndicator() }
            if (note != null) item(key = "note") { ConversationNote(note) }
        }

        ChatInput(onSend = { text -> scope.launch { coordinator.runOneTurnWithText(text) } })

        if (WindowInsets.isImeVisible) {
            Spacer(Modifier.height(8.dp))
        } else {
            LigayaTabBar(
                selected = LigayaTab.Chat,
                onSelect = onSelectTab,
                onSos = { onSos?.invoke() },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun greeting(userName: String?, inEmergency: Boolean): String {
    if (inEmergency) return "I'm here with you.\nTell me what's happening."
    val name = userName?.trim()?.takeIf { it.isNotEmpty() } ?: "kaibigan"
    return "Hi $name! 👋\nHow can I help you today?"
}

@Composable
private fun ChatHeader(
    phase: VoicePipelinePhase,
    limited: Boolean,
    onBack: () -> Unit,
    onStartVoice: () -> Unit,
    onSos: (() -> Unit)?,
) {
    val status = when (phase) {
        VoicePipelinePhase.LISTENING -> "Listening…"
        VoicePipelinePhase.PROCESSING -> "Typing…"
        VoicePipelinePhase.SPEAKING -> "Speaking…"
        VoicePipelinePhase.IDLE -> if (limited) "Limited replies" else "Online"
    }
    val dot = if (limited && phase == VoicePipelinePhase.IDLE) LigayaTheme.colors.colorStatusPending else LigayaTheme.colors.online

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(LigayaIcons.chevronBack, contentDescription = "Back", tint = LigayaTheme.colors.cocoaInk, modifier = Modifier.size(20.dp))
        }
        LigayaAvatar(size = 46.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text("Ligaya", style = LigayaTypography.chatTitle, color = LigayaTheme.colors.cocoaInk)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Text(status, style = LigayaTypography.chatStatus, color = LigayaTheme.colors.taupe, modifier = Modifier.padding(start = 6.dp))
            }
        }
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LigayaTheme.colors.shell)
                        .border(1.dp, LigayaTheme.colors.shellEdge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LigayaIcons.moreOptions, contentDescription = "More options", tint = LigayaTheme.colors.cocoaInk)
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = LigayaTheme.colors.shell,
            ) {
                DropdownMenuItem(
                    text = { Text("Talk to Ligaya", style = LigayaTypography.chipLabel, color = LigayaTheme.colors.cocoaInk) },
                    onClick = {
                        menuOpen = false
                        onStartVoice()
                    },
                )
                if (onSos != null) {
                    DropdownMenuItem(
                        text = { Text("Emergency SOS", style = LigayaTypography.chipLabel, color = LigayaTheme.colors.colorEmergencyActive) },
                        onClick = {
                            menuOpen = false
                            onSos()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitedRepliesNotice() {
    Text(
        text = "Smart replies are off right now, so Ligaya's answers will be basic. SOS and emergency calling still work.",
        style = LigayaTypography.chatStatus,
        color = LigayaTheme.colors.cocoaInk,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LigayaTheme.colors.colorStatusPending.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private val BubbleShape = RoundedCornerShape(20.dp)

@Composable
private fun LigayaMessage(text: String, time: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Ligaya said: $text" },
        verticalAlignment = Alignment.Top,
    ) {
        LigayaAvatar(size = 40.dp)
        Column(modifier = Modifier.weight(1f, fill = false).padding(start = 10.dp)) {
            Text(
                text = text,
                style = LigayaTypography.bubble,
                color = LigayaTheme.colors.cocoaInk,
                modifier = Modifier
                    .shadow(2.dp, BubbleShape, ambientColor = LigayaTheme.colors.cocoa.copy(alpha = 0.15f), spotColor = LigayaTheme.colors.cocoa.copy(alpha = 0.15f))
                    .clip(BubbleShape)
                    .background(LigayaTheme.colors.bubbleLigaya)
                    .border(1.dp, LigayaTheme.colors.bubbleLigayaEdge, BubbleShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (time != null) {
                Text(time, style = LigayaTypography.messageTime, color = LigayaTheme.colors.taupe, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            }
        }
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun UserMessage(text: String, time: String?, answered: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Your messages wrap at about half the width, as the design draws them.
            .padding(start = 120.dp)
            .semantics(mergeDescendants = true) { contentDescription = "You said: $text" },
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = text,
                style = LigayaTypography.bubble,
                color = LigayaTheme.colors.cocoaInk,
                modifier = Modifier
                    .shadow(2.dp, BubbleShape, ambientColor = LigayaTheme.colors.cocoa.copy(alpha = 0.15f), spotColor = LigayaTheme.colors.cocoa.copy(alpha = 0.15f))
                    .clip(BubbleShape)
                    .background(LigayaTheme.colors.bubbleUser)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Row(modifier = Modifier.padding(top = 4.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (time != null) Text(time, style = LigayaTypography.messageTime, color = LigayaTheme.colors.taupe)
                if (answered) {
                    Icon(
                        LigayaIcons.answered,
                        contentDescription = "Answered by Ligaya",
                        tint = LigayaTheme.colors.online,
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
        }
    }
}

/** Three dots in Ligaya's bubble while she's working out a reply. Still, not bouncing, with reduced motion. */
@Composable
private fun TypingIndicator() {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "typingPhase")
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Ligaya is typing" },
        verticalAlignment = Alignment.Top,
    ) {
        LigayaAvatar(size = 40.dp)
        Row(
            modifier = Modifier
                .padding(start = 10.dp)
                .shadow(2.dp, BubbleShape, ambientColor = LigayaTheme.colors.cocoa.copy(alpha = 0.15f), spotColor = LigayaTheme.colors.cocoa.copy(alpha = 0.15f))
                .clip(BubbleShape)
                .background(LigayaTheme.colors.bubbleLigaya)
                .border(1.dp, LigayaTheme.colors.bubbleLigayaEdge, BubbleShape)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                val lit = !reduceMotion && phase.toInt() == i
                Box(Modifier.size(8.dp).alpha(if (reduceMotion || lit) 0.9f else 0.35f).clip(CircleShape).background(LigayaTheme.colors.taupe))
            }
        }
    }
}

@Composable
private fun ConversationNote(text: String) {
    Text(
        text = text,
        style = LigayaTypography.chatStatus,
        color = LigayaTheme.colors.taupe,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

@Composable
private fun ChatInput(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun send() {
        val message = text.trim()
        if (message.isEmpty()) return
        text = ""
        onSend(message)
    }
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(8.dp, shape, ambientColor = LigayaTheme.colors.cocoa.copy(alpha = 0.2f), spotColor = LigayaTheme.colors.cocoa.copy(alpha = 0.2f))
            .clip(shape)
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.shellEdge, shape)
            .padding(start = 22.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            maxLines = 4,
            textStyle = LigayaTypography.askField.copy(color = LigayaTheme.colors.cocoaInk),
            cursorBrush = SolidColor(LigayaTheme.colors.berry),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f).testTag("companionTextInput"),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Type a message...", style = LigayaTypography.askField, color = LigayaTheme.colors.taupe)
                    field()
                }
            },
        )
        val canSend = text.isNotBlank()
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                // Solid in both states, as the design draws it; an empty message simply isn't sent.
                .background(LigayaTheme.colors.berry)
                .clickable(enabled = canSend, role = Role.Button) { send() }
                .semantics { contentDescription = "Send message" }
                .testTag("companionSendButton"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LigayaIcons.send, contentDescription = null, tint = LigayaTheme.colors.onBerry, modifier = Modifier.size(22.dp).rotate(-35f))
        }
    }
}
