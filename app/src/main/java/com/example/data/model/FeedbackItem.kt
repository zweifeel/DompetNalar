package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "feedbacks")
data class FeedbackItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userName: String,
    val userEmail: String,
    val rating: Int,
    val category: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
