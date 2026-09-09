package com.ligaya.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Mirrors the FAMILY_MEMBER entity in LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24.
 * Keyed by (householdId, userId) since a user's membership is scoped to one household.
 *
 * `status` (Step 8): "PENDING" once the household owner has invited this user but they haven't
 * accepted yet, "ACTIVE" once they have. There is no "REMOVED" status — removal/leaving deletes
 * the record outright (see backend/firestore.rules and SafetyCircleRepository), so a missing
 * record and a removed one are the same observable state, deliberately.
 */
@Entity(
    tableName = "family_members",
    primaryKeys = ["householdId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("householdId"), Index("userId")],
)
data class FamilyMemberEntity(
    val householdId: String,
    val userId: String,
    val relationship: String,
    val permissionsJson: String,
    val notificationChannel: String,
    val status: String,
)
