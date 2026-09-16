package com.ligaya.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ligaya.core.billing.EntitlementRepository
import com.ligaya.core.billing.PurchaseOutcome
import com.ligaya.core.billing.SubscriptionTier
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaBackButton
import com.ligaya.designsystem.components.LigayaTab
import com.ligaya.designsystem.components.LigayaTabBar
import com.ligaya.designsystem.ligayaButtonElevation
import com.ligaya.designsystem.ligayaElevation
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The Ligaya+ paywall (§7/§8). "Clearly separated from core safety features, never visually
 * implying core safety features are gated" (the UI/UX brief's own Ligaya+ row) is enforced at the
 * module-dependency level, not just by this screen's own copy: this module has no dependency path
 * to core-emergency-engine, core-ui-state, or any emergency screen at all (root build.gradle.kts'
 * own checkNoBillingInCoreScreens task holds the reverse direction) — see this module's own
 * build.gradle.kts.
 *
 * The reference design's own checklist ("Unlimited conversations", "Advanced voice & speech",
 * "Custom character & animation", "Priority support") is not what this screen shows: none of
 * those are actually gated anywhere in this codebase — the Emergency Companion's conversation
 * turns, TTS/STT, and Character & Animation (Settings screen 10) are all free today, and section
 * 8 of the architecture is explicit that "core emergency functionality is never fully gated
 * behind Ligaya+, only family/household-scoped features are." Shipping the reference's own
 * checklist verbatim would be a false claim about what buying Ligaya+ unlocks. The four rows
 * shown instead are section 8's real Ligaya+-only column: Safety Circle, family status/check-ins,
 * location sharing, and family emergency alerts — same visual shape (an icon, four short lines),
 * true content.
 *
 * The ₱99/month and ₱999/year prices are this screen's own static display copy for the plan as
 * designed, not read from a live RevenueCat offering — there is no RevenueCat project configured
 * yet (ACCOUNT_ACTIONS_NEEDED.md item 3) for a price to come from. [onSubscribe] below still
 * always resolves to a real, honest [PurchaseOutcome] rather than a fabricated success: tapping
 * either Subscribe button today reports the real "not configured" failure a correctly-wired but
 * unconfigured SDK call produces.
 */
