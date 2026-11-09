package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * P0-C Room 11 Installment Period (Contract Schedules).
 * Represents an individual scheduled payment period for an installment plan.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "installment_periods",
    foreignKeys = [
        ForeignKey(
            entity = InstallmentPlanItem::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
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
        Index(value = ["userId"]),
        Index(value = ["planId", "periodNumber"], unique = true)
    ]
)
data class InstallmentPeriod(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val currencyCode: String = "IDR",
    val userId: Long,
    val periodNumber: Int,
    val amountDueMinorUnits: Long,
    val amountPaidMinorUnits: Long = 0L,
    val dueDate: String,
    val isPaid: Boolean = false
)
