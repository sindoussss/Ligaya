package com.ligaya.feature.emergencyactive

import com.ligaya.designsystem.LigayaDeliveryState

/**
 * Section 27's screen inventory, stated explicitly: Safety Circle delivery status "per-member,
 * per-channel... never collapsed into one 'notified' badge." [EmergencyActiveScreen] renders one
 * of these per household member, and one [DeliveryStateBadge][com.ligaya.designsystem.components.DeliveryStateBadge]
 * (Step 34) per channel within it — nothing folds multiple channels into a single status.
 */
data class MemberDeliveryStatus(
    val memberName: String,
    val channelStatuses: List<ChannelDeliveryStatus>,
)

data class ChannelDeliveryStatus(
    val channelLabel: String,
    val state: LigayaDeliveryState,
)