@Composable
fun PaywallScreen(
    entitlementRepository: EntitlementRepository,
    onBack: () -> Unit = {},
    onSelectTab: (LigayaTab) -> Unit = {},
    onSos: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSubscribed by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { isSubscribed = entitlementRepository.isSubscribed() }

    fun subscribe(tier: SubscriptionTier) {
        val activity = context.findActivity()
        if (activity == null) {
            statusMessage = "Can't start checkout right now. Please try again."
            return
        }
        statusMessage = null
        isBusy = true
        scope.launch {
            when (val outcome = entitlementRepository.purchase(activity, tier)) {
                is PurchaseOutcome.Success -> {
                    isSubscribed = true
                    statusMessage = null
                }
                is PurchaseOutcome.Failure -> statusMessage = outcome.message
            }
            isBusy = false
        }
    }

    fun restore() {
        statusMessage = null
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
    }

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
            Icon(
                imageVector = LigayaIcons.ligayaPlus,
                contentDescription = null,
                tint = LigayaTheme.colors.berry,
                modifier = Modifier.size(22.dp).padding(start = 4.dp),
            )
            Text(
                text = "Ligaya+",
                style = LigayaTypography.settingsTitle,
                color = LigayaTheme.colors.cocoaInk,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            if (isSubscribed) {
                Text(
                    text = "You're subscribed to Ligaya+.",
                    style = LigayaTypography.settingsRow,
                    color = LigayaTheme.colors.colorStatusConfirmed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .ligayaElevation(shape = RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(LigayaTheme.colors.shell)
                        .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(22.dp))
                        .padding(LigayaSpacing.md)
                        .semantics { contentDescription = "You're subscribed to Ligaya+" },
                )
            } else {
                // The reference gathers the whole offer — headline, what you get, and both plans —
                // onto one raised panel, rather than letting it sit loose on the page.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ligayaElevation(shape = RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(LigayaTheme.colors.shell)
                        .border(1.dp, LigayaTheme.colors.shellEdge, RoundedCornerShape(26.dp))
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                ) {
                    Text(
                        text = "Unlock more with Ligaya+",
                        style = LigayaTypography.chatTitle,
                        color = LigayaTheme.colors.cocoaInk,
                    )
                    Text(
                        text = "Look after the people closest to you, wherever they are.",
                        style = LigayaTypography.chatStatus,
                        color = LigayaTheme.colors.taupe,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    Spacer(Modifier.height(18.dp))

                    FeatureChecklist()

                    Spacer(Modifier.height(20.dp))

                    PricingCard(
                        title = "Monthly",
                        price = "₱99",
                        period = "/ month",
                        badge = "Most Popular",
                        emphasized = true,
                        buttonBusy = isBusy,
                        onSubscribe = { subscribe(SubscriptionTier.MONTHLY) },
                        buttonModifier = Modifier.testTag("paywallSubscribeMonthly"),
                    )
                    Spacer(Modifier.height(12.dp))
                    PricingCard(
                        title = "Yearly",
                        price = "₱999",
                        period = "/ year",
                        badge = "Save 17%",
                        emphasized = false,
                        buttonBusy = isBusy,
                        onSubscribe = { subscribe(SubscriptionTier.YEARLY) },
                        buttonModifier = Modifier.testTag("paywallSubscribeYearly"),
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Cancel anytime. No hidden fees.",
                        style = LigayaTypography.chatStatus,
                        color = LigayaTheme.colors.taupe,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (isBusy) {
                    Spacer(Modifier.height(14.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("paywallBusyIndicator"),
                        color = LigayaTheme.colors.berry,
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Restore Purchases",
                        style = LigayaTypography.label,
                        color = LigayaTheme.colors.taupe,
                        modifier = Modifier
                            .clip(LigayaShapes.pill)
                            .clickable(role = Role.Button, onClick = ::restore)
                            .testTag("paywallRestore")
                            .padding(horizontal = LigayaSpacing.md, vertical = LigayaSpacing.sm),
                    )
                }
            }

            statusMessage?.let {
                Text(
                    text = it,
                    style = LigayaTypography.label,
                    color = LigayaTheme.colors.colorStatusFailed,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .semantics { contentDescription = "Paywall status: $it" },
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        LigayaTabBar(selected = LigayaTab.Circle, onSelect = onSelectTab, onSos = onSos)
    }
}

/** Section 8's real Ligaya+-only column, not the reference's own (inaccurate) checklist — see
 *  this file's own top doc comment for why. */
@Composable
private fun FeatureChecklist() {
    val items = listOf(
        "Your Safety Circle",
        "Family safety status & check-ins",
        "Location sharing with your circle",
        "Family emergency alerts",
    )
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = LigayaIcons.confirmed,
                    contentDescription = null,
                    tint = LigayaTheme.colors.berry,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = item,
                    style = LigayaTypography.body,
                    color = LigayaTheme.colors.cocoaInk,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PricingCard(
    title: String,
    price: String,
    period: String,
    badge: String,
    emphasized: Boolean,
    buttonBusy: Boolean,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (emphasized) LigayaTheme.colors.blush.copy(alpha = 0.5f) else LigayaTheme.colors.shell)
            .border(
                width = 1.dp,
                color = if (emphasized) LigayaTheme.colors.berry else LigayaTheme.colors.shellEdge,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // The highlighted plan wears its badge above the whole row; the saving sits in the right-hand
        // column, stacked over that plan's own button, exactly where the reference puts each.
        if (emphasized) {
            Badge(badge, emphasized = true)
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LigayaTypography.settingsRow.copy(fontWeight = FontWeight.SemiBold),
                    color = LigayaTheme.colors.cocoaInk,
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 3.dp)) {
                    Text(
                        text = price,
                        style = LigayaTypography.settingsRow.copy(fontWeight = FontWeight.Bold),
                        color = LigayaTheme.colors.cocoaInk,
                    )
                    Text(
                        text = period,
                        style = LigayaTypography.chatStatus,
                        color = LigayaTheme.colors.taupe,
                        modifier = Modifier.padding(start = 4.dp, bottom = 1.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (!emphasized) {
                    Badge(badge, emphasized = false)
                    Spacer(Modifier.height(10.dp))
                }
                SubscribeButton(
                    busy = buttonBusy,
                    emphasized = emphasized,
                    onClick = onSubscribe,
                    modifier = buttonModifier,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, emphasized: Boolean) {
    Text(
        text = text,
        style = LigayaTypography.messageTime.copy(fontWeight = FontWeight.SemiBold),
        color = if (emphasized) LigayaTheme.colors.onBerry else LigayaTheme.colors.berry,
        modifier = Modifier
            .clip(LigayaShapes.pill)
            .background(if (emphasized) LigayaTheme.colors.berry else LigayaTheme.colors.blush)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Compact, card-scoped — unlike [com.ligaya.designsystem.components.LigayaPrimaryButton]/
 *  [com.ligaya.designsystem.components.LigayaSecondaryButton], both full-width, this sits beside
 *  a price rather than under it, matching the reference's own layout. */
@Composable
private fun SubscribeButton(busy: Boolean, emphasized: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .ligayaButtonElevation(elevation = if (emphasized) 4.dp else 0.dp)
            .clip(LigayaShapes.pill)
            .let {
                if (emphasized) {
                    it.background(LigayaTheme.colors.berry)
                } else {
                    it.background(LigayaTheme.colors.shell).border(1.dp, LigayaTheme.colors.berry, LigayaShapes.pill)
                }
            }
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            Box(modifier = Modifier.size(16.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.dp,
                    color = if (emphasized) LigayaTheme.colors.onBerry else LigayaTheme.colors.berry,
                )
            }
        } else {
            Text(
                text = "Subscribe",
                style = LigayaTypography.label,
                color = if (emphasized) LigayaTheme.colors.onBerry else LigayaTheme.colors.berry,
            )
        }
    }
}
