package com.ligaya.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors the LOCATION_EVENT entity in LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24.
 * Immutable once written (see section 14: never invent or retroactively alter a recorded fix).
 */
@Entity(
    tableName = "location_events",
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
data class LocationEventEntity(
    @PrimaryKey val id: String,
    val emergencyEventId: String,
    val latitude: Double,
    val longitude: Double,
    val timestampEpochMillis: Long,
    val source: String,
    val sharingPermission: String,
)
