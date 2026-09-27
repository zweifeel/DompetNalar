package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
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
    val accountMode: String = AccountMode.PERSONAL.name
)
