package com.ligaya.core.data.repository

import com.ligaya.core.data.dao.FamilyMemberDao
import com.ligaya.core.data.entity.FamilyMemberEntity
import kotlinx.coroutines.flow.Flow

interface FamilyMemberRepository {
    suspend fun save(member: FamilyMemberEntity)
    suspend fun getByHouseholdAndUser(householdId: String, userId: String): FamilyMemberEntity?
    fun observeByHousehold(householdId: String): Flow<List<FamilyMemberEntity>>
}

class RoomFamilyMemberRepository(private val dao: FamilyMemberDao) : FamilyMemberRepository {
    override suspend fun save(member: FamilyMemberEntity) = dao.upsert(member)
    override suspend fun getByHouseholdAndUser(householdId: String, userId: String): FamilyMemberEntity? =
        dao.getByHouseholdAndUser(householdId, userId)
    override fun observeByHousehold(householdId: String): Flow<List<FamilyMemberEntity>> =
        dao.observeByHousehold(householdId)
}
