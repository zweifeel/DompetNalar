package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 10 Wallet Entity.
 *
 * - [userId]: Owning user ID (nullable Long? preserved from Room 9 DEF-03 legacy v6 isolation).
 * - [isArchived]: Lifecycle flag (default 0).
 * - [currencyCode]: Nullable ISO 4217 code (default NULL). Zero silent fallback to IDR/USD.
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
    indices = [
        Index(value = ["userId"]),
        Index(value = ["id", "userId"], unique = true)
    ]
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
    @ColumnInfo(name = "isArchived", defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "currencyCode", defaultValue = "NULL")
    val currencyCode: String? = null,
    val userId: Long? = null,
    @ColumnInfo(name = "balanceMinorUnits", defaultValue = "0")
    val balanceMinorUnits: Long = if (balance != 0.0) com.example.util.CurrencyRegistry.displayToCanonicalMinorUnits(balance, currencyCode) else 0L
) {
    val hasAssignedCurrency: Boolean get() = !currencyCode.isNullOrBlank()
}
