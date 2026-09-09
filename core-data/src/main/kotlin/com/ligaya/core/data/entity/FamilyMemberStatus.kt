package com.ligaya.core.data.entity

/** The two legal values of FamilyMemberEntity.status — plain string constants (matching the
 *  existing pattern used for NotificationEventEntity.state) rather than a Room-mapped enum. */
object FamilyMemberStatus {
    const val PENDING = "PENDING"
    const val ACTIVE = "ACTIVE"
}
