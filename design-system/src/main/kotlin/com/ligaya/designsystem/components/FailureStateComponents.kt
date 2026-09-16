package com.ligaya.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.ligayaButtonElevation
import com.ligaya.designsystem.ligayaElevation

/**
 * Step 41's own brief: "give every failure mode from section 21/Step 28 its own calm, non-alarming,
 * actionable visual treatment... each visually distinct from a generic error toast." Step 28's
 * checklist (see core-emergency-engine's EmergencyState.kt for the authoritative list this
 * mirrors) names seven failure rows; each maps onto exactly one of the four components below --
 * [CallFailedCard] (CALL_FAILED), [LookupFailedCard] (EMERGENCY_SERVICE_LOOKUP_FAILED and
 * "phone number missing", both real lookup-adjacent outcomes distinguished by [LookupFailureReason]
 * rather than two near-duplicate components), [DeliveryFailedCard] (SMS_FAILED, PUSH_FAILED), and
 * [OfflineDegradedBanner] (GEMINI_FAILED, GPS/location unavailable, via [DegradedReason]).
 *
 * "Calm, non-alarming" used to mean a solid [LigayaTheme.colors.colorStatusFailed] block -- this
 * predates the app's own visual design pass (see git history) and read as the opposite of calm
 * next to every other screen's soft cream cards. Repainted onto the same
 * [LigayaTheme.colors.shell] surface [StatusCard] now uses, with the tone carried by a
 * [LigayaTheme.colors.colorStatusFailed] border and accent disc rather than the whole card -- the
 * failure is still unmistakable (a red-bordered card among plain ones, still its own distinct
 * treatment from a generic [StatusCard]), just no longer shouting alone in a room where
 * everything else went quiet. [CallFailedCard]'s Retry pill is still the one place a solid
 * coloured surface remains, because it is a button, not a status card.
 */

@Composable
fun CallFailedCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .ligayaElevation(shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.colorStatusFailed, RoundedCornerShape(22.dp))
            .padding(LigayaSpacing.md)
            .semantics { contentDescription = "911 call failed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FailureDisc(icon = Icons.Filled.PhoneDisabled)
        Column(modifier = Modifier.weight(1f).padding(start = LigayaSpacing.sm)) {
            Text(text = "911 call failed", style = LigayaTypography.headline, color = LigayaTheme.colors.cocoaInk)
            Text(text = "The call did not go through.", style = LigayaTypography.body, color = LigayaTheme.colors.taupe)
        }
        Spacer(Modifier.width(LigayaSpacing.sm))
        Row(
            modifier = Modifier
                .ligayaButtonElevation(elevation = 3.dp)
                .clip(LigayaShapes.pill)
                .background(LigayaTheme.colors.colorStatusFailed)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = LigayaSpacing.md, vertical = LigayaSpacing.sm),
        ) {
            Text("Retry", style = LigayaTypography.label, color = LigayaTheme.colors.onStatusFailed)
        }
    }
}

/** [SERVICE_NOT_FOUND]: EMERGENCY_SERVICE_LOOKUP_FAILED. [PHONE_NUMBER_MISSING]: "emergency-service
 *  phone number missing, do not invent one" -- both real Step 28 checklist rows, both resolved the
 *  same way (911 remains the reliable path), so they share one component distinguished by copy. */
enum class LookupFailureReason { SERVICE_NOT_FOUND, PHONE_NUMBER_MISSING }

@Composable
fun LookupFailedCard(reason: LookupFailureReason, modifier: Modifier = Modifier) {
    val message = when (reason) {
        LookupFailureReason.SERVICE_NOT_FOUND -> "Couldn't find a nearby emergency service."
        LookupFailureReason.PHONE_NUMBER_MISSING -> "No phone number available for this service."
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .ligayaElevation(shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.colorStatusFailed, RoundedCornerShape(22.dp))
            .padding(LigayaSpacing.md)
            .semantics { contentDescription = "$message Please call 911 directly." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FailureDisc(icon = Icons.Filled.SearchOff)
        Column(modifier = Modifier.padding(start = LigayaSpacing.sm)) {
            Text(text = message, style = LigayaTypography.body, color = LigayaTheme.colors.cocoaInk)
            Text(
                text = "Please call 911 directly.",
                style = LigayaTypography.label,
                color = LigayaTheme.colors.colorStatusFailed,
            )
        }
    }
}

@Composable
fun DeliveryFailedCard(channelLabel: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .ligayaElevation(shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(LigayaTheme.colors.shell)
            .border(1.dp, LigayaTheme.colors.colorStatusFailed, RoundedCornerShape(22.dp))
            .padding(LigayaSpacing.md)
            .semantics { contentDescription = "$channelLabel delivery failed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FailureDisc(icon = Icons.Filled.ErrorOutline)
        Text(
            text = "$channelLabel delivery failed",
            style = LigayaTypography.body,
            color = LigayaTheme.colors.cocoaInk,
            modifier = Modifier.padding(start = LigayaSpacing.sm),
        )
    }
}

/** The small tinted-disc icon badge every failure row shares -- the same shape [StatusCard] and
 *  screen 6's own outcome cards use, so a failure here reads as one more status row, not a
 *  different visual language. */
@Composable
private fun FailureDisc(icon: ImageVector) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(LigayaTheme.colors.colorStatusFailed.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = LigayaTheme.colors.colorStatusFailed, modifier = Modifier.size(20.dp))
    }
}

/** [GEMINI_UNAVAILABLE]: GEMINI_FAILED. [LOCATION_UNAVAILABLE]: "GPS / location unavailable". Both
 *  are the app continuing in a reduced-capability mode, not an action that failed. */
enum class DegradedReason { GEMINI_UNAVAILABLE, LOCATION_UNAVAILABLE }

@Composable
fun OfflineDegradedBanner(reason: DegradedReason, modifier: Modifier = Modifier) {
    val message = when (reason) {
        DegradedReason.GEMINI_UNAVAILABLE -> "Voice assistant is temporarily unavailable. Emergency actions still work."
        DegradedReason.LOCATION_UNAVAILABLE -> "Location is unavailable right now. Emergency actions still work."
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LigayaTheme.colors.colorStatusPending.copy(alpha = 0.16f))
            .semantics { contentDescription = message }
            .padding(LigayaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (reason == DegradedReason.GEMINI_UNAVAILABLE) Icons.Filled.SyncProblem else Icons.Filled.CloudOff,
            contentDescription = null,
            tint = LigayaTheme.colors.colorStatusPending,
        )
        Spacer(Modifier.width(LigayaSpacing.sm))
        Text(text = message, style = LigayaTypography.label, color = LigayaTheme.colors.cocoaInk)
    }
}

@LigayaComponentPreviews
@Composable
private fun FailureStateComponentsPreview() {
    MaterialTheme {
        Column {
            CallFailedCard(onRetry = {})
            LookupFailedCard(reason = LookupFailureReason.SERVICE_NOT_FOUND)
            DeliveryFailedCard(channelLabel = "SMS")
            OfflineDegradedBanner(reason = DegradedReason.LOCATION_UNAVAILABLE)
        }
    }
}
