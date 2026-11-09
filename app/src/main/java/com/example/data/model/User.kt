package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole(val displayName: String, val badgeText: String) {
    FREE_MEMBER("DompetNalar Member", "Member"),
    SMART_MASTER("Smart Master", "Smart Master 👑"),
    MASTER_DEV("Master Developer", "MasterDev 👑")
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val email: String,
    val passwordHash: String = "",
    val role: UserRole = UserRole.FREE_MEMBER,
    val createdAt: Long = System.currentTimeMillis(),
    val trialStartDate: Long = 0L,
    val subscriptionExpiresAt: Long? = null,
    val isApproved: Boolean = true,
    val isGuest: Boolean = false
)
