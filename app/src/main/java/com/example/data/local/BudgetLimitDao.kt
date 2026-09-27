package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BudgetLimitItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetLimitDao {
    @Query("SELECT * FROM budget_limits")
    fun getAllBudgetLimits(): Flow<List<BudgetLimitItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetLimit(item: BudgetLimitItem)

    @Delete
    suspend fun deleteBudgetLimit(item: BudgetLimitItem)

    @Query("DELETE FROM budget_limits")
    suspend fun clearAllBudgetLimits()
}
