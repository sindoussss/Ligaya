package com.ligaya.core.data.profile

import com.ligaya.core.data.dao.UserDao
import com.ligaya.core.data.entity.UserEntity

interface EmergencyProfileRepository {
    /** Null only if no USER row exists yet for this id — distinct from an explicitly-skipped
     *  (but existing) profile, which decodes to EmergencyProfile(). */
    suspend fun getProfile(userId: String): EmergencyProfile?
    suspend fun saveProfile(userId: String, profile: EmergencyProfile)
}

class RoomEmergencyProfileRepository(
    private val userDao: UserDao,
) : EmergencyProfileRepository {

    override suspend fun getProfile(userId: String): EmergencyProfile? {
        val user = userDao.getById(userId) ?: return null
        return EmergencyProfileSerializer.fromJson(user.emergencyProfileJson)
    }

    override suspend fun saveProfile(userId: String, profile: EmergencyProfile) {
        val existing = userDao.getById(userId)
        val updated = (existing ?: emptyUser(userId)).copy(
            emergencyProfileJson = EmergencyProfileSerializer.toJson(profile),
        )
        userDao.upsert(updated)
    }

    private fun emptyUser(userId: String) = UserEntity(
        id = userId,
        profileJson = "{}",
        emergencyProfileJson = "{}",
        permissionsJson = "{}",
    )
}
