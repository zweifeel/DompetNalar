package com.example.data.repository

import com.example.data.local.UserDao
import com.example.data.model.User
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {

    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()

    suspend fun getUserByIdentifier(identifier: String): User? = userDao.getUserByIdentifier(identifier)

    suspend fun getUserById(id: Long): User? = userDao.getUserById(id)

    suspend fun insertUser(user: User): Long = userDao.insertUser(user)

    suspend fun updateUser(user: User) = userDao.updateUser(user)

    suspend fun deleteUser(user: User) = userDao.deleteUser(user)

    suspend fun updateUserRole(userId: Long, role: UserRole, expiresAt: Long? = null) {
        userDao.updateUserRole(userId, role, expiresAt)
    }
}
