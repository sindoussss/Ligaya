package com.ligaya.core.data.repository

import com.ligaya.core.data.dao.EmergencyEventDao
import com.ligaya.core.data.entity.EmergencyEventEntity
import kotlinx.coroutines.flow.Flow

interface EmergencyEventRepository {
    suspend fun save(event: EmergencyEventEntity)
    suspend fun getById(id: String): EmergencyEventEntity?
    fun observeById(id: String): Flow<EmergencyEventEntity?>
    fun observeByUser(userId: String): Flow<List<EmergencyEventEntity>>
}

class RoomEmergencyEventRepository(private val dao: EmergencyEventDao) : EmergencyEventRepository {
    override suspend fun save(event: EmergencyEventEntity) = dao.upsert(event)
    override suspend fun getById(id: String): EmergencyEventEntity? = dao.getById(id)
    override fun observeById(id: String): Flow<EmergencyEventEntity?> = dao.observeById(id)
    override fun observeByUser(userId: String): Flow<List<EmergencyEventEntity>> = dao.observeByUser(userId)
}
