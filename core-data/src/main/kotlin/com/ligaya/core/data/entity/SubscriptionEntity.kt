package com.ligaya.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors the SUBSCRIPTION entity in LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24.
 * Per section 2, this is a read-mostly local cache of what the backend decided from
 * RevenueCat's entitlement confirmation — it is never written locally as a source of truth.
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val householdId: String,
    val entitlement: String,
    val state: String,
)
