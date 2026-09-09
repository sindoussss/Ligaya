package com.ligaya.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ligaya.core.data.entity.EmergencyEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EmergencyEventEntity)

    @Query("SELECT * FROM emergency_events WHERE id = :id")
    suspend fun getById(id: String): EmergencyEventEntity?

    @Query("SELECT * FROM emergency_events WHERE id = :id")
    fun observeById(id: String): Flow<EmergencyEventEntity?>

    @Query("SELECT * FROM emergency_events WHERE userId = :userId ORDER BY createdAtEpochMillis DESC")
    fun observeByUser(userId: String): Flow<List<EmergencyEventEntity>>
}
