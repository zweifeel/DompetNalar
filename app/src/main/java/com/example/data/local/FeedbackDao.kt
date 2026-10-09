package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FeedbackItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {

    // --- User-Scoped Operations (DEF-03 Stage 2) ---

    @Query("SELECT * FROM feedbacks WHERE userId = :userId ORDER BY timestamp DESC")
    fun getFeedbacksByUser(userId: Long): Flow<List<FeedbackItem>>

    @Query("SELECT * FROM feedbacks WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getFeedbackById(id: String, userId: Long): FeedbackItem?

    @Query("DELETE FROM feedbacks WHERE id = :id AND userId = :userId")
    suspend fun deleteFeedbackById(id: String, userId: Long): Int

    suspend fun deleteFeedback(id: String, userId: Long): Int =
        deleteFeedbackById(id, userId)

    @Query("SELECT COUNT(*) FROM feedbacks WHERE userId = :userId")
    suspend fun getFeedbackCount(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: FeedbackItem)

    // --- Global / Existing Operations ---

    @Query("SELECT * FROM feedbacks ORDER BY timestamp DESC")
    fun getAllFeedbacks(): Flow<List<FeedbackItem>>

    /**
     * Administrative moderation operation for Developer Feedback Viewer.
     * Accessible exclusively by developer tooling; does not manage user financial data.
     */
    @Query("DELETE FROM feedbacks WHERE id = :id")
    suspend fun deleteFeedbackByAdmin(id: String): Int
}
