package com.ligaya.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ligaya.core.permissions.PermissionState
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaBackButton
import com.ligaya.designsystem.components.LigayaPrimaryButton
import com.ligaya.designsystem.components.LigayaSecondaryButton
import com.ligaya.designsystem.rememberIsReduceMotionEnabled

/**
 * Screen 4: §3's location-permission step, as a primer shown *before* the system dialog rather
 * than instead of it.
 *
 * The primer exists because Android only ever gives an app one good chance to ask: the reasons
 * have to be on screen before the system dialog appears, not after it's been dismissed. §3's own
 * flow says as much — permission granted enables location-dependent features, denial gets
 * "explain reduced functionality" rather than a dead end.
 *
 * The screen adapts to [permissionState] because the right action genuinely differs by state, and
 * getting this wrong produces the worst possible bug in a permission primer: a button that appears
 * to do nothing. Once Android reports [PermissionState.PermanentlyDenied], `launch()` no longer
 * shows a dialog at all — so the only honest action left is to send the user to Settings, and the
 * copy has to say so. [PermissionState.Granted] likewise must not re-ask.
 *
 * Denial never blocks: "Not Now" is always available and always continues. Location is one of
 * §13's five independent subsystems — losing it degrades the emergency, it does not stop it.
 */
@Composable
fun LocationPermissionScreen(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberIsReduceMotionEnabled()
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            entrance.snapTo(1f)
        } else {
            entrance.animateTo(1f, tween(LigayaMotion.durationEntrance, easing = LigayaMotion.easingEntrance))
        }
    }
    val t = entrance.value

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LigayaSpacing.lg),
    ) {
        LigayaBackButton(onClick = onBack)

        Spacer(modifier = Modifier.height(LigayaSpacing.xl))

        val badge = LigayaMotion.easingEntrance.transform(slice(t, 0f, 0.55f))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(BADGE_SIZE)
                .graphicsLayer {
                    // Settles in rather than appearing, matching the splash's own entrance
                    // character so the two screens feel like one product.
                    scaleX = BADGE_START_SCALE + (1f - BADGE_START_SCALE) * badge
                    scaleY = BADGE_START_SCALE + (1f - BADGE_START_SCALE) * badge
                    alpha = badge
                }
                .clip(CircleShape)
                .background(LigayaTheme.colors.blush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LigayaIcons.location,
                contentDescription = null,
                tint = LigayaTheme.colors.roseDeep,
                modifier = Modifier.size(BADGE_ICON_SIZE),
            )
        }

        Spacer(modifier = Modifier.height(LigayaSpacing.lg))

        val granted = permissionState == PermissionState.Granted
        val permanentlyDenied = permissionState == PermissionState.PermanentlyDenied

        Text(
            text = if (granted) "Location access is on" else "Allow location access",
            style = LigayaTypography.headline,
            color = LigayaTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(LigayaMotion.easingEntrance.transform(slice(t, 0.2f, 0.75f))),
        )

        Spacer(modifier = Modifier.height(LigayaSpacing.md))

        Column(
            modifier = Modifier.alpha(LigayaMotion.easingEntrance.transform(slice(t, 0.35f, 0.9f))),
        ) {
            Text(
                text = if (granted) {
                    "Ligaya can use your location during an emergency to:"
                } else {
                    "We need your location to:"
                },
                style = LigayaTypography.body,
                color = LigayaTheme.colors.inkSoft,
            )
            Spacer(modifier = Modifier.height(LigayaSpacing.sm))
            REASONS.forEach { reason -> ReasonRow(reason) }
        }

        if (permanentlyDenied) {
            Spacer(modifier = Modifier.height(LigayaSpacing.md))
            Text(
                // The one case where the app genuinely cannot ask again — saying "allow" here
                // would be a button that visibly does nothing.
                text = "Location is currently blocked for Ligaya, so we can't ask again from here. " +
                    "You can turn it on in Settings. Without it, an emergency still starts and still " +
                    "calls 911. We just cannot share where you are.",
                style = LigayaTypography.label,
                color = LigayaTheme.colors.colorStatusPending,
            )
        }

        Spacer(modifier = Modifier.height(LigayaSpacing.xl))

        Column(
            modifier = Modifier.alpha(LigayaMotion.easingEntrance.transform(slice(t, 0.5f, 1f))),
        ) {
            when {
                granted -> LigayaPrimaryButton(text = "Continue", onClick = onContinue)
                permanentlyDenied -> LigayaPrimaryButton(text = "Open Settings", onClick = onOpenSettings)
                else -> LigayaPrimaryButton(text = "Allow While Using App", onClick = onRequestPermission)
            }

            if (!granted) {
                Spacer(modifier = Modifier.height(LigayaSpacing.sm))
                LigayaSecondaryButton(text = "Not Now", onClick = onContinue)
            }
        }

        Spacer(modifier = Modifier.height(LigayaSpacing.xl))
    }
}

@Composable
private fun ReasonRow(reason: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LigayaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(BULLET_SIZE)
                .clip(CircleShape)
                .background(LigayaTheme.colors.rose),
        )
        Text(
            text = reason,
            style = LigayaTypography.body,
            color = LigayaTheme.colors.ink,
            modifier = Modifier.padding(start = LigayaSpacing.sm),
        )
    }
}

private fun slice(master: Float, start: Float, end: Float): Float =
    ((master - start) / (end - start)).coerceIn(0f, 1f)

private val REASONS = listOf(
    "Find nearby emergency services",
    "Share your location with your Safety Circle",
    "Provide faster, more accurate help",
)

private val BADGE_SIZE = 96.dp
private val BADGE_ICON_SIZE = 44.dp
private val BULLET_SIZE = 6.dp
private const val BADGE_START_SCALE = 0.7f
