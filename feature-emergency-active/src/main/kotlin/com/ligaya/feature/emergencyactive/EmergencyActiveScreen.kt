package com.ligaya.feature.emergencyactive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ligaya.core.emergencyengine.ConcurrentSubsystemStates
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.core.uistate.PresentationTone
import com.ligaya.core.uistate.toPresentation
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaDeliveryState
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.CallFailedCard
import com.ligaya.designsystem.components.DegradedReason
import com.ligaya.designsystem.components.DeliveryFailedCard
import com.ligaya.designsystem.components.DeliveryStateBadge
import com.ligaya.designsystem.components.LookupFailedCard
import com.ligaya.designsystem.components.LookupFailureReason
import com.ligaya.designsystem.components.OfflineDegradedBanner
import com.ligaya.designsystem.components.StatusCard
import com.ligaya.designsystem.components.VoiceStateIndicator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The Emergency Active screen (Step 37): "the most information-dense but must stay
 * low-cognitive-load." Every card here is driven by [EmergencyController.observeSnapshot] and
 * Step 35's presentation mappings — nothing here decides emergency behavior, only how to show a
 * state the engine already decided (this module's whole reason to exist, same as core-ui-state's
 * own doc comment says of itself one layer down).
 *
 * "Screen never blocks on a slow-loading subsystem" (this step's own acceptance criterion) is a
 * consequence of this structure, not a special case handled anywhere: every card renders
 * whatever [ConcurrentSubsystemStates] value is CURRENTLY true (Pending until a coordinator
 * reports otherwise), so a slow or failed subsystem is just another value already representable —
 * there is no synchronous wait for anything in this composable itself.
 *
 * "I'm safe" is pinned outside the scrollable content (a fixed [Column] slot below it, not part
 * of [Modifier.verticalScroll]) so it stays visible and tappable regardless of scroll position or
 * what else on screen is still loading or has failed — reachable "regardless of what else is
 * loading or has failed," per the brief's own words for this control.
 *
 * [voicePipelinePhase] (Step 38): the Emergency Companion card's placeholder text is still just a
 * placeholder (the real transcript UI is a later step's job, same as Step 37 left it), but the
 * voice indicator next to it is real — bound to feature-companion's actual
 * EmergencyCompanionCoordinator.phase, not a static value, via [VoicePipelinePhase.toLigayaVoiceState].
 *
 * Step 41: three of Step 28's checklist failure states are directly observable from
 * [ConcurrentSubsystemStates] this screen already reads, so those three now render their own
 * named treatment ([CallFailedCard]/[LookupFailedCard]/[OfflineDegradedBanner]) instead of a
 * generic [StatusCard], and a per-channel [DeliveryFailedCard] replaces the compact badge for a
 * FAILED Safety Circle channel. [onRetryCall] is a real action, not a placeholder — see
 * [EmergencyController.retryCall] and LigayaNavHost's own wiring of it — defaulting to a no-op
 * only for callers (previews, tests) that have no real controller action to give it. GEMINI_FAILED
 * and "phone number missing" (the checklist's other two rows) still have no live signal reaching
 * this screen at all — [com.ligaya.core.emergencyengine.EmergencyCompanionState] has no failure
 * sub-state (see its own doc comment) and phone-number availability is a core-places PlaceDetails
 * property never threaded into [ConcurrentSubsystemStates] — so both stay design-system-only
 * components, proven directly by FailureStateComponentsTest rather than live here. Unlike the
 * retry gap, fixing this would mean changing what core-emergency-engine itself tracks (a settled,
 * already-tested Step 9/13 data shape used everywhere), not just connecting existing pieces — a
 * design decision, not a wiring bug.
 */
@Composable
fun EmergencyActiveScreen(
    emergencyController: EmergencyController,
    safetyCircleDeliveryStatus: List<MemberDeliveryStatus>,
    voicePipelinePhase: StateFlow<VoicePipelinePhase>,
    onMarkedSafe: () -> Unit,
    onRetryCall: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snapshot by emergencyController.observeSnapshot().collectAsState(initial = null)
    val phase by voicePipelinePhase.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(LigayaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LigayaSpacing.md),
        ) {
            val state = snapshot?.state
            val subsystems = snapshot?.subsystems ?: ConcurrentSubsystemStates()

            StatusBanner(presentationLabel = state?.toPresentation()?.label ?: "Loading…", tone = state?.toPresentation()?.tone)
            ActivityLine(subsystems)

            if (subsystems.unified911 == Unified911FlowState.CallFailed) {
                CallFailedCard(onRetry = onRetryCall)
            } else {
                StatusCard(
                    title = "911",
                    message = subsystems.unified911.toPresentation().label,
                    tone = subsystems.unified911.toPresentation().tone.toStatusTone(),
                )
            }
            if (subsystems.location == LocationFlowState.Unavailable) {
                OfflineDegradedBanner(reason = DegradedReason.LOCATION_UNAVAILABLE)
            } else {
                StatusCard(
                    title = "Location",
                    message = subsystems.location.toPresentation().label,
                    tone = subsystems.location.toPresentation().tone.toStatusTone(),
                )
            }
            // "Optional, clearly secondary to 911" — same precedence as before, deliberately last
            // among the subsystem cards and given no special emphasis of its own.
            if (subsystems.emergencyService == EmergencyServiceFlowState.LookupFailed) {
                LookupFailedCard(reason = LookupFailureReason.SERVICE_NOT_FOUND)
            } else {
                StatusCard(
                    title = "Nearby Emergency Service",
                    message = subsystems.emergencyService.toPresentation().label,
                    tone = subsystems.emergencyService.toPresentation().tone.toStatusTone(),
                )
            }

            SafetyCircleSection(safetyCircleDeliveryStatus)

            // "Emergency Companion transcript area" — explicitly a placeholder per Step 37's own
            // "what's implemented": the real transcript UI is a later step's job. The voice
            // indicator alongside it is real (Step 38), not a placeholder.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm)) {
                VoiceStateIndicator(state = phase.toLigayaVoiceState())
                StatusCard(
                    title = "Emergency Companion",
                    message = "${subsystems.companion.toPresentation().label} (full companion view coming soon)",
                    tone = subsystems.companion.toPresentation().tone.toStatusTone(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ImSafeButton(
            onClick = {
                scope.launch {
                    // Only once the engine has actually accepted the transition: the screen this leads to states that
                    // the user is marked safe, and section 23 forbids showing that before it is true.
                    if (emergencyController.markSafe().isSuccess) onMarkedSafe()
                }
            },
        )
    }
}

@Composable
private fun StatusBanner(presentationLabel: String, tone: PresentationTone?) {
    val resolvedTone = tone ?: PresentationTone.NEUTRAL
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(resolvedTone.containerColor())
            .padding(LigayaSpacing.md)
            .semantics { contentDescription = "Emergency status: $presentationLabel" },
    ) {
        Text(text = presentationLabel, style = LigayaTypography.display, color = resolvedTone.contentColor())
    }
}

/** "Live 'what Ligaya is doing now' activity line": the first subsystem currently InProgress,
 *  or a calm default once nothing is — a single short line, not a repeat of every card below. */
@Composable
private fun ActivityLine(subsystems: ConcurrentSubsystemStates) {
    val inProgressLabel = listOf(
        subsystems.unified911.toPresentation(),
        subsystems.location.toPresentation(),
        subsystems.emergencyService.toPresentation(),
        subsystems.familyAlert.toPresentation(),
    ).firstOrNull { it.tone == PresentationTone.IN_PROGRESS }?.label

    Text(
        text = inProgressLabel ?: "Ligaya is monitoring your emergency",
        style = LigayaTypography.body,
        color = LigayaTheme.colors.onSurface,
        modifier = Modifier.semantics { contentDescription = "Current activity: ${inProgressLabel ?: "monitoring"}" },
    )
}

@Composable
private fun SafetyCircleSection(members: List<MemberDeliveryStatus>) {
    Column(verticalArrangement = Arrangement.spacedBy(LigayaSpacing.sm)) {
        Text(text = "Safety Circle", style = LigayaTypography.headline, color = LigayaTheme.colors.onSurface)
        if (members.isEmpty()) {
            Text(text = "No Safety Circle members to notify", style = LigayaTypography.body, color = LigayaTheme.colors.onSurface)
        } else {
            members.forEach { member ->
                Column(modifier = Modifier.padding(vertical = LigayaSpacing.xs)) {
                    Text(text = member.memberName, style = LigayaTypography.label, color = LigayaTheme.colors.onSurface)
                    Column(verticalArrangement = Arrangement.spacedBy(LigayaSpacing.xs)) {
                        member.channelStatuses.filter { it.state == LigayaDeliveryState.FAILED }.forEach { channel ->
                            DeliveryFailedCard(channelLabel = channel.channelLabel)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.xs)) {
                            member.channelStatuses.filter { it.state != LigayaDeliveryState.FAILED }.forEach { channel ->
                                DeliveryStateBadge(state = channel.state)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImSafeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(LigayaSpacing.md)
            .semantics { contentDescription = "I'm safe" },
        colors = ButtonDefaults.buttonColors(
            containerColor = LigayaTheme.colors.colorStatusConfirmed,
            contentColor = LigayaTheme.colors.onStatusConfirmed,
        ),
    ) {
        Text(text = "I'm safe", style = LigayaTypography.headline)
    }
}
