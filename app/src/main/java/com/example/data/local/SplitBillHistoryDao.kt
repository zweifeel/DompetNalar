package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SplitBillHistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitBillHistoryDao {

    // --- User-Scoped Operations (DEF-03 Stage 2) ---

    @Query("SELECT * FROM split_bill_history WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllSplitBillHistory(userId: Long): Flow<List<SplitBillHistoryItem>>

    @Query("SELECT * FROM split_bill_history WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getSplitBillHistoryById(id: String, userId: Long): SplitBillHistoryItem?

    @Query("DELETE FROM split_bill_history WHERE id = :id AND userId = :userId")
    suspend fun deleteSplitBillHistoryById(id: String, userId: Long): Int

    suspend fun deleteSplitBillHistory(id: String, userId: Long): Int =
        deleteSplitBillHistoryById(id, userId)

    suspend fun deleteSplitBillHistory(item: SplitBillHistoryItem, userId: Long): Int =
        deleteSplitBillHistoryById(item.id, userId)

    @Query("""
        UPDATE split_bill_history
        SET merchantName = :merchantName,
            date = :date,
            totalAmount = :totalAmount,
            formattedText = :formattedText,
            timestamp = :timestamp
        WHERE id = :id AND userId = :userId
    """)
    suspend fun updateSplitBillHistoryScoped(
        id: String,
        userId: Long,
        merchantName: String,
        date: String,
        totalAmount: Double,
        formattedText: String,
        timestamp: Long
    ): Int

    suspend fun updateSplitBillHistory(item: SplitBillHistoryItem, userId: Long): Int =
        updateSplitBillHistoryScoped(
            id = item.id,
            userId = userId,
            merchantName = item.merchantName,
            date = item.date,
            totalAmount = item.totalAmount,
            formattedText = item.formattedText,
            timestamp = item.timestamp
        )

    @Query("DELETE FROM split_bill_history WHERE userId = :userId")
    suspend fun clearAllHistory(userId: Long): Int

    @Query("SELECT COUNT(*) FROM split_bill_history WHERE userId = :userId")
    suspend fun getSplitBillHistoryCount(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplitBillHistory(item: SplitBillHistoryItem)

}
