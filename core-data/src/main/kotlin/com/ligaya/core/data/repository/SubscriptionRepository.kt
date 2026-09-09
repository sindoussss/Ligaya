package com.ligaya.core.data.repository

import com.ligaya.core.data.dao.SubscriptionDao
import com.ligaya.core.data.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    suspend fun cache(subscription: SubscriptionEntity)
    suspend fun getByHousehold(householdId: String): SubscriptionEntity?
    fun observeByHousehold(householdId: String): Flow<SubscriptionEntity?>
}

class RoomSubscriptionRepository(private val dao: SubscriptionDao) : SubscriptionRepository {
    override suspend fun cache(subscription: SubscriptionEntity) = dao.upsert(subscription)
    override suspend fun getByHousehold(householdId: String): SubscriptionEntity? = dao.getByHousehold(householdId)
    override fun observeByHousehold(householdId: String): Flow<SubscriptionEntity?> = dao.observeByHousehold(householdId)
}
