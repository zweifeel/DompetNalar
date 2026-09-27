package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_limits")
data class BudgetLimitItem(
    @PrimaryKey val id: String, // "total_monthly" or the category name (e.g. "Makanan", "Transportasi")
    val limitAmount: Double,
    val isCategory: Boolean
)
