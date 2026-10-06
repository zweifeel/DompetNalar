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
    tableName = "transactions",
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
data class TransactionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val categoryIcon: String = "category",
    val walletId: Long,
    val walletName: String,
    val targetWalletId: Long? = null,
    val targetWalletName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val tags: String = "",
    val accountMode: String = AccountMode.PERSONAL.name,
    val userId: Long? = null
)
