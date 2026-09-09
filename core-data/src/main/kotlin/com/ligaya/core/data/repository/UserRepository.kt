package com.ligaya.core.data.repository

import com.ligaya.core.data.dao.UserDao
import com.ligaya.core.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun save(user: UserEntity)
    suspend fun getById(id: String): UserEntity?
    fun observeById(id: String): Flow<UserEntity?>
}

class RoomUserRepository(private val dao: UserDao) : UserRepository {
    override suspend fun save(user: UserEntity) = dao.upsert(user)
    override suspend fun getById(id: String): UserEntity? = dao.getById(id)
    override fun observeById(id: String): Flow<UserEntity?> = dao.observeById(id)
}
