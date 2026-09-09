package com.ligaya.core.data.repository

import com.ligaya.core.data.dao.EmergencyStateSnapshotDao
import com.ligaya.core.data.entity.EmergencyStateSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * Persistence-side half of section 25's "never held only in memory" requirement. The
 * deterministic safety engine (a later step) is the only intended writer of this repository;
 * this step defines the storage contract only.
 */
interface EmergencyStateSnapshotRepository {
    suspend fun save(snapshot: EmergencyStateSnapshotEntity)
    suspend fun getCurrent(): EmergencyStateSnapshotEntity?
    fun observeCurrent(): Flow<EmergencyStateSnapshotEntity?>
    suspend fun clear()
}

class RoomEmergencyStateSnapshotRepository(
    private val dao: EmergencyStateSnapshotDao,
) : EmergencyStateSnapshotRepository {
    override suspend fun save(snapshot: EmergencyStateSnapshotEntity) = dao.upsert(snapshot)
    override suspend fun getCurrent(): EmergencyStateSnapshotEntity? = dao.getSnapshot()
    override fun observeCurrent(): Flow<EmergencyStateSnapshotEntity?> = dao.observeSnapshot()
    override suspend fun clear() = dao.clear()
}
