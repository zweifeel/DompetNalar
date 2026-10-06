package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * DEF-03 (Room v7): [userId] is the owning user. `NULL` means ownership is unknown
 * (legacy v6 data) and must never be implicitly assigned to any user.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "debts",
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
data class DebtItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val interestRatePercent: Double,
    val minMonthlyPayment: Double,
    val category: String = "Lain-lain",
    val dueDate: String = "15 tiap bulan",
    val createdAt: Long = System.currentTimeMillis(),
    val userId: Long? = null
)
