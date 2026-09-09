package com.ligaya.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors the NOTIFICATION_EVENT entity in LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24.
 * `state` holds the per-channel values from section 17
 * (PENDING -> SENT -> CONFIRMED/DELIVERY_CONFIRMED/FAILED) as a plain string for this step;
 * the enum lives in core-emergency-engine once the engine consumes it.
 */
@Entity(
    tableName = "notification_events",
    foreignKeys = [
        ForeignKey(
            entity = EmergencyEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["emergencyEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("emergencyEventId")],
)
data class NotificationEventEntity(
    @PrimaryKey val id: String,
    val emergencyEventId: String,
    val recipient: String,
    val channel: String,
    val state: String,
    val timestampEpochMillis: Long,
)
