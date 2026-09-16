package com.ligaya.feature.safetycircle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaBackButton
import com.ligaya.designsystem.ligayaElevation
import kotlinx.coroutines.launch

/**
 * The Safety Circle tab's own "Quick actions" row leads here rather than doing all three at once —
 * see [SafetyCircleHomeScreen]'s own doc comment for why. Three separate, honestly-stated actions:
 *
 * - **Call 911**: real, via [onCallSos] — the same [android.content.Intent.ACTION_DIAL] hand-off
 *   every other 911 control in this app uses, reached through the deterministic safety engine
 *   (section 12), never bypassing it.
 * - **Alert your circle**: only ever as real as [circleAlertsConfigured] says it is — with no
 *   backend project configured, this row states that plainly and is not a button, per section 23's
 *   rule against implying an action that would not do anything.
 * - **Share your location**: real, via [onShareLocation] — the device's own location and Android's
 *   own share sheet, needing neither a backend nor any API key. [onShareLocation] returns whether a
 *   location was actually found and shared, never a silent no-op.
 */
@Composable
fun QuickActionsScreen(
    onBack: () -> Unit,
    onCallSos: () -> Unit,
    circleAlertsConfigured: Boolean,
    onShareLocation: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var sharingLocation by remember { mutableStateOf(false) }
    var shareResultMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LigayaBackButton(onClick = onBack)
            Text(
                text = "Quick actions",
                style = LigayaTypography.settingsTitle,
                color = LigayaTheme.colors.cocoaInk,
            )
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Each of these works on its own — nothing here waits on the others.",
                style = LigayaTypography.chatStatus,
                color = LigayaTheme.colors.taupe,
            )

            Spacer(Modifier.height(18.dp))

            CircleCard {
                ActionRow(
                    icon = LigayaIcons.callPhone,
                    label = "Call 911",
                    detail = "Starts an emergency and opens the dialer, pre-filled with 911.",
                    onClick = onCallSos,
                )
            }

            Spacer(Modifier.height(14.dp))

            CircleCard {
                // Never a button, in either state: family alerts fire automatically once an emergency is
                // active (section 17) — there is no "send one right now" action anywhere in this app's own
                // architecture to wire a tap to, configured backend or not.
                CircleFact(
                    label = if (circleAlertsConfigured) "Alert your circle" else "Alert your circle — not set up on this build",
                    value = if (circleAlertsConfigured) {
                        "Your Safety Circle is alerted with your location automatically once an emergency " +
                            "starts — nothing to tap here ahead of time."
                    } else {
                        "This needs the same Ligaya backend project the Safety Circle tab already explains " +
                            "is missing. Nobody would be alerted, so this is not a button here."
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            CircleCard {
                ActionRow(
                    icon = LigayaIcons.shareLocation,
                    label = "Share your location",
                    detail = "Opens your phone's own share sheet with a map link to where you are right now.",
                    busy = sharingLocation,
                    onClick = {
                        sharingLocation = true
                        shareResultMessage = null
                        scope.launch {
                            val shared = onShareLocation()
                            sharingLocation = false
                            if (!shared) {
                                shareResultMessage = "Location isn't available right now — check that " +
                                    "location access is on for Ligaya and try again."
                            }
                        }
                    },
                )
            }
            AnimatedVisibility(visible = shareResultMessage != null) {
                Text(
                    text = shareResultMessage.orEmpty(),
                    style = LigayaTypography.chatStatus,
                    color = LigayaTheme.colors.colorStatusFailed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp)
                        .semantics { contentDescription = "Share location: ${shareResultMessage.orEmpty()}" },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, detail: String, onClick: () -> Unit, busy: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label. $detail" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(LigayaTheme.colors.blush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = LigayaTheme.colors.accentInk, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(label, style = LigayaTypography.settingsRow, color = LigayaTheme.colors.cocoaInk)
            Text(
                text = detail,
                style = LigayaTypography.chatStatus,
                color = LigayaTheme.colors.taupe,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LigayaTheme.colors.berry)
        }
    }
}
