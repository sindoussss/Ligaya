package com.ligaya.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors the USER entity in LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24.
 *
 * profile/emergencyProfile/permissions are stored as opaque JSON for this step — concrete
 * serialization is decided when the consuming feature (emergency profile CRUD) is built.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val profileJson: String,
    val emergencyProfileJson: String,
    val permissionsJson: String,
)
