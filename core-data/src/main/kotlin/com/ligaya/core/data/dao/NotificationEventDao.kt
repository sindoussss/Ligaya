package com.ligaya.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ligaya.core.data.entity.NotificationEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notificationEvent: NotificationEventEntity)

    @Query("SELECT * FROM notification_events WHERE id = :id")
    suspend fun getById(id: String): NotificationEventEntity?

    @Query("SELECT * FROM notification_events WHERE emergencyEventId = :emergencyEventId ORDER BY timestampEpochMillis ASC")
    fun observeByEmergencyEvent(emergencyEventId: String): Flow<List<NotificationEventEntity>>
}
