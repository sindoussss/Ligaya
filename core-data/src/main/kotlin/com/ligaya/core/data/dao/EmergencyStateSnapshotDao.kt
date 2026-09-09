package com.ligaya.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ligaya.core.data.entity.EmergencyStateSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyStateSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: EmergencyStateSnapshotEntity)

    @Query("SELECT * FROM emergency_state_snapshot WHERE id = ${EmergencyStateSnapshotEntity.SINGLETON_ID}")
    suspend fun getSnapshot(): EmergencyStateSnapshotEntity?

    @Query("SELECT * FROM emergency_state_snapshot WHERE id = ${EmergencyStateSnapshotEntity.SINGLETON_ID}")
    fun observeSnapshot(): Flow<EmergencyStateSnapshotEntity?>

    @Query("DELETE FROM emergency_state_snapshot")
    suspend fun clear()
}
