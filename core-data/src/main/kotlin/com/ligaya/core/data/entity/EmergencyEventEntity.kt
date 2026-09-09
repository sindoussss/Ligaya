package com.ligaya.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Mirrors the EMERGENCY_EVENT entity in LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24. */
@Entity(tableName = "emergency_events")
data class EmergencyEventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val incidentType: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val resolvedAtEpochMillis: Long?,
)
