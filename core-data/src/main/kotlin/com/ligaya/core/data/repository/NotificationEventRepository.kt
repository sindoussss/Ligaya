package com.ligaya.core.data.repository

import com.ligaya.core.data.dao.NotificationEventDao
import com.ligaya.core.data.entity.NotificationEventEntity
import kotlinx.coroutines.flow.Flow

interface NotificationEventRepository {
    suspend fun save(notificationEvent: NotificationEventEntity)
    suspend fun getById(id: String): NotificationEventEntity?
    fun observeByEmergencyEvent(emergencyEventId: String): Flow<List<NotificationEventEntity>>
}

class RoomNotificationEventRepository(private val dao: NotificationEventDao) : NotificationEventRepository {
    override suspend fun save(notificationEvent: NotificationEventEntity) = dao.upsert(notificationEvent)
    override suspend fun getById(id: String): NotificationEventEntity? = dao.getById(id)
    override fun observeByEmergencyEvent(emergencyEventId: String): Flow<List<NotificationEventEntity>> =
        dao.observeByEmergencyEvent(emergencyEventId)
}
