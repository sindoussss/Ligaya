package com.ligaya.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ligaya.core.data.entity.HouseholdEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(household: HouseholdEntity)

    @Query("SELECT * FROM households WHERE id = :id")
    suspend fun getById(id: String): HouseholdEntity?

    @Query("SELECT * FROM households WHERE id = :id")
    fun observeById(id: String): Flow<HouseholdEntity?>

    @Delete
    suspend fun delete(household: HouseholdEntity)
}
