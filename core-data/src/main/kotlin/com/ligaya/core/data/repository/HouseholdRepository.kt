package com.ligaya.core.data.repository

import com.ligaya.core.data.dao.HouseholdDao
import com.ligaya.core.data.entity.HouseholdEntity
import kotlinx.coroutines.flow.Flow

interface HouseholdRepository {
    suspend fun save(household: HouseholdEntity)
    suspend fun getById(id: String): HouseholdEntity?
    fun observeById(id: String): Flow<HouseholdEntity?>
}

class RoomHouseholdRepository(private val dao: HouseholdDao) : HouseholdRepository {
    override suspend fun save(household: HouseholdEntity) = dao.upsert(household)
    override suspend fun getById(id: String): HouseholdEntity? = dao.getById(id)
    override fun observeById(id: String): Flow<HouseholdEntity?> = dao.observeById(id)
}
