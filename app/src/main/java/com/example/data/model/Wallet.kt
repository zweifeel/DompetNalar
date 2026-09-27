package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
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
    val createdAt: Long = System.currentTimeMillis()
)
