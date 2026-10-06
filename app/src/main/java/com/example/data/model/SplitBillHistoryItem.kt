package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * DEF-03 (Room v7): [userId] is the owning user. `NULL` means ownership is unknown
 * (legacy v6 data) and must never be implicitly assigned to any user.
 */
@Entity(
    tableName = "split_bill_history",
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
data class SplitBillHistoryItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val merchantName: String,
    val date: String,
    val totalAmount: Double,
    val formattedText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long? = null
)
