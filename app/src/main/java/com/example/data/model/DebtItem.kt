package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
@Entity(tableName = "debts")
data class DebtItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val interestRatePercent: Double,
    val minMonthlyPayment: Double,
    val category: String = "Lain-lain",
    val dueDate: String = "15 tiap bulan",
    val createdAt: Long = System.currentTimeMillis()
)
