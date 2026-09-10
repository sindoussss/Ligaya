package com.ligaya.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.LigayaVoiceState
import com.ligaya.designsystem.components.SosControl
import com.ligaya.designsystem.components.SosControlState
import com.ligaya.designsystem.components.VoiceStateIndicator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Section 4's Home screen: "Calm normal-mode design, SOS control visually dominant but not
 * alarming when idle, Safety Circle status glanceable, AI assistance visually separated from
 * emergency actions." Every color/spacing/type value below comes from design-system's tokens
 * (Steps 33/34) — this step's own acceptance criterion is that none of it is hardcoded.
 *
 * The SOS control here calls [EmergencyController.triggerSos] directly on tap and calls
 * [onSosActivated] once it resolves — Step 13's flow reached from Home itself, not via first
 * navigating to the separate "Sos" placeholder route (Step 7's own scaffolding, left untouched
 * and still independently reachable through [otherDestinations] below; this doesn't replace it,
 * it gives Home its own real, directly-actionable control per the brief).
 *
 * [otherDestinations] and [onNavigateToRoute] exist so this module never depends on :app's own
 * navigation types (that dependency would run backwards — :app depends on this module, not the
 * other way around): :app's own nav host passes in the (route, title) pairs for whichever
 * destinations aren't yet given their own dedicated entry point here, preserving "every route
 * reachable from Home" (Step 7's own test) unchanged, without this module knowing what a
 * LigayaDestination is.
 *
 * [voicePhase]/[voiceAiUnavailable] (Step 53 audit follow-up): section 27's "persistent, always-
 * visible" voice indicator previously only ever appeared on the Emergency Active/Companion
 * screens, during an already-active emergency — the always-on background wake-word loop (section
 * 5's headline hands-free feature, running continuously from Home) had no visual presence here at
 * all, and its one AI-unavailable case (section 21: "the UI must clearly tell the user") was
 * spoken via TTS only. Both gaps closed the same way the rest of this app already does it: real
 * live state from [com.ligaya.core.voice.VoiceActivationCoordinator], not a static placeholder.
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
) {
    val scope = rememberCoroutineScope()
    val phase by voicePhase.collectAsState()
    val aiUnavailable by voiceAiUnavailable.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaColors.idleBackground)
            .verticalScroll(rememberScrollState())
            .padding(LigayaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Home", style = LigayaTypography.headline, color = LigayaColors.onSurface)

        Row(
            modifier = Modifier.padding(top = LigayaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm),
        ) {
            VoiceStateIndicator(state = phase.toLigayaVoiceState())
            Text(text = voiceStateLabel(phase), style = LigayaTypography.body, color = LigayaColors.onSurface)
        }
        if (aiUnavailable) {
            Text(
                text = "Voice assistant unavailable right now — use the SOS button instead.",
                style = LigayaTypography.body,
                color = LigayaColors.colorStatusPending,
                modifier = Modifier.padding(top = LigayaSpacing.xs),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = LigayaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SosControl(
                state = SosControlState.Idle,
                onClick = {
                    scope.launch {
                        when (emergencyController.triggerSos()) {
                            is SosResult.Activated, is SosResult.AlreadyInProgress -> onSosActivated()
                        }
                    }
                },
            )
        }

        GlanceableCard(
            title = "Safety Circle",
            body = "Tap to view your Safety Circle's status",
            onClick = onNavigateToSafetyCircle,
            containerColor = LigayaColors.idleBackground,
            contentColor = LigayaColors.onSurface,
        )

        // "AI assistance visually separated from emergency actions": its own spacing gap and its
        // own tonal container (LigayaColors.idlePrimary, not the neutral idleBackground the
        // Safety Circle card above uses), distinct from the SOS control and that card.
        Column(modifier = Modifier.padding(top = LigayaSpacing.xl)) {
            GlanceableCard(
                title = "Emergency Companion",
                body = "Ask Ligaya's AI assistant for guidance",
                onClick = onNavigateToCompanion,
                containerColor = LigayaColors.idlePrimary,
                contentColor = LigayaColors.onIdlePrimary,
            )
        }

        if (otherDestinations.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = LigayaSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                otherDestinations.forEach { destination ->
                    Button(
                        onClick = { onNavigateToRoute(destination.route) },
                        modifier = Modifier.padding(vertical = LigayaSpacing.xs),
                    ) {
                        Text(destination.title)
                    }
                }
            }
        }
    }
}

/** A route/title pair — this module's own minimal stand-in for :app's LigayaDestination, so this
 *  module never depends on :app (see [HomeScreen]'s own doc comment). */
data class NavigableDestination(val route: String, val title: String)

/** [VoiceStateIndicator]'s own icon/color already carries the state visually (and its own
 *  contentDescription covers accessibility) — this is the glanceable text label alongside it,
 *  phrased for Home's own ambient, no-emergency-yet context rather than [VoiceStateIndicator]'s
 *  generic "Voice assistant listening" (apt during an active companion turn, less so for "this is
 *  always on in the background, waiting for a wake phrase"). */
private fun voiceStateLabel(phase: VoicePipelinePhase): String = when (phase) {
    VoicePipelinePhase.IDLE -> "Voice assistant idle"
    VoicePipelinePhase.LISTENING -> "Listening for \"Ligaya\"…"
    VoicePipelinePhase.PROCESSING -> "Understanding what you said…"
    VoicePipelinePhase.SPEAKING -> "Ligaya is speaking…"
}

@Composable
private fun GlanceableCard(
    title: String,
    body: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    // Step 45: an explicit floor, not an assumption that the headline+body text stack below
    // always renders taller than 48dp on its own — the touch target must hold regardless of
    // content length, matching this codebase's other custom-shaped tappable components (e.g.
    // SosControl's own explicit emergencyTouchTarget size).
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LigayaSpacing.minTouchTarget)
            .clip(RoundedCornerShape(LigayaSpacing.sm))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title: $body" },
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier.padding(LigayaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LigayaSpacing.xs),
        ) {
            Text(text = title, style = LigayaTypography.headline, color = contentColor)
            Text(text = body, style = LigayaTypography.body, color = contentColor)
        }
    }
}
