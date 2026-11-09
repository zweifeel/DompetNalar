package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * DEF-03 (Room v7): [userId] is the submitting user. `NULL` means ownership is unknown
 * (legacy v6 data) and must never be implicitly assigned to any user.
 * DEF-04 (Room v9): [isSynced] tracks Firestore cloud sync status (0=false, 1=true).
 */
@Entity(
    tableName = "feedbacks",
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
data class FeedbackItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userName: String,
    val userEmail: String,
    val rating: Int,
    val category: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long? = null,
    @ColumnInfo(name = "isSynced", defaultValue = "0")
    val isSynced: Boolean = false
)
