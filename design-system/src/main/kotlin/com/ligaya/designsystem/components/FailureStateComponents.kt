package com.ligaya.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography

/**
 * Step 41's own brief: "give every failure mode from §21/Step 28 its own calm, non-alarming,
 * actionable visual treatment... each visually distinct from a generic error toast." Step 28's
 * checklist (see core-emergency-engine's EmergencyState.kt for the authoritative list this
 * mirrors) names seven failure rows; each maps onto exactly one of the four components below —
 * [CallFailedCard] (CALL_FAILED), [LookupFailedCard] (EMERGENCY_SERVICE_LOOKUP_FAILED and
 * "phone number missing", both real lookup-adjacent outcomes distinguished by [LookupFailureReason]
 * rather than two near-duplicate components), [DeliveryFailedCard] (SMS_FAILED, PUSH_FAILED), and
 * [OfflineDegradedBanner] (GEMINI_FAILED, GPS/location unavailable, via [DegradedReason]).
 *
 * "Visually distinct from a generic error toast" is real here, not just different copy on
 * [StatusCard]: [CallFailedCard] is the only one with an actual retry action, [LookupFailedCard]
 * is outlined rather than filled (an outcome you're informed of, not one to retry), and
 * [OfflineDegradedBanner] is a full-width row rather than a card at all — a reduced-capability
 * banner, not a failure card, using the calmer amber [LigayaTheme.colors.colorStatusPending] token
 * rather than red, since "the app keeps working in a degraded mode" is a different message than
 * "this action failed."
 */

@Composable
fun CallFailedCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "911 call failed" },
        colors = CardDefaults.cardColors(
            containerColor = LigayaTheme.colors.colorStatusFailed,
            contentColor = LigayaTheme.colors.onStatusFailed,
        ),
    ) {
        Row(
            modifier = Modifier.padding(LigayaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.PhoneDisabled, contentDescription = null, tint = LigayaTheme.colors.onStatusFailed)
            Spacer(Modifier.width(LigayaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "911 call failed", style = LigayaTypography.headline, color = LigayaTheme.colors.onStatusFailed)
                Text(text = "The call didn't go through.", style = LigayaTypography.body, color = LigayaTheme.colors.onStatusFailed)
            }
            Spacer(Modifier.width(LigayaSpacing.sm))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LigayaTheme.colors.onStatusFailed,
                    contentColor = LigayaTheme.colors.colorStatusFailed,
                ),
            ) {
                Text("Retry")
            }
        }
    }
}

/** [SERVICE_NOT_FOUND]: EMERGENCY_SERVICE_LOOKUP_FAILED. [PHONE_NUMBER_MISSING]: "emergency-service
 *  phone number missing, do not invent one" — both real Step 28 checklist rows, both resolved the
 *  same way (911 remains the reliable path), so they share one component distinguished by copy. */
enum class LookupFailureReason { SERVICE_NOT_FOUND, PHONE_NUMBER_MISSING }

@Composable
fun LookupFailedCard(reason: LookupFailureReason, modifier: Modifier = Modifier) {
    val message = when (reason) {
        LookupFailureReason.SERVICE_NOT_FOUND -> "Couldn't find a nearby emergency service."
        LookupFailureReason.PHONE_NUMBER_MISSING -> "No phone number available for this service."
    }
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$message Please call 911 directly." },
        border = BorderStroke(1.dp, LigayaTheme.colors.colorStatusFailed),
    ) {
        Row(
            modifier = Modifier.padding(LigayaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.SearchOff, contentDescription = null, tint = LigayaTheme.colors.colorStatusFailed)
            Spacer(Modifier.width(LigayaSpacing.sm))
            Column {
                Text(text = message, style = LigayaTypography.body, color = LigayaTheme.colors.onSurface)
                Text(
                    text = "Please call 911 directly.",
                    style = LigayaTypography.label,
                    color = LigayaTheme.colors.colorStatusFailed,
                )
            }
        }
    }
}

@Composable
fun DeliveryFailedCard(channelLabel: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$channelLabel delivery failed" },
        colors = CardDefaults.cardColors(
            containerColor = LigayaTheme.colors.colorStatusFailed,
            contentColor = LigayaTheme.colors.onStatusFailed,
        ),
    ) {
        Row(
            modifier = Modifier.padding(LigayaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = LigayaTheme.colors.onStatusFailed)
            Spacer(Modifier.width(LigayaSpacing.sm))
            Text(
                text = "$channelLabel delivery failed",
                style = LigayaTypography.body,
                color = LigayaTheme.colors.onStatusFailed,
            )
        }
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
            .background(LigayaTheme.colors.colorStatusPending.copy(alpha = 0.2f))
            .semantics { contentDescription = message }
            .padding(LigayaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (reason == DegradedReason.GEMINI_UNAVAILABLE) Icons.Filled.SyncProblem else Icons.Filled.CloudOff,
            contentDescription = null,
            tint = LigayaTheme.colors.onStatusPending,
        )
        Spacer(Modifier.width(LigayaSpacing.sm))
        Text(text = message, style = LigayaTypography.label, color = LigayaTheme.colors.onStatusPending)
    }
}
