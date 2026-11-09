package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * P0-C Room 11 Installment Payment (Settlement Receipts).
 * Binds payment settlements strictly to a general ledger transaction and installment plan.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "installment_payments",
    foreignKeys = [
        ForeignKey(
            entity = InstallmentPlanItem::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionItem::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionItem::class,
            parentColumns = ["id"],
            childColumns = ["reversalTransactionId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["planId"]),
        Index(value = ["transactionId"]),
        Index(value = ["reversalTransactionId"], unique = true),
        Index(value = ["userId"])
    ]
)
data class InstallmentPayment(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val transactionId: Long,
    val currencyCode: String = "IDR",
    val userId: Long,
    val totalMinorUnits: Long,
    val unallocatedMinorUnits: Long = 0L,
    val isVoid: Boolean = false,
    val reversalTransactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
