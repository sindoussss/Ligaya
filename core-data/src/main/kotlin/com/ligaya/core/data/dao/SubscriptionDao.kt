package com.ligaya.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ligaya.core.data.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE householdId = :householdId")
    suspend fun getByHousehold(householdId: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE householdId = :householdId")
    fun observeByHousehold(householdId: String): Flow<SubscriptionEntity?>
}
