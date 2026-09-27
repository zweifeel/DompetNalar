package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "split_bill_history")
data class SplitBillHistoryItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val merchantName: String,
    val date: String,
    val totalAmount: Double,
    val formattedText: String,
    val timestamp: Long = System.currentTimeMillis()
)
