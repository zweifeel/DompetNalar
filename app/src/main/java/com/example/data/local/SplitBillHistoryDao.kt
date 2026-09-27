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
    @Query("SELECT * FROM split_bill_history ORDER BY timestamp DESC")
    fun getAllSplitBillHistory(): Flow<List<SplitBillHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplitBillHistory(item: SplitBillHistoryItem)

    @Delete
    suspend fun deleteSplitBillHistory(item: SplitBillHistoryItem)

    @Query("DELETE FROM split_bill_history")
    suspend fun clearAllHistory()
}
