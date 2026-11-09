package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.util.RecurringInterval
import java.util.UUID

/**
 * Room 10 RecurringSchedule Entity.
 * Represents a durable, scheduled financial transaction with complete timing parameters,
 * timezone isolation, and composite foreign key constraint to wallets(id, userId).
 */
@Entity(
    tableName = "recurring_schedules",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Wallet::class,
            parentColumns = ["id", "userId"],
            childColumns = ["walletId", "userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["walletId", "userId"])
    ]
)
data class RecurringSchedule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val categoryIcon: String,
    val walletId: Long,
    val walletName: String,
    val interval: RecurringInterval,
    @ColumnInfo(defaultValue = "1") val dayOfMonth: Int = 1, // 1..31
    @ColumnInfo(defaultValue = "1") val dayOfWeek: Int = 1,   // ISO: 1 = Mon .. 7 = Sun
    @ColumnInfo(defaultValue = "9") val executionHour: Int = 9, // 0..23 (Local time in timezoneId)
    @ColumnInfo(defaultValue = "0") val executionMinute: Int = 0, // 0..59 (Local time in timezoneId)
    val startDate: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'UTC'") val timezoneId: String = "UTC",
    @ColumnInfo(defaultValue = "1") val version: Int = 1,
    @ColumnInfo(defaultValue = "1") val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "NULL") val lastExecutedCycle: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: Long
)
