package com.ligaya.feature.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ligaya.core.data.family.FamilyEmergencyLocation
import com.ligaya.core.data.family.FamilyEmergencyView
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaDeliveryState
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaIncidentType
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.DeliveryStateBadge
import com.ligaya.designsystem.components.StatusCard
import com.ligaya.designsystem.components.StatusTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Step 40's Family emergency screen: "mirrors §18's field list exactly (incident type, location,
 * time, status, alert state) with permission-gated visibility enforced from data the backend
 * already filtered — the UI must not have to hide fields client-side that the backend already
 * should not have sent." Every field this composable renders comes straight from [view]; there is
 * no separate client-side permission check anywhere here, because [FamilyEmergencyView.location]
 * being null is already the backend's own final word on whether this viewer may see it (Step 20).
 *
 * This step's own acceptance criterion — "renders correctly with partial data ... without layout
 * breakage" — is why every field is rendered through an explicit present/absent branch rather
 * than assuming a value exists: [location] and [FamilyEmergencyView.alertState] are both
 * legitimately null in ordinary use (withheld by permission, or no notification sent yet), not
 * error states to guard against.
 */
@Composable
fun FamilyEmergencyScreen(view: FamilyEmergencyView, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LigayaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LigayaSpacing.md),
    ) {
        IncidentTypeHeader(view.incidentType)
        TimeLine(view.timeEpochMillis)
        StatusCard(
            title = "Status",
            message = humanize(view.status),
            tone = StatusTone.Neutral,
        )
        AlertStateSection(view.alertState)
        LocationSection(view.location)
    }
}

@Composable
private fun IncidentTypeHeader(incidentType: String) {
    val ligayaType = incidentType.toLigayaIncidentTypeOrNull()
    val label = ligayaType?.let { humanize(incidentType) } ?: incidentType
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Incident type: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm),
    ) {
        ligayaType?.let {
            Icon(
                imageVector = requireNotNull(LigayaIcons.incidentType[it]) { "no icon mapped for $it" },
                contentDescription = null,
                tint = LigayaColors.onSurface,
            )
        }
        Text(text = label, style = LigayaTypography.display, color = LigayaColors.onSurface)
    }
}

@Composable
private fun TimeLine(timeEpochMillis: Long) {
    val formatted = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timeEpochMillis))
    Text(
        text = formatted,
        style = LigayaTypography.body,
        color = LigayaColors.onSurface,
        modifier = Modifier.semantics { contentDescription = "Time: $formatted" },
    )
}

@Composable
private fun AlertStateSection(alertState: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(LigayaSpacing.xs)) {
        Text(text = "Alert Status", style = LigayaTypography.label, color = LigayaColors.onSurface)
        when (val ligayaState = alertState?.toLigayaDeliveryStateOrNull()) {
            null -> Text(
                text = alertState ?: "Not sent yet",
                style = LigayaTypography.body,
                color = LigayaColors.onSurface,
                modifier = Modifier.semantics { contentDescription = "Alert status: ${alertState ?: "not sent yet"}" },
            )
            else -> DeliveryStateBadge(state = ligayaState)
        }
    }
}

@Composable
private fun LocationSection(location: FamilyEmergencyLocation?) {
    Column(verticalArrangement = Arrangement.spacedBy(LigayaSpacing.xs)) {
        Text(text = "Location", style = LigayaTypography.label, color = LigayaColors.onSurface)
        val message = location?.let { "%.5f, %.5f".format(it.latitude, it.longitude) }
            ?: "Location not shared"
        Text(
            text = message,
            style = LigayaTypography.body,
            color = LigayaColors.onSurface,
            modifier = Modifier.semantics { contentDescription = "Location: $message" },
        )
    }
}

private fun String.toLigayaIncidentTypeOrNull(): LigayaIncidentType? =
    runCatching { LigayaIncidentType.valueOf(this) }.getOrNull()

private fun String.toLigayaDeliveryStateOrNull(): LigayaDeliveryState? =
    runCatching { LigayaDeliveryState.valueOf(this) }.getOrNull()

/** "EMERGENCY_ACTIVE" -> "Emergency Active" — cosmetic only, never changes which branch above a
 *  value takes; an unrecognized raw string is displayed exactly as sent, still humanized the same
 *  way, rather than hidden. */
private fun humanize(raw: String): String = raw
    .lowercase(Locale.getDefault())
    .split('_')
    .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
