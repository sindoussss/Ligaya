package com.ligaya.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Mirrors the HOUSEHOLD entity in LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24. */
@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val subscriptionStateJson: String,
)
