package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DEF-03 (Room v7): [userId] is the owning user. `NULL` means ownership is unknown
 * (legacy v6 data) and must never be implicitly assigned to any user.
 *
 * Room 10: Adds [idempotencyKey] with unique index for durable recurring idempotency.
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
    indices = [
        Index(value = ["userId"]),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["reversedTransactionId"])
    ]
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
    @ColumnInfo(name = "idempotencyKey", defaultValue = "NULL")
    val idempotencyKey: String? = null,
    val userId: Long? = null,
    @ColumnInfo(name = "currencyCode", defaultValue = "NULL")
    val currencyCode: String? = null,
    @ColumnInfo(name = "amountMinorUnits", defaultValue = "0")
    val amountMinorUnits: Long = if (amount != 0.0) com.example.util.CurrencyRegistry.displayToCanonicalMinorUnits(amount, currencyCode) else 0L,
    @ColumnInfo(name = "isReversal", defaultValue = "0")
    val isReversal: Boolean = false,
    @ColumnInfo(name = "reversedTransactionId", defaultValue = "NULL")
    val reversedTransactionId: Long? = null
)
