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

    // --- User-Scoped Operations (DEF-03 Stage 2) ---

    @Query("SELECT * FROM budget_limits WHERE userId = :userId")
    fun getAllBudgetLimits(userId: Long): Flow<List<BudgetLimitItem>>

    @Query("SELECT * FROM budget_limits WHERE userId = :userId AND categoryOrTotalId = :categoryOrTotalId LIMIT 1")
    suspend fun getBudgetLimit(userId: Long, categoryOrTotalId: String): BudgetLimitItem?

    @Query("SELECT * FROM budget_limits WHERE localId = :localId AND userId = :userId LIMIT 1")
    suspend fun getBudgetLimitByLocalId(localId: Long, userId: Long): BudgetLimitItem?

    @Query("DELETE FROM budget_limits WHERE userId = :userId AND categoryOrTotalId = :categoryOrTotalId")
    suspend fun deleteBudgetLimitByKey(userId: Long, categoryOrTotalId: String): Int

    @Query("DELETE FROM budget_limits WHERE localId = :localId AND userId = :userId")
    suspend fun deleteBudgetLimitByLocalId(localId: Long, userId: Long): Int

    suspend fun deleteBudgetLimit(userId: Long, categoryOrTotalId: String): Int =
        deleteBudgetLimitByKey(userId, categoryOrTotalId)

    suspend fun deleteBudgetLimit(item: BudgetLimitItem, userId: Long): Int =
        deleteBudgetLimitByKey(userId, item.id)

    @Query("DELETE FROM budget_limits WHERE userId = :userId")
    suspend fun clearAllBudgetLimits(userId: Long): Int

    @Query("SELECT COUNT(*) FROM budget_limits WHERE userId = :userId")
    suspend fun getBudgetLimitCount(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetLimit(item: BudgetLimitItem)

}
