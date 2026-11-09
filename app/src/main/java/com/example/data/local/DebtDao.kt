package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DebtItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {

    // --- User-Scoped Operations (DEF-03 Stage 2) ---

    @Query("SELECT * FROM debts WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllDebts(userId: Long): Flow<List<DebtItem>>

    @Query("SELECT * FROM debts WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getDebtById(id: String, userId: Long): DebtItem?

    @Query("DELETE FROM debts WHERE id = :id AND userId = :userId")
    suspend fun deleteDebtById(id: String, userId: Long): Int

    suspend fun deleteDebt(id: String, userId: Long): Int =
        deleteDebtById(id, userId)

    suspend fun deleteDebt(debt: DebtItem, userId: Long): Int =
        deleteDebtById(debt.id, userId)

    @Query("""
        UPDATE debts
        SET name = :name,
            amount = :amount,
            interestRatePercent = :interestRatePercent,
            minMonthlyPayment = :minMonthlyPayment,
            category = :category,
            dueDate = :dueDate
        WHERE id = :id AND userId = :userId
    """)
    suspend fun updateDebtScoped(
        id: String,
        userId: Long,
        name: String,
        amount: Double,
        interestRatePercent: Double,
        minMonthlyPayment: Double,
        category: String,
        dueDate: String
    ): Int

    suspend fun updateDebt(debt: DebtItem, userId: Long): Int =
        updateDebtScoped(
            id = debt.id,
            userId = userId,
            name = debt.name,
            amount = debt.amount,
            interestRatePercent = debt.interestRatePercent,
            minMonthlyPayment = debt.minMonthlyPayment,
            category = debt.category,
            dueDate = debt.dueDate
        )

    @Query("DELETE FROM debts WHERE userId = :userId")
    suspend fun clearAllDebts(userId: Long): Int

    @Query("SELECT COUNT(*) FROM debts WHERE userId = :userId")
    suspend fun getDebtCount(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebt(debt: DebtItem)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebts(debts: List<DebtItem>)

    @androidx.room.Update
    suspend fun updateDebt(debt: DebtItem): Int

    @Query("SELECT * FROM debts WHERE userId IS NULL ORDER BY id ASC")
    suspend fun getLegacyNullOwnerDebts(): List<DebtItem>

    @Query("UPDATE debts SET userId = :userId WHERE id = :id AND userId IS NULL")
    suspend fun claimLegacyDebt(id: String, userId: Long): Int

    @Query("UPDATE debts SET userId = :userId WHERE userId IS NULL")
    suspend fun claimLegacyDebts(userId: Long): Int

}
