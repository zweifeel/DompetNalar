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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudgetLimit(item: BudgetLimitItem)

    @androidx.room.Update
    suspend fun updateBudgetLimit(item: BudgetLimitItem)

    @Query("""
        INSERT INTO budget_limits (categoryOrTotalId, limitAmount, isCategory, userId)
        VALUES (:categoryOrTotalId, :limitAmount, :isCategory, :userId)
        ON CONFLICT(userId, categoryOrTotalId) DO UPDATE SET 
            limitAmount = :limitAmount,
            isCategory = :isCategory
    """)
    suspend fun upsertBudgetLimitByKey(
        categoryOrTotalId: String,
        limitAmount: Double,
        isCategory: Boolean,
        userId: Long
    )

    @Query("SELECT * FROM budget_limits WHERE userId IS NULL ORDER BY localId ASC")
    suspend fun getLegacyNullOwnerBudgetLimits(): List<BudgetLimitItem>

    @Query("UPDATE budget_limits SET userId = :userId WHERE localId = :localId AND userId IS NULL")
    suspend fun claimLegacyBudgetLimit(localId: Long, userId: Long): Int

    @Query("UPDATE budget_limits SET userId = :userId WHERE userId IS NULL")
    suspend fun claimLegacyBudgetLimits(userId: Long): Int

    @Query("UPDATE budget_limits SET limitAmount = :newAmount WHERE localId = :activeLocalId AND userId = :userId")
    suspend fun updateLimitAmount(activeLocalId: Long, userId: Long, newAmount: Double): Int

    @Query("DELETE FROM budget_limits WHERE localId = :localId AND userId IS NULL")
    suspend fun retireLegacyBudgetLimit(localId: Long): Int

}
