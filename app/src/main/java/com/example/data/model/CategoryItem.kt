package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DEF-03 (Room v7) category representation:
 * - Canonical system category: `isSystem = true`,  `userId = NULL`
 * - User custom category:      `isSystem = false`, `userId = <owner id>`
 * - Legacy / unassigned:       `isSystem = false`, `userId = NULL` (ownership unknown)
 */
@Entity(
    tableName = "categories",
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
        Index(value = ["isSystem"])
    ]
)
data class CategoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconName: String = "category",
    val colorHex: String = "#0B57D0",
    val userId: Long? = null,
    val isSystem: Boolean = false
)
