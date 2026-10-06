package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DEF-03 (Room v7): [userId] is the owning user. `NULL` means ownership is unknown
 * (legacy v6 data) and must never be implicitly assigned to any user.
 */
@Entity(
    tableName = "wallets",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class Wallet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: WalletType = WalletType.BANK,
    val accountNumber: String = "",
    val balance: Double = 0.0,
    val colorHex: String = "#0B57D0",
    val iconName: String = "bank",
    val accountMode: String = AccountMode.PERSONAL.name,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: Long? = null
)
