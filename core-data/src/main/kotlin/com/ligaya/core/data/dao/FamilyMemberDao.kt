package com.ligaya.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ligaya.core.data.entity.FamilyMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: FamilyMemberEntity)

    @Query("SELECT * FROM family_members WHERE householdId = :householdId AND userId = :userId")
    suspend fun getByHouseholdAndUser(householdId: String, userId: String): FamilyMemberEntity?

    @Query("SELECT * FROM family_members WHERE householdId = :householdId")
    fun observeByHousehold(householdId: String): Flow<List<FamilyMemberEntity>>

    @Delete
    suspend fun delete(member: FamilyMemberEntity)
}
