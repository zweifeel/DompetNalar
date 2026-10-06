package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DEF-03 (Room v7) budget schema: `localId` (surrogate PK) + `userId` (owner, nullable) +
 * `categoryOrTotalId` ("total_monthly" or a category name).
 *
 * The Kotlin property [id] is intentionally kept and mapped to the `categoryOrTotalId`
 * column so that existing callers (and legacy backup JSON using the `id` key) keep
 * compiling/deserializing until the DAO/Repository/Backup stages are implemented.
 *
 * Uniqueness is enforced per owner via the unique index (`userId`, `categoryOrTotalId`),
 * so different users can each own a "total_monthly" row without primary-key collision.
 * `userId = NULL` means ownership is unknown (legacy v6 data).
 */
@Entity(
    tableName = "budget_limits",
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
        Index(value = ["userId", "categoryOrTotalId"], unique = true)
    ]
)
data class BudgetLimitItem(
    @ColumnInfo(name = "categoryOrTotalId")
    val id: String, // "total_monthly" or the category name (e.g. "Makanan", "Transportasi")
    val limitAmount: Double,
    val isCategory: Boolean,
    val userId: Long? = null,
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0
)
