package com.ligaya.core.data.profile

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts EmergencyProfile to/from the JSON string stored in UserEntity.emergencyProfileJson
 * (see core-data/entity/UserEntity.kt) and in the backend's users/{userId}.emergency_profile
 * field (see core-backend/profile/UserProfileRepository.kt) — the single shared representation
 * both layers write, so local and synced state can never structurally drift apart.
 */
object EmergencyProfileSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun toJson(profile: EmergencyProfile): String = json.encodeToString(profile)

    /** An empty/unparseable stored value decodes to the valid "skipped onboarding" profile. */
    fun fromJson(raw: String): EmergencyProfile =
        if (raw.isBlank()) {
            EmergencyProfile()
        } else {
            runCatching { json.decodeFromString<EmergencyProfile>(raw) }.getOrDefault(EmergencyProfile())
        }
}
