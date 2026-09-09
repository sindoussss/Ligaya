package com.ligaya.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ligaya.core.data.entity.LocationEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationEventDao {
    @Insert
    suspend fun insert(locationEvent: LocationEventEntity)

    @Query("SELECT * FROM location_events WHERE id = :id")
    suspend fun getById(id: String): LocationEventEntity?

    @Query("SELECT * FROM location_events WHERE emergencyEventId = :emergencyEventId ORDER BY timestampEpochMillis ASC")
    fun observeByEmergencyEvent(emergencyEventId: String): Flow<List<LocationEventEntity>>
}
