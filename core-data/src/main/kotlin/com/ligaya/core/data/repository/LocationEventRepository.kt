package com.ligaya.core.data.repository

import com.ligaya.core.data.dao.LocationEventDao
import com.ligaya.core.data.entity.LocationEventEntity
import kotlinx.coroutines.flow.Flow

interface LocationEventRepository {
    suspend fun record(locationEvent: LocationEventEntity)
    suspend fun getById(id: String): LocationEventEntity?
    fun observeByEmergencyEvent(emergencyEventId: String): Flow<List<LocationEventEntity>>
}

class RoomLocationEventRepository(private val dao: LocationEventDao) : LocationEventRepository {
    override suspend fun record(locationEvent: LocationEventEntity) = dao.insert(locationEvent)
    override suspend fun getById(id: String): LocationEventEntity? = dao.getById(id)
    override fun observeByEmergencyEvent(emergencyEventId: String): Flow<List<LocationEventEntity>> =
        dao.observeByEmergencyEvent(emergencyEventId)
}
