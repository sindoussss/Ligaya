package com.ligaya.feature.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ligaya.core.ai.CompanionTurn
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.VoiceStateIndicator
import kotlinx.coroutines.launch

/**
 * Step 39's real Emergency Companion screen: the transcript placeholder
 * feature-emergency-active's [com.ligaya.feature.companion.EmergencyCompanionCoordinator] left
 * behind in Step 37/38 becomes a live view here — [EmergencyCompanionCoordinator.transcript]
 * rendered as a scrolling list, [EmergencyCompanionCoordinator.phase] driving the same
 * [VoiceStateIndicator] Step 38 already wired into feature-emergency-active, and a text input row
 * as the always-available fallback for a user who can't or doesn't want to speak
 * ([EmergencyCompanionCoordinator.runOneTurnWithText] — see that class's own doc comment for why
 * this never touches voice capture).
 *
 * Voice stays the primary path: nothing here disables or hides microphone-driven turns, and the
 * text row is deliberately plain (no mic icon, no "or type instead" framing) — just a normal input
 * that happens to always be there, matching section 19's "voice-first" framing being about how
 * *Ligaya* communicates, not a requirement on the user's own input method (the same point
 * [EmergencyCompanionCoordinator.respondTo] documents).
 */
@Composable
fun EmergencyCompanionScreen(
    coordinator: EmergencyCompanionCoordinator,
    modifier: Modifier = Modifier,
) {
    val transcript by coordinator.transcript.collectAsState()
    val phase by coordinator.phase.collectAsState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LigayaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm),
        ) {
            VoiceStateIndicator(state = phase.toLigayaVoiceState())
            Text(text = "Emergency Companion", style = LigayaTypography.headline, color = LigayaColors.onSurface)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = LigayaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LigayaSpacing.sm),
        ) {
            items(transcript) { turn -> TranscriptTurnRow(turn) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LigayaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("companionTextInput"),
                placeholder = { Text("Type a message") },
                singleLine = true,
            )
            Button(
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .testTag("companionSendButton")
                    .semantics { contentDescription = "Send message" },
                onClick = {
                    val text = inputText
                    inputText = ""
                    scope.launch { coordinator.runOneTurnWithText(text) }
                },
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun TranscriptTurnRow(turn: CompanionTurn) {
    val speakerLabel = when (turn.speaker) {
        CompanionTurn.Speaker.USER -> "You"
        CompanionTurn.Speaker.LIGAYA -> "Ligaya"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$speakerLabel said: ${turn.text}" },
    ) {
        Text(text = speakerLabel, style = LigayaTypography.label, color = LigayaColors.idlePrimary)
        Text(text = turn.text, style = LigayaTypography.body, color = LigayaColors.onSurface)
    }
}
