package com.ligaya.feature.emergencyactive

import com.ligaya.core.data.entity.FamilyMemberEntity
import com.ligaya.core.data.entity.NotificationEventEntity
import com.ligaya.core.data.repository.FamilyMemberRepository
import com.ligaya.core.data.repository.NotificationEventRepository
import com.ligaya.designsystem.LigayaDeliveryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Live per-member, per-channel Safety Circle delivery status for one emergency. No existing
 * core-data query returns this shape directly — it's a join of the household roster
 * (FamilyMemberRepository.observeByHousehold) with this specific emergency's notification rows
 * (NotificationEventRepository.observeByEmergencyEvent), which is why it lives here rather than
 * as a single repository method (see this module's own build.gradle.kts comment).
 */
fun interface SafetyCircleDeliveryStatusSource {
    fun observe(): Flow<List<MemberDeliveryStatus>>
}

/**
 * [householdId] and [emergencyEventId] must be supplied by whoever constructs this — nothing in
 * this codebase yet has a "current user's household" concept wired anywhere (confirmed by
 * inspection: MainActivity's own composition root has no household/event-id context at all), so
 * providing a real household/event id at the composition root is out of this step's own file
 * scope (`feature-emergency-active` only). This class is genuinely functional given real ids,
 * proven directly by SafetyCircleDeliveryStatusSourceTest against a real Room database — it just
 * has no caller supplying real ids yet.
 */
class RoomSafetyCircleDeliveryStatusSource(
    private val familyMemberRepository: FamilyMemberRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val householdId: String,
    private val emergencyEventId: String,
) : SafetyCircleDeliveryStatusSource {

    override fun observe(): Flow<List<MemberDeliveryStatus>> =
        combine(
            familyMemberRepository.observeByHousehold(householdId),
            notificationEventRepository.observeByEmergencyEvent(emergencyEventId),
        ) { members, notificationEvents ->
            members.map { it.toDeliveryStatus(notificationEvents) }
        }
}

/** FamilyMemberEntity has no display-name field (only relationship, e.g. "sibling") — a full
 *  name would need a further join against UserRepository/UserEntity, which is a real, separate
 *  piece of infrastructure this step doesn't need to build to satisfy "per-member, per-channel,
 *  never collapsed" — relationship is what's honestly available and shown as the label. */
private fun FamilyMemberEntity.toDeliveryStatus(notificationEvents: List<NotificationEventEntity>): MemberDeliveryStatus {
    val channelStatuses = notificationEvents
        .filter { it.recipient == userId }
        .map { event ->
            ChannelDeliveryStatus(
                channelLabel = event.channel.replaceFirstChar { it.uppercase() },
                state = event.state.toLigayaDeliveryState(),
            )
        }
    return MemberDeliveryStatus(memberName = relationship.replaceFirstChar { it.uppercase() }, channelStatuses = channelStatuses)
}

private fun String.toLigayaDeliveryState(): LigayaDeliveryState = when (this) {
    "PENDING" -> LigayaDeliveryState.PENDING
    "SENT" -> LigayaDeliveryState.SENT
    "CONFIRMED", "DELIVERY_CONFIRMED" -> LigayaDeliveryState.CONFIRMED
    "FAILED" -> LigayaDeliveryState.FAILED
    else -> throw IllegalArgumentException("Unknown NotificationEventEntity.state value: $this")
}
