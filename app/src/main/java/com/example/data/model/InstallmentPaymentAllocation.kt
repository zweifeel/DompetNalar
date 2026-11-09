package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * P0-C Room 11 Installment Payment Allocation (Audit Lines).
 * Immutable audit line attributing payment minor units to specific installment periods.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "installment_payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = InstallmentPayment::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InstallmentPeriod::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
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
        Index(value = ["paymentId"]),
        Index(value = ["periodId"]),
        Index(value = ["userId"])
    ]
)
data class InstallmentPaymentAllocation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val paymentId: String,
    val periodId: String,
    val currencyCode: String = "IDR",
    val userId: Long,
    val allocatedMinorUnits: Long,
    val isVoid: Boolean = false,
    val allocatedAt: Long = System.currentTimeMillis()
)
