package com.ligaya.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ligaya.core.billing.EntitlementRepository
import com.ligaya.core.billing.PurchaseOutcome
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Step 44's Ligaya+ paywall (§7/§8). "Clearly separated from core safety features, never visually
 * implying core safety features are gated" (the UI/UX brief's own Ligaya+ row) is enforced at the
 * module-dependency level, not just by this screen's own copy: this module has no dependency path
 * to core-emergency-engine, core-ui-state, or any emergency screen at all — see this module's own
 * build.gradle.kts. The copy below only ever says what Ligaya+ *adds* (expanded Safety Circle
 * features); it never mentions SOS, 911, voice activation, or the Companion, because those are
 * never the subject of this screen.
 *
 * A real purchase/restore round trip needs `Purchases.configure()` to have already run with a
 * real RevenueCat API key — see [EntitlementRepository]'s own doc comment. Until then, every
 * action here degrades to a real, honest Failure message rather than crashing or fabricating
 * success.
 */
@Composable
fun PaywallScreen(
    entitlementRepository: EntitlementRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSubscribed by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { isSubscribed = entitlementRepository.isSubscribed() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LigayaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LigayaSpacing.md),
    ) {
        Text(text = "Ligaya+", style = LigayaTypography.display, color = LigayaColors.onSurface)
        Text(
            text = "Ligaya+ unlocks expanded Safety Circle features. Every core emergency " +
                "feature — SOS, voice activation, 911 calling, and the Emergency Companion — " +
                "is always free and never gated.",
            style = LigayaTypography.body,
            color = LigayaColors.onSurface,
        )

        if (isSubscribed) {
            Text(
                text = "You're subscribed to Ligaya+.",
                style = LigayaTypography.headline,
                color = LigayaColors.resolved,
                modifier = Modifier.semantics { contentDescription = "You're subscribed to Ligaya+" },
            )
        } else if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.testTag("paywallBusyIndicator"))
        } else {
            Button(
                modifier = Modifier.fillMaxWidth().testTag("paywallSubscribe"),
                onClick = {
                    val activity = context.findActivity()
                    if (activity == null) {
                        statusMessage = "Can't start checkout right now. Please try again."
                        return@Button
                    }
                    isBusy = true
                    scope.launch {
                        when (val outcome = entitlementRepository.purchase(activity)) {
                            is PurchaseOutcome.Success -> {
                                isSubscribed = true
                                statusMessage = null
                            }
                            is PurchaseOutcome.Failure -> statusMessage = outcome.message
                        }
                        isBusy = false
                    }
                },
            ) {
                Text("Subscribe to Ligaya+")
            }
            TextButton(
                modifier = Modifier.testTag("paywallRestore"),
                onClick = {
                    isBusy = true
                    scope.launch {
                        when (val outcome = entitlementRepository.restorePurchases()) {
                            is PurchaseOutcome.Success -> {
                                isSubscribed = entitlementRepository.isSubscribed()
                                statusMessage = null
                            }
                            is PurchaseOutcome.Failure -> statusMessage = outcome.message
                        }
                        isBusy = false
                    }
                },
            ) {
                Text("Restore Purchases")
            }
        }

        statusMessage?.let {
            Text(
                text = it,
                style = LigayaTypography.label,
                color = LigayaColors.colorStatusFailed,
                modifier = Modifier.semantics { contentDescription = "Paywall status: $it" },
            )
        }
    }
}
